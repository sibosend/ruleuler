package com.caritasem.ruleuler.server.grayscale;

import com.bstek.urule.Configure;
import com.bstek.urule.console.repository.ClientConfig;
import com.bstek.urule.console.repository.RepositoryService;
import com.bstek.urule.runtime.KnowledgePackage;
import com.bstek.urule.runtime.KnowledgePackageWrapper;
import com.bstek.urule.runtime.cache.CacheUtils;
import com.caritasem.ruleuler.server.approval.ApprovalDao;
import com.caritasem.ruleuler.server.approval.model.Approval;
import com.caritasem.ruleuler.server.approval.model.ApprovalStatus;
import com.caritasem.ruleuler.server.approval.model.PublishSnapshot;
import com.caritasem.ruleuler.server.grayscale.model.*;
import tools.jackson.databind.ObjectMapper;
import tools.jackson.core.type.TypeReference;
import org.codehaus.jackson.map.SerializationConfig;
import org.codehaus.jackson.map.annotate.JsonSerialize;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.io.*;
import java.net.HttpURLConnection;
import java.net.URL;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.text.SimpleDateFormat;
import java.util.*;

@Service
public class GrayscaleService {

    private static final Logger log = LoggerFactory.getLogger(GrayscaleService.class);
    private final ObjectMapper objectMapper = new ObjectMapper();

    private final GrayscaleRuleDao grayscaleRuleDao;
    private final ApprovalDao approvalDao;
    private final SnapshotKnowledgeBuilder snapshotBuilder;
    private final RepositoryService repositoryService;

    public GrayscaleService(GrayscaleRuleDao grayscaleRuleDao,
                            ApprovalDao approvalDao,
                            SnapshotKnowledgeBuilder snapshotBuilder,
                            @Qualifier("urule.repositoryService") RepositoryService repositoryService) {
        this.grayscaleRuleDao = grayscaleRuleDao;
        this.approvalDao = approvalDao;
        this.snapshotBuilder = snapshotBuilder;
        this.repositoryService = repositoryService;
    }

    // ---- 创建灰度规则 ----

    @Transactional
    public Map<String, Object> createRule(Long approvalId, GrayscaleStrategy strategy,
                                          Integer percentage, String conditionExpr,
                                          String description, String operator) {
        // 1. 验证审批单
        Approval approval = approvalDao.findById(approvalId);
        if (approval == null) throw new IllegalArgumentException("审批单不存在");
        if (approval.getStatus() != ApprovalStatus.APPROVED)
            throw new IllegalArgumentException("审批单状态必须为 APPROVED");

        // 2. 验证无已激活的灰度规则
        GrayscaleRule existing = grayscaleRuleDao.findActive(approval.getProject(), approval.getPackageId());
        if (existing != null) throw new IllegalArgumentException("该知识包已有激活的灰度规则");

        // 3. 加载快照
        PublishSnapshot snapshot = approvalDao.findSnapshotByApprovalId(approvalId);
        if (snapshot == null) throw new IllegalArgumentException("审批单快照不存在");
        Map<String, String> snapshotContent = parseSnapshotData(snapshot);

        // 4. 构建 gray 版 KnowledgePackage（验证可构建，不用于推送）
        try {
            snapshotBuilder.build(snapshotContent);
        } catch (Exception e) {
            throw new RuntimeException("构建灰度版本失败: " + e.getMessage(), e);
        }

        // 5. 持久化灰度规则
        long now = System.currentTimeMillis();
        approvalDao.updateStatusOnly(approvalId, ApprovalStatus.GRAYSCALE);
        GrayscaleRule rule = GrayscaleRule.builder()
                .project(approval.getProject())
                .packageId(approval.getPackageId())
                .approvalId(approvalId)
                .snapshotId(snapshot.getId())
                .strategy(strategy)
                .percentage(percentage)
                .conditionExpr(conditionExpr)
                .status(GrayscaleRuleStatus.ACTIVE)
                .description(description)
                .createdBy(operator)
                .createdAt(now)
                .updatedAt(now)
                .build();
        Long ruleId = grayscaleRuleDao.insert(rule);
        rule.setId(ruleId);

        // 6. 推送 gray 包到 clients（snapshot JSON，client 本地构建）
        String fullPackageId = normalizePackageId(approval.getProject(), approval.getPackageId());
        pushSnapshotToClients(fullPackageId + "__gray", "v" + approval.getVersion(), snapshotContent, approval.getProject());

        // 7. 推送路由规则 + 版本号到 clients
        Map<String, Object> routingPayload = new LinkedHashMap<>();
        routingPayload.put("packageId", fullPackageId);
        routingPayload.put("version", "v" + approval.getVersion());
        routingPayload.put("strategy", strategy.name());
        routingPayload.put("percentage", percentage);
        routingPayload.put("conditionExpr", conditionExpr);
        pushRoutingToClients(routingPayload, approval.getProject());

        log.info("灰度规则创建成功: ruleId={}, package={}, strategy={}, operator={}", ruleId, fullPackageId, strategy, operator);
        return buildRuleVo(rule);
    }

    // ---- 全量发布 ----

    @Transactional
    public Map<String, Object> fullRollout(Long ruleId, String operator) {
        GrayscaleRule rule = grayscaleRuleDao.findById(ruleId);
        if (rule == null) throw new IllegalArgumentException("灰度规则不存在");
        if (rule.getStatus() != GrayscaleRuleStatus.ACTIVE)
            throw new IllegalArgumentException("灰度规则状态必须为 ACTIVE");

        // 1. 加载快照，构建正式包（本地缓存用）
        PublishSnapshot snapshot = approvalDao.findSnapshotById(rule.getSnapshotId());
        Map<String, String> snapshotContent = parseSnapshotData(snapshot);
        KnowledgePackage kp;
        try {
            kp = snapshotBuilder.build(snapshotContent);
        } catch (Exception e) {
            throw new RuntimeException("构建正式版本失败: " + e.getMessage(), e);
        }

        // 2. 替换正式缓存
        String fullPackageId = normalizePackageId(rule.getProject(), rule.getPackageId());
        CacheUtils.getKnowledgeCache().putKnowledge(fullPackageId, kp);

        // 3. 推送到 clients（snapshot JSON + version，client 本地构建）
        Approval rolloutApproval = approvalDao.findById(rule.getApprovalId());
        String version = rolloutApproval != null ? "v" + rolloutApproval.getVersion() : "unknown";
        pushSnapshotToClients(fullPackageId, version, snapshotContent, rule.getProject());

        // 4. 推送路由规则停用
        pushRoutingDeleteToClients(fullPackageId, rule.getProject());

        // 5. 更新灰度规则状态
        grayscaleRuleDao.updateStatus(ruleId, GrayscaleRuleStatus.ROLLED_OUT);

        // 6. 更新审批单为 PUBLISHED
        Approval pubApproval = approvalDao.findById(rule.getApprovalId());
        if (pubApproval != null) {
            long now = System.currentTimeMillis();
            approvalDao.updatePublished(rule.getApprovalId(), operator, now);

            // 创建发布后快照作为下次 diff 基准
            createContentSnapshot(rule.getProject(), rule.getPackageId(), null, snapshotContent);
        }

        rule.setStatus(GrayscaleRuleStatus.ROLLED_OUT);
        log.info("灰度全量发布完成: ruleId={}, package={}, operator={}", ruleId, fullPackageId, operator);
        return buildRuleVo(rule);
    }

    // ---- 回退 ----

    @Transactional
    public Map<String, Object> rollback(Long ruleId, String operator) {
        GrayscaleRule rule = grayscaleRuleDao.findById(ruleId);
        if (rule == null) throw new IllegalArgumentException("灰度规则不存在");
        if (rule.getStatus() != GrayscaleRuleStatus.ACTIVE)
            throw new IllegalArgumentException("灰度规则状态必须为 ACTIVE");

        String fullPackageId = normalizePackageId(rule.getProject(), rule.getPackageId());

        // 1. 推送路由规则停用到 clients
        pushRoutingDeleteToClients(fullPackageId, rule.getProject());

        // 2. 更新灰度规则状态
        grayscaleRuleDao.updateStatus(ruleId, GrayscaleRuleStatus.ROLLED_BACK);

        // 3. 审批单状态回退为 APPROVED
        Approval rollbackApproval = approvalDao.findById(rule.getApprovalId());
        if (rollbackApproval != null && rollbackApproval.getStatus() == ApprovalStatus.GRAYSCALE) {
            approvalDao.updateStatusOnly(rule.getApprovalId(), ApprovalStatus.APPROVED);
        }

        rule.setStatus(GrayscaleRuleStatus.ROLLED_BACK);
        log.info("灰度回退完成: ruleId={}, package={}, operator={}", ruleId, fullPackageId, operator);
        return buildRuleVo(rule);
    }

    // ---- 列表 / 详情 ----

    public Map<String, Object> listRules(String project, String packageId, String status, int page, int pageSize) {
        int offset = (page - 1) * pageSize;
        List<GrayscaleRule> items = grayscaleRuleDao.listByFilter(project, packageId, status, offset, pageSize);
        int total = grayscaleRuleDao.countByFilter(project, packageId, status);
        List<Map<String, Object>> voList = new ArrayList<>();
        for (GrayscaleRule r : items) {
            voList.add(buildRuleVo(r));
        }
        return Map.of("items", voList, "total", total);
    }

    public Map<String, Object> getDetail(Long ruleId) {
        GrayscaleRule rule = grayscaleRuleDao.findById(ruleId);
        if (rule == null) throw new IllegalArgumentException("灰度规则不存在");
        return buildRuleVo(rule);
    }

    public Map<String, Object> getMetrics(Long ruleId, String startDate, String endDate) {
        GrayscaleRule rule = grayscaleRuleDao.findById(ruleId);
        if (rule == null) throw new IllegalArgumentException("灰度规则不存在");
        List<Map<String, Object>> metrics = grayscaleRuleDao.findMetrics(ruleId, startDate, endDate);
        return Map.of("rule", buildRuleVo(rule), "metrics", metrics);
    }

    /** 返回指定 packageId 的灰度 snapshot 内容（供 client 恢复灰度包） */
    public Map<String, Object> getSnapshotContent(String fullPackageId) {
        String norm = fullPackageId;
        if (norm.startsWith("/")) norm = norm.substring(1);
        String[] parts = norm.split("/", 2);
        if (parts.length < 2) throw new IllegalArgumentException("无效的 packageId: " + fullPackageId);
        GrayscaleRule rule = grayscaleRuleDao.findActive(parts[0], parts[1]);
        if (rule == null) throw new IllegalArgumentException("无活跃灰度规则: " + fullPackageId);
        PublishSnapshot snapshot = approvalDao.findSnapshotById(rule.getSnapshotId());
        if (snapshot == null) throw new IllegalArgumentException("快照不存在");
        Map<String, String> content = parseSnapshotData(snapshot);
        Approval approval = approvalDao.findById(rule.getApprovalId());
        String version = approval != null ? "v" + approval.getVersion() : "unknown";
        return Map.of("snapshotContent", content != null ? content : Map.of(), "version", version);
    }

    /** 返回活跃灰度状态（project 为 null 则返回全部），同时重新推送 gray 包 */
    public List<Map<String, Object>> getActiveStates(String project) {
        List<GrayscaleRule> rules = (project != null && !project.isEmpty())
                ? grayscaleRuleDao.findActiveByProject(project)
                : grayscaleRuleDao.findAllActive();
        List<Map<String, Object>> result = new ArrayList<>();
        for (GrayscaleRule rule : rules) {
            Map<String, Object> state = new LinkedHashMap<>();
            String fullPackageId = normalizePackageId(rule.getProject(), rule.getPackageId());
            state.put("packageId", fullPackageId);
            state.put("strategy", rule.getStrategy().name());
            state.put("percentage", rule.getPercentage());
            state.put("conditionExpr", rule.getConditionExpr());

            // 重新构建并推送 gray 包到 clients（走已有 /knowledgepackagereceiver 通道）
            try {
                PublishSnapshot snapshot = approvalDao.findSnapshotById(rule.getSnapshotId());
                Map<String, String> snapshotContent = parseSnapshotData(snapshot);
                KnowledgePackage kp = snapshotBuilder.build(snapshotContent);
                pushPackageToClients(fullPackageId + "__gray", kp, project);
            } catch (Exception e) {
                log.error("重新推送灰度包失败: ruleId={}", rule.getId(), e);
            }

            // 版本号
            Approval approval = approvalDao.findById(rule.getApprovalId());
            state.put("version", approval != null ? "v" + approval.getVersion() : "unknown");

            result.add(state);
        }
        return result;
    }

    // ---- 推送相关 ----

    private void pushPackageToClients(String packageId, KnowledgePackage kp, String project) {
        try {
            String content = serializePackage(kp);
            List<ClientConfig> clients = repositoryService.loadClientConfigs(project);
            for (ClientConfig config : clients) {
                boolean ok = pushToClient(packageId, content, config.getClient());
                log.info("推送 {} 到 {}: {}", packageId, config.getName(), ok ? "成功" : "失败");
            }
        } catch (Exception e) {
            log.error("推送灰度包失败: packageId={}", packageId, e);
        }
    }

    /** 推送 snapshot JSON + version 到 client /api/grayscale/package 端点，client 本地构建 */
    private void pushSnapshotToClients(String packageId, String version, Map<String, String> snapshotContent, String project) {
        try {
            Map<String, Object> payload = new LinkedHashMap<>();
            payload.put("packageId", packageId);
            payload.put("version", version);
            payload.put("snapshotContent", snapshotContent);
            String json = objectMapper.writeValueAsString(payload);
            List<ClientConfig> clients = repositoryService.loadClientConfigs(project);
            for (ClientConfig config : clients) {
                boolean ok = postToClient(config.getClient() + "/api/grayscale/package", json);
                log.info("推送snapshot {} 到 {}: {}", packageId, config.getName(), ok ? "成功" : "失败");
            }
        } catch (Exception e) {
            log.error("推送snapshot失败: packageId={}", packageId, e);
        }
    }

    private void pushRoutingToClients(Map<String, Object> payload, String project) {
        try {
            String json = objectMapper.writeValueAsString(payload);
            List<ClientConfig> clients = repositoryService.loadClientConfigs(project);
            for (ClientConfig config : clients) {
                boolean ok = postToClient(config.getClient() + "/api/grayscale/routing", json);
                log.info("推送路由规则到 {}: {}", config.getName(), ok ? "成功" : "失败");
            }
        } catch (Exception e) {
            log.error("推送路由规则失败", e);
        }
    }

    private void pushRoutingDeleteToClients(String packageId, String project) {
        try {
            List<ClientConfig> clients = repositoryService.loadClientConfigs(project);
            for (ClientConfig config : clients) {
                String url = config.getClient() + "/api/grayscale/routing/" +
                        URLEncoder.encode(packageId, "utf-8");
                boolean ok = deleteToClient(url);
                log.info("推送路由删除到 {}: {}", config.getName(), ok ? "成功" : "失败");
            }
        } catch (Exception e) {
            log.error("推送路由删除失败", e);
        }
    }

    private String serializePackage(KnowledgePackage kp) throws Exception {
        org.codehaus.jackson.map.ObjectMapper mapper1 = new org.codehaus.jackson.map.ObjectMapper();
        mapper1.setSerializationInclusion(JsonSerialize.Inclusion.NON_NULL);
        mapper1.configure(SerializationConfig.Feature.WRITE_DATES_AS_TIMESTAMPS, false);
        mapper1.setDateFormat(new SimpleDateFormat(Configure.getDateFormat()));
        StringWriter writer = new StringWriter();
        mapper1.writeValue(writer, new KnowledgePackageWrapper(kp));
        String content = writer.toString();
        writer.close();
        return content;
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

    private boolean postToClient(String urlStr, String jsonBody) {
        HttpURLConnection connection = null;
        try {
            URL url = new URL(urlStr);
            connection = (HttpURLConnection) url.openConnection();
            connection.setRequestMethod("POST");
            connection.setRequestProperty("Content-Type", "application/json");
            connection.setDoOutput(true);
            try (OutputStream os = connection.getOutputStream()) {
                os.write(jsonBody.getBytes(StandardCharsets.UTF_8));
            }
            return connection.getResponseCode() < 300;
        } catch (Exception e) {
            log.error("POST 到客户端失败: url={}, error={}", urlStr, e.getMessage());
            return false;
        } finally {
            if (connection != null) connection.disconnect();
        }
    }

    private boolean deleteToClient(String urlStr) {
        HttpURLConnection connection = null;
        try {
            URL url = new URL(urlStr);
            connection = (HttpURLConnection) url.openConnection();
            connection.setRequestMethod("DELETE");
            return connection.getResponseCode() < 300;
        } catch (Exception e) {
            log.error("DELETE 到客户端失败: url={}, error={}", urlStr, e.getMessage());
            return false;
        } finally {
            if (connection != null) connection.disconnect();
        }
    }

    // ---- helpers ----

    private Map<String, String> parseSnapshotData(PublishSnapshot snapshot) {
        if (snapshot == null || snapshot.getSnapshotData() == null) return null;
        try {
            return objectMapper.readValue(snapshot.getSnapshotData(),
                    new TypeReference<LinkedHashMap<String, String>>() {});
        } catch (Exception e) {
            return null;
        }
    }

    private void createContentSnapshot(String project, String packageId, Long approvalId, Map<String, String> contentMap) {
        try {
            String json = objectMapper.writeValueAsString(contentMap);
            approvalDao.insertSnapshot(com.caritasem.ruleuler.server.approval.model.PublishSnapshot.builder()
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

    private String normalizePackageId(String project, String packageId) {
        String pid = project + "/" + packageId;
        if (pid.startsWith("/")) pid = pid.substring(1);
        return pid;
    }

    private Map<String, Object> buildRuleVo(GrayscaleRule r) {
        Map<String, Object> vo = new LinkedHashMap<>();
        vo.put("id", r.getId());
        vo.put("project", r.getProject());
        vo.put("packageId", r.getPackageId());
        vo.put("approvalId", r.getApprovalId());
        vo.put("snapshotId", r.getSnapshotId());
        vo.put("strategy", r.getStrategy().name());
        vo.put("percentage", r.getPercentage());
        vo.put("conditionExpr", r.getConditionExpr());
        vo.put("status", r.getStatus().name());
        vo.put("description", r.getDescription());
        vo.put("createdBy", r.getCreatedBy());
        vo.put("createdAt", r.getCreatedAt());
        vo.put("updatedAt", r.getUpdatedAt());
        return vo;
    }
}
