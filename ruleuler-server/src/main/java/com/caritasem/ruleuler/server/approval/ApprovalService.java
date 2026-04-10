package com.caritasem.ruleuler.server.approval;

import com.bstek.urule.Configure;
import com.bstek.urule.console.repository.ClientConfig;
import com.bstek.urule.console.repository.RepositoryService;
import com.bstek.urule.console.repository.model.ResourceItem;
import com.bstek.urule.console.repository.model.ResourcePackage;
import com.bstek.urule.runtime.KnowledgePackage;
import com.bstek.urule.runtime.KnowledgePackageWrapper;
import com.bstek.urule.runtime.cache.CacheUtils;
import com.caritasem.ruleuler.server.approval.model.*;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.codehaus.jackson.map.SerializationConfig;
import org.codehaus.jackson.map.annotate.JsonSerialize;

import java.io.*;
import java.net.HttpURLConnection;
import java.net.URL;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.text.SimpleDateFormat;
import java.util.*;

@Service
public class ApprovalService {

    private static final Logger log = LoggerFactory.getLogger(ApprovalService.class);
    private final ObjectMapper objectMapper = new ObjectMapper();

    private final ApprovalDao approvalDao;
    private final DiffCalculator diffCalculator;
    private final RepositoryService repositoryService;

    public ApprovalService(ApprovalDao approvalDao,
                           DiffCalculator diffCalculator,
                           @Qualifier("urule.repositoryService") RepositoryService repositoryService) {
        this.approvalDao = approvalDao;
        this.diffCalculator = diffCalculator;
        this.repositoryService = repositoryService;
    }

    // ---- submit ----

    @Transactional
    public Map<String, Object> submit(String project, String packageId, String submitter) {
        // 1. 检查 pending 唯一性
        if (approvalDao.existsPending(project, packageId)) {
            throw new IllegalArgumentException("该知识包已有待审批的发布申请");
        }

        // 2. 加载知识包
        ResourcePackage pkg = loadPackage(project, packageId);
        if (pkg == null) {
            throw new IllegalArgumentException("知识包不存在");
        }

        // 3. 计算快照和 diff
        PublishSnapshot lastSnapshot = approvalDao.findLatestSnapshot(project, packageId);
        Map<String, String> lastMap = parseSnapshotData(lastSnapshot);

        List<ResourceItem> items = pkg.getResourceItems() != null ? pkg.getResourceItems() : List.of();
        List<ApprovalDiffItem> diffs = diffCalculator.calculate(items, lastMap);

        // 4. 构建当前快照数据
        Map<String, String> currentSnapshotMap = new LinkedHashMap<>();
        for (ResourceItem item : items) {
            String resolved = diffCalculator.resolveVersion(item.getPath(), item.getVersion());
            currentSnapshotMap.put(item.getPath(), resolved);
        }

        // 5. 持久化
        long now = System.currentTimeMillis();
        Approval approval = Approval.builder()
                .project(project)
                .packageId(packageId)
                .packageName(pkg.getName())
                .status(ApprovalStatus.PENDING)
                .submitter(submitter)
                .submittedAt(now)
                .build();
        Long approvalId = approvalDao.insertApproval(approval);

        for (ApprovalDiffItem d : diffs) {
            d.setApprovalId(approvalId);
        }
        if (!diffs.isEmpty()) {
            approvalDao.batchInsertDiffItems(diffs);
        }

        approval.setId(approvalId);
        return buildApprovalVo(approval, diffs);
    }

    // ---- approve ----

    @Transactional
    public Map<String, Object> approve(Long approvalId, String approver, String comment) {
        Approval a = approvalDao.findById(approvalId);
        if (a == null) throw new IllegalArgumentException("审批单不存在");
        if (a.getStatus() != ApprovalStatus.PENDING)
            throw new IllegalArgumentException("审批单状态不允许此操作");

        if (comment != null && comment.length() > 500)
            throw new IllegalArgumentException("审批意见不能超过500个字符");

        long now = System.currentTimeMillis();
        approvalDao.updateStatus(approvalId, ApprovalStatus.APPROVED, approver, comment, now);

        a.setStatus(ApprovalStatus.APPROVED);
        a.setApprover(approver);
        a.setComment(comment);
        a.setApprovedAt(now);
        return buildApprovalVo(a, approvalDao.findDiffItemsByApprovalId(approvalId));
    }

    // ---- publish ----

    @Transactional
    public Map<String, Object> publish(Long approvalId, String publisher) {
        Approval a = approvalDao.findById(approvalId);
        if (a == null) throw new IllegalArgumentException("审批单不存在");
        if (a.getStatus() != ApprovalStatus.APPROVED && a.getStatus() != ApprovalStatus.PUBLISH_FAILED)
            throw new IllegalArgumentException("审批单状态不允许此操作");

        try {
            String pushInfo = executePublish(a.getProject(), a.getPackageId());
            log.info("上线完成: approvalId={}, info={}", approvalId, pushInfo);

            createPublishSnapshot(a.getProject(), a.getPackageId(), approvalId);

            long now = System.currentTimeMillis();
            approvalDao.updatePublished(approvalId, publisher, now);

            a.setStatus(ApprovalStatus.PUBLISHED);
            a.setPublisher(publisher);
            a.setPublishedAt(now);
            return buildApprovalVo(a, approvalDao.findDiffItemsByApprovalId(approvalId));
        } catch (Exception e) {
            log.error("上线失败: approvalId={}, error={}", approvalId, e.getMessage(), e);
            approvalDao.updatePublishFailed(approvalId, e.getMessage());
            a.setStatus(ApprovalStatus.PUBLISH_FAILED);
            a.setFailReason(e.getMessage());
            return buildApprovalVo(a, approvalDao.findDiffItemsByApprovalId(approvalId));
        }
    }

    // ---- reject ----

    public Map<String, Object> reject(Long approvalId, String approver, String comment) {
        Approval a = approvalDao.findById(approvalId);
        if (a == null) throw new IllegalArgumentException("审批单不存在");
        if (a.getStatus() != ApprovalStatus.PENDING)
            throw new IllegalArgumentException("审批单状态不允许此操作");

        if (comment != null && comment.length() > 500)
            throw new IllegalArgumentException("审批意见不能超过500个字符");

        approvalDao.updateStatus(approvalId, ApprovalStatus.REJECTED, approver, comment, System.currentTimeMillis());

        a.setStatus(ApprovalStatus.REJECTED);
        a.setApprover(approver);
        a.setComment(comment);
        a.setApprovedAt(System.currentTimeMillis());
        return buildApprovalVo(a, approvalDao.findDiffItemsByApprovalId(approvalId));
    }

    // ---- list ----

    public Map<String, Object> list(String project, String packageId, String status,
                                     String submitter, int page, int pageSize) {
        int offset = (page - 1) * pageSize;
        List<Approval> items = approvalDao.listByFilter(project, packageId, status, submitter, offset, pageSize);
        int total = approvalDao.countByFilter(project, packageId, status, submitter);
        List<Map<String, Object>> voList = new ArrayList<>();
        for (Approval a : items) {
            voList.add(buildApprovalVo(a, null));
        }
        return Map.of("items", voList, "total", total);
    }

    // ---- detail ----

    public Map<String, Object> getDetail(Long approvalId) {
        Approval a = approvalDao.findById(approvalId);
        if (a == null) throw new IllegalArgumentException("审批单不存在");
        List<ApprovalDiffItem> diffs = approvalDao.findDiffItemsByApprovalId(approvalId);
        return buildApprovalVo(a, diffs);
    }

    // ---- private helpers ----

    private ResourcePackage loadPackage(String project, String packageId) {
        try {
            List<ResourcePackage> packages = repositoryService.loadProjectResourcePackages(project);
            for (ResourcePackage p : packages) {
                if (p.getId().equals(packageId)) return p;
            }
        } catch (Exception e) {
            throw new RuntimeException("加载知识包失败: " + e.getMessage(), e);
        }
        return null;
    }

    private Map<String, String> parseSnapshotData(PublishSnapshot snapshot) {
        if (snapshot == null || snapshot.getSnapshotData() == null) return null;
        try {
            return objectMapper.readValue(snapshot.getSnapshotData(),
                    new com.fasterxml.jackson.core.type.TypeReference<LinkedHashMap<String, String>>() {});
        } catch (Exception e) {
            return null;
        }
    }

    private void createPublishSnapshot(String project, String packageId, Long approvalId) {
        try {
            ResourcePackage pkg = loadPackage(project, packageId);
            List<ResourceItem> items = pkg != null && pkg.getResourceItems() != null
                    ? pkg.getResourceItems() : List.of();
            Map<String, String> snapshotMap = new LinkedHashMap<>();
            for (ResourceItem item : items) {
                String resolved = diffCalculator.resolveVersion(item.getPath(), item.getVersion());
                snapshotMap.put(item.getPath(), resolved);
            }
            String json = objectMapper.writeValueAsString(snapshotMap);
            approvalDao.insertSnapshot(PublishSnapshot.builder()
                    .project(project)
                    .packageId(packageId)
                    .approvalId(approvalId)
                    .snapshotData(json)
                    .createdAt(System.currentTimeMillis())
                    .build());
        } catch (Exception e) {
            throw new RuntimeException("创建发布快照失败: " + e.getMessage(), e);
        }
    }

    /**
     * 直接调用底层组件执行发布，复用 PackageServletHandler 的核心逻辑。
     */
    private String executePublish(String project, String packageId) throws Exception {
        String fullPackageId = project + "/" + packageId;
        if (fullPackageId.startsWith("/")) {
            fullPackageId = fullPackageId.substring(1);
        }

        KnowledgePackage kp = CacheUtils.getKnowledgeCache().getKnowledge(fullPackageId);
        if (kp == null) {
            throw new RuntimeException("知识包缓存为空，请先在编辑器中编译知识包");
        }

        // 序列化（用 Jackson 1.x 与 PackageServletHandler 一致）
        org.codehaus.jackson.map.ObjectMapper mapper1 = new org.codehaus.jackson.map.ObjectMapper();
        mapper1.setSerializationInclusion(JsonSerialize.Inclusion.NON_NULL);
        mapper1.configure(SerializationConfig.Feature.WRITE_DATES_AS_TIMESTAMPS, false);
        mapper1.setDateFormat(new SimpleDateFormat(Configure.getDateFormat()));
        StringWriter writer = new StringWriter();
        mapper1.writeValue(writer, new KnowledgePackageWrapper(kp));
        String content = writer.toString();
        writer.close();

        // 推送到客户端
        List<ClientConfig> clients = repositoryService.loadClientConfigs(project);
        StringBuilder sb = new StringBuilder();
        sb.append("知识包:").append(fullPackageId).append("<br>");

        for (ClientConfig config : clients) {
            boolean result = pushToClient(fullPackageId, content, config.getClient());
            sb.append(result ? "推送 " + config.getName() + " 成功" : "推送 " + config.getName() + " 失败")
              .append("<br>");
        }
        return sb.toString();
    }

    private boolean pushToClient(String packageId, String content, String client) {
        HttpURLConnection connection = null;
        try {
            if (client.endsWith("/")) client = client.substring(0, client.length() - 1);
            String clientUrl = client + "/knowledgepackagereceiver";
            String encoded = "packageId=" + URLEncoder.encode(packageId, "utf-8")
                    + "&content=" + URLEncoder.encode(content, "utf-8");
            URL url = new URL(clientUrl);
            connection = (HttpURLConnection) url.openConnection();
            connection.setRequestMethod("POST");
            connection.setRequestProperty("Charset", "UTF-8");
            connection.setRequestProperty("Content-Type", "application/x-www-form-urlencoded");
            connection.setUseCaches(false);
            connection.setDoOutput(true);
            connection.setDoInput(true);
            connection.connect();
            try (OutputStream os = connection.getOutputStream();
                 DataOutputStream wr = new DataOutputStream(os)) {
                wr.writeBytes(encoded);
                wr.flush();
            }
            if (connection.getResponseCode() >= 300) return false;
            String result = new String(connection.getInputStream().readAllBytes(), StandardCharsets.UTF_8);
            return "ok".equals(result);
        } catch (Exception e) {
            log.error("推送到客户端失败: client={}, error={}", client, e.getMessage());
            return false;
        } finally {
            if (connection != null) connection.disconnect();
        }
    }

    private Map<String, Object> buildApprovalVo(Approval a, List<ApprovalDiffItem> diffs) {
        Map<String, Object> vo = new LinkedHashMap<>();
        vo.put("id", a.getId());
        vo.put("project", a.getProject());
        vo.put("packageId", a.getPackageId());
        vo.put("packageName", a.getPackageName());
        vo.put("status", a.getStatus().name());
        vo.put("submitter", a.getSubmitter());
        vo.put("comment", a.getComment());
        vo.put("failReason", a.getFailReason());
        vo.put("submittedAt", a.getSubmittedAt());
        vo.put("approver", a.getApprover());
        vo.put("approvedAt", a.getApprovedAt());
        vo.put("publisher", a.getPublisher());
        vo.put("publishedAt", a.getPublishedAt());
        if (diffs != null) vo.put("diffs", diffs);
        return vo;
    }
}
