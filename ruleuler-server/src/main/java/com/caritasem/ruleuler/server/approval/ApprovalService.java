package com.caritasem.ruleuler.server.approval;

import com.bstek.urule.Configure;
import com.bstek.urule.builder.KnowledgeBase;
import com.bstek.urule.builder.KnowledgeBuilder;
import com.bstek.urule.builder.ResourceBase;
import com.bstek.urule.builder.resource.Resource;
import com.bstek.urule.console.repository.ClientConfig;
import com.bstek.urule.console.repository.RepositoryService;
import com.bstek.urule.console.repository.model.ResourcePackage;
import com.bstek.urule.runtime.KnowledgePackage;
import com.bstek.urule.runtime.KnowledgePackageWrapper;
import com.bstek.urule.runtime.cache.CacheUtils;
import com.bstek.urule.runtime.service.KnowledgePackageService;
import com.caritasem.ruleuler.console.servlet.respackage.autotest.TestCasePack;
import com.caritasem.ruleuler.console.servlet.respackage.autotest.TestExecutor;
import com.caritasem.ruleuler.console.servlet.respackage.autotest.TestResultDao;
import com.caritasem.ruleuler.console.servlet.respackage.autotest.TestRun;
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
    private final TestExecutor testExecutor;
    private final TestResultDao testResultDao;
    private final KnowledgePackageService knowledgePackageService;
    private final KnowledgeBuilder knowledgeBuilder;

    public ApprovalService(ApprovalDao approvalDao,
                           DiffCalculator diffCalculator,
                           @Qualifier("urule.repositoryService") RepositoryService repositoryService,
                           @Qualifier("urule.testExecutor") TestExecutor testExecutor,
                           @Qualifier("urule.testResultDao") TestResultDao testResultDao,
                           @Qualifier("urule.knowledgePackageService") KnowledgePackageService knowledgePackageService,
                           @Qualifier("urule.knowledgeBuilder") KnowledgeBuilder knowledgeBuilder) {
        this.approvalDao = approvalDao;
        this.diffCalculator = diffCalculator;
        this.repositoryService = repositoryService;
        this.testExecutor = testExecutor;
        this.testResultDao = testResultDao;
        this.knowledgePackageService = knowledgePackageService;
        this.knowledgeBuilder = knowledgeBuilder;
    }

    // ---- submit ----

    @Transactional
    public Map<String, Object> submit(String project, String packageId, String submitter, String description) {
        // 1. 检查 pending 唯一性
        if (approvalDao.existsPending(project, packageId)) {
            throw new IllegalArgumentException("该知识包已有待审批的发布申请");
        }

        // 2. 加载知识包
        ResourcePackage pkg = loadPackage(project, packageId);
        if (pkg == null) {
            throw new IllegalArgumentException("知识包不存在");
        }

        // 3. 加载当前项目全量资源内容 & 上次快照
        Map<String, String> currentMap = diffCalculator.loadProjectContentMap(project);
        PublishSnapshot lastSnapshot = approvalDao.findLatestSnapshot(project, packageId);
        Map<String, String> prevMap = parseSnapshotData(lastSnapshot);

        // 4. 内容级 diff
        List<ApprovalDiffItem> diffs = diffCalculator.calculateContentDiff(currentMap, prevMap);

        // 5. 判断是否有用例包，决定初始状态
        boolean hasTestPack = hasTestPacks(project, packageId);
        ApprovalStatus initialStatus = hasTestPack ? ApprovalStatus.TESTING : ApprovalStatus.PENDING;

        // 6. 持久化审批单
        long now = System.currentTimeMillis();
        Approval approval = Approval.builder()
                .project(project)
                .packageId(packageId)
                .packageName(pkg.getName())
                .status(initialStatus)
                .submitter(submitter)
                .description(description)
                .submittedAt(now)
                .build();
        Long approvalId = approvalDao.insertApproval(approval);

        for (ApprovalDiffItem d : diffs) {
            d.setApprovalId(approvalId);
        }
        if (!diffs.isEmpty()) {
            approvalDao.batchInsertDiffItems(diffs);
        }

        // 7. 提交时创建快照（内容级）
        createContentSnapshot(project, packageId, approvalId, currentMap);

        // 8. 异步执行自动测试
        if (hasTestPack) {
            runAutoTestAsync(project, packageId, approvalId);
        }

        approval.setId(approvalId);
        return buildApprovalVo(approval, diffs);
    }

    private boolean hasTestPacks(String project, String packageId) {
        try {
            List<TestCasePack> packs = testResultDao.findPacksByPackage(project, packageId);
            return packs != null && !packs.isEmpty();
        } catch (Exception e) {
            return false;
        }
    }

    /**
     * 异步执行自动测试，完成后更新审批单状态为 PENDING。
     * 测试失败不阻塞审批流程，只记录结果。
     */
    private void runAutoTestAsync(String project, String packageId, Long approvalId) {
        new Thread(() -> {
            Long testRunId = null;
            try {
                List<TestCasePack> packs = testResultDao.findPacksByPackage(project, packageId);
                if (packs != null && !packs.isEmpty()) {
                    TestCasePack latestPack = packs.get(packs.size() - 1);
                    log.info("审批#{} 异步自动测试开始: packId={}", approvalId, latestPack.getId());
                    TestRun run = testExecutor.execute(latestPack.getId(), null);
                    testRunId = run.getId();
                    log.info("审批#{} 异步自动测试完成: runId={}, passed={}, failed={}",
                            approvalId, run.getId(), run.getPassedCases(), run.getFailedCases());
                }
            } catch (Exception e) {
                log.warn("审批#{} 异步自动测试执行失败: {}", approvalId, e.getMessage(), e);
            } finally {
                // 无论测试成功失败，都流转到 PENDING
                try {
                    approvalDao.updateTestResult(approvalId, testRunId, ApprovalStatus.PENDING);
                } catch (Exception e) {
                    log.error("审批#{} 更新测试结果失败", approvalId, e);
                }
            }
        }, "approval-test-" + approvalId).start();
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
            // 加载该审批单对应的快照内容（提交时的版本）
            PublishSnapshot snapshot = approvalDao.findSnapshotByApprovalId(approvalId);
            Map<String, String> snapshotContent = parseSnapshotData(snapshot);
            String pushInfo = executePublish(a.getProject(), a.getPackageId(), snapshotContent);
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

    @Transactional
    public Map<String, Object> recalcDiff(Long approvalId) {
        Approval a = approvalDao.findById(approvalId);
        if (a == null) throw new IllegalArgumentException("审批单不存在");

        // 找该审批单对应的快照（提交时创建的）
        PublishSnapshot snapshot = approvalDao.findSnapshotByApprovalId(approvalId);
        Map<String, String> prevMap = parseSnapshotData(snapshot);

        // 找该快照的前一个快照作为 prev（即提交时的 prevMap）
        // 实际上 snapshot 存的是提交时的当前内容，prev 是上一次的快照
        // 需要找 approval_id < approvalId 的最新快照
        PublishSnapshot prevSnapshot = approvalDao.findPrevSnapshot(a.getProject(), a.getPackageId(), approvalId);
        Map<String, String> prevContentMap = parseSnapshotData(prevSnapshot);

        // 用当前快照内容作为 currentMap，前一快照作为 prevMap 重新计算
        List<ApprovalDiffItem> diffs = diffCalculator.calculateContentDiff(
                prevMap != null ? prevMap : new LinkedHashMap<>(),
                prevContentMap);

        // 删旧的，插新的
        approvalDao.deleteDiffItemsByApprovalId(approvalId);
        for (ApprovalDiffItem d : diffs) {
            d.setApprovalId(approvalId);
        }
        if (!diffs.isEmpty()) {
            approvalDao.batchInsertDiffItems(diffs);
        }
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
        Map<String, String> currentMap = diffCalculator.loadProjectContentMap(project);
        createContentSnapshot(project, packageId, approvalId, currentMap);
    }

    private void createContentSnapshot(String project, String packageId, Long approvalId, Map<String, String> contentMap) {
        try {
            String json = objectMapper.writeValueAsString(contentMap);
            approvalDao.insertSnapshot(PublishSnapshot.builder()
                    .project(project)
                    .packageId(packageId)
                    .approvalId(approvalId)
                    .snapshotData(json)
                    .createdAt(System.currentTimeMillis())
                    .build());
        } catch (Exception e) {
            throw new RuntimeException("创建快照失败: " + e.getMessage(), e);
        }
    }

    /**
     * 执行发布：用审批时的快照内容 build 知识包，推送到客户端。
     * 快照内容为空时（兼容旧数据）降级为 build 当前最新内容。
     */
    private String executePublish(String project, String packageId, Map<String, String> snapshotContent) throws Exception {
        String fullPackageId = project + "/" + packageId;
        if (fullPackageId.startsWith("/")) {
            fullPackageId = fullPackageId.substring(1);
        }

        KnowledgePackage kp;
        if (snapshotContent != null && !snapshotContent.isEmpty()) {
            // 用快照内容 build，确保上线的是审批通过的版本
            kp = buildFromSnapshot(snapshotContent);
            log.info("使用快照内容 build 知识包: {}, 共 {} 个资源", fullPackageId, snapshotContent.size());
        } else {
            // 兼容旧数据：快照为空时 build 当前最新
            log.warn("快照为空，降级为 build 当前最新内容: {}", fullPackageId);
            kp = knowledgePackageService.buildKnowledgePackage(fullPackageId);
        }
        CacheUtils.getKnowledgeCache().putKnowledge(fullPackageId, kp);

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

    /**
     * 用快照内容（path → xmlContent）直接 build KnowledgePackage，不走 repositoryService。
     * ResourceBase.resources 是 private，用反射注入。
     */
    @SuppressWarnings("unchecked")
    private KnowledgePackage buildFromSnapshot(Map<String, String> snapshotContent) throws Exception {
        ResourceBase resourceBase = knowledgeBuilder.newResourceBase();
        java.lang.reflect.Field resourcesField = ResourceBase.class.getDeclaredField("resources");
        resourcesField.setAccessible(true);
        List<Resource> resources = (List<Resource>) resourcesField.get(resourceBase);
        for (Map.Entry<String, String> entry : snapshotContent.entrySet()) {
            String path = entry.getKey();
            String xml = entry.getValue();
            if (xml == null || xml.isBlank()) continue;
            // path 需要带 dbr: 前缀，Resource 构造是 (content, path)
            String resourcePath = path.startsWith("dbr:") ? path : "dbr:" + path;
            resources.add(new Resource(xml, resourcePath));
        }
        KnowledgeBase kb = knowledgeBuilder.buildKnowledgeBase(resourceBase);
        return kb.getKnowledgePackage();
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
        vo.put("testRunId", a.getTestRunId());
        vo.put("description", a.getDescription());
        if (diffs != null) vo.put("diffs", diffs);

        // 测试结果摘要
        if (a.getTestRunId() != null) {
            try {
                TestRun run = testResultDao.findRunById(a.getTestRunId());
                if (run != null) {
                    Map<String, Object> testSummary = new LinkedHashMap<>();
                    testSummary.put("runId", run.getId());
                    testSummary.put("status", run.getStatus());
                    testSummary.put("totalCases", run.getTotalCases());
                    testSummary.put("passedCases", run.getPassedCases());
                    testSummary.put("failedCases", run.getFailedCases());
                    testSummary.put("startedAt", run.getStartedAt());
                    testSummary.put("finishedAt", run.getFinishedAt());
                    vo.put("testSummary", testSummary);
                }
            } catch (Exception e) {
                log.warn("加载测试结果摘要失败: runId={}", a.getTestRunId(), e);
            }
        }
        return vo;
    }
}
