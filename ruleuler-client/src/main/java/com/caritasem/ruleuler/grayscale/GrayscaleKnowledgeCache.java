package com.caritasem.ruleuler.grayscale;

import com.bstek.urule.runtime.KnowledgePackage;
import com.bstek.urule.runtime.cache.MemoryKnowledgeCache;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import jakarta.annotation.PostConstruct;
import java.net.HttpURLConnection;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

/**
 * 灰度感知缓存。替代默认 MemoryKnowledgeCache。
 * - delegate: 存 base 版本
 * - grayCache: 存 gray 版本
 * - routingRules: 路由规则
 * - versionMap: 版本号映射（独立于 KnowledgePackage）
 */
@Component("urule.knowledgeCache")
public class GrayscaleKnowledgeCache implements com.bstek.urule.runtime.cache.KnowledgeCache {

    private static final Logger log = LoggerFactory.getLogger(GrayscaleKnowledgeCache.class);
    private final ObjectMapper objectMapper = new ObjectMapper();

    @Value("${urule.resporityServerUrl:}")
    private String serverUrl;

    private SnapshotPackageBuilder snapshotPackageBuilder;

    public GrayscaleKnowledgeCache(SnapshotPackageBuilder snapshotPackageBuilder) {
        this.snapshotPackageBuilder = snapshotPackageBuilder;
    }

    private final MemoryKnowledgeCache delegate = new MemoryKnowledgeCache();
    private final ConcurrentHashMap<String, KnowledgePackage> grayCache = new ConcurrentHashMap<>();
    private final ConcurrentHashMap<String, GrayscaleRoutingRule> routingRules = new ConcurrentHashMap<>();
    private final ConcurrentHashMap<String, String> versionMap = new ConcurrentHashMap<>();

    /** 启动时从 server 恢复所有活跃灰度路由规则 */
    @PostConstruct
    public void init() {
        if (serverUrl == null || serverUrl.isBlank()) {
            log.info("未配置 server 地址，跳过灰度启动恢复");
            return;
        }
        try {
            recoverAllRoutingRules();
        } catch (Exception e) {
            log.warn("灰度启动恢复失败（非致命）: {}", e.getMessage());
        }
    }

    public boolean hasActiveRouting(String packageId) {
        return routingRules.containsKey(normalize(packageId));
    }

    public record GrayscaleRoutingRule(
            String strategy,   // PERCENTAGE / CONDITION
            Integer percentage,
            String conditionExpr  // JSON array
    ) {}

    // ---- KnowledgeCache 接口实现 ----

    @Override
    public KnowledgePackage getKnowledge(String packageId) {
        String norm = normalize(packageId);
        GrayscaleRoutingRule rule = routingRules.get(norm);
        if (rule != null) {
            GrayscaleContext.ContextInfo ctx = GrayscaleContext.get();
            if (ctx != null && shouldRouteToGray(norm, rule, ctx)) {
                KnowledgePackage gray = grayCache.get(norm);
                if (gray == null) {
                    gray = recoverGrayFromServer(norm);
                }
                return gray;
            }
        }
        return delegate.getKnowledge(packageId);
    }

    @Override
    public void putKnowledge(String packageId, KnowledgePackage kp) {
        if (packageId.endsWith("__gray")) {
            String norm = normalize(packageId.replace("__gray", ""));
            grayCache.put(norm, kp);
            log.info("灰度版本缓存: {}", norm);
        } else {
            delegate.putKnowledge(packageId, kp);
        }
    }

    @Override
    public void removeKnowledge(String packageId) {
        String norm = normalize(packageId);
        delegate.removeKnowledge(norm);
        grayCache.remove(norm);
        routingRules.remove(norm);
        versionMap.remove(norm);
    }

    // ---- 路由规则管理 ----

    public void activateRouting(String packageId, GrayscaleRoutingRule rule, String version) {
        String norm = normalize(packageId);
        routingRules.put(norm, rule);
        if (version != null) versionMap.put(norm, version);
        log.info("灰度路由激活: {}, strategy={}, version={}", norm, rule.strategy(), version);
    }

    public void deactivateRouting(String packageId) {
        String norm = normalize(packageId);
        routingRules.remove(norm);
        grayCache.remove(norm);
        log.info("灰度路由停用: {}", norm);
    }

    public String getVersion(String packageId) {
        return versionMap.get(normalize(packageId));
    }

    public void setVersion(String packageId, String version) {
        versionMap.put(normalize(packageId), version);
    }

    /**
     * 版本号校验：只有新版本才接受。格式为 "vN"，比较数字部分。
     */
    public boolean acceptVersion(String packageId, String newVersion) {
        String norm = normalize(packageId);
        String current = versionMap.get(norm);
        if (current == null) return true;  // 无历史版本，接受
        return parseVersionNum(newVersion) > parseVersionNum(current);
    }

    private int parseVersionNum(String v) {
        if (v == null) return 0;
        try {
            return v.startsWith("v") ? Integer.parseInt(v.substring(1)) : Integer.parseInt(v);
        } catch (NumberFormatException e) {
            return 0;
        }
    }

    // ---- 路由逻辑 ----

    private boolean shouldRouteToGray(String norm, GrayscaleRoutingRule rule, GrayscaleContext.ContextInfo ctx) {
        if ("PERCENTAGE".equals(rule.strategy())) {
            int hash = Math.abs(ctx.routingKey().hashCode());
            return (hash % 100) < rule.percentage();
        } else if ("CONDITION".equals(rule.strategy()) && rule.conditionExpr() != null) {
            try {
                return ConditionEvaluator.evaluate(rule.conditionExpr(), ctx.attributes());
            } catch (Exception e) {
                log.warn("条件求值失败: {}", e.getMessage());
                return false;
            }
        }
        return false;
    }

    /** 启动时从 server 恢复所有活跃灰度路由规则（不含包内容，包由 server 主动推送） */
    private void recoverAllRoutingRules() throws Exception {
        // client 启动时尚不知道有哪些 project，用 serverUrl 基路径拉全局活跃状态
        // 需要配合 server 端调整：增加无 project 参数时返回所有活跃规则
        String url = serverUrl + "/api/grayscale/active-states";
        HttpURLConnection conn = (HttpURLConnection) new URL(url).openConnection();
        conn.setConnectTimeout(5000);
        conn.setReadTimeout(10000);
        if (conn.getResponseCode() >= 300) {
            log.warn("拉取灰度活跃状态失败: HTTP {}", conn.getResponseCode());
            conn.disconnect();
            return;
        }
        String body = new String(conn.getInputStream().readAllBytes(), StandardCharsets.UTF_8);
        conn.disconnect();
        applyRoutingStates(body);
    }

    /** grayCache miss 时从 server 恢复：拉 snapshot 内容，本地构建 KnowledgePackage */
    private KnowledgePackage recoverGrayFromServer(String norm) {
        if (serverUrl == null || serverUrl.isBlank()) {
            log.warn("未配置 server 地址，无法恢复灰度: {}", norm);
            return null;
        }
        try {
            // 1. 先拉路由规则
            String statesUrl = serverUrl + "/api/grayscale/active-states?project=" + norm.split("/", 2)[0];
            HttpURLConnection conn = (HttpURLConnection) new URL(statesUrl).openConnection();
            conn.setConnectTimeout(3000);
            conn.setReadTimeout(5000);
            if (conn.getResponseCode() >= 300) {
                log.warn("恢复灰度路由失败: {}", conn.getResponseCode());
                conn.disconnect();
                return null;
            }
            String body = new String(conn.getInputStream().readAllBytes(), StandardCharsets.UTF_8);
            conn.disconnect();
            applyRoutingStates(body);

            // 2. 再拉 snapshot 内容
            String snapshotUrl = serverUrl + "/api/grayscale/snapshot?packageId=" + java.net.URLEncoder.encode(norm, "UTF-8");
            conn = (HttpURLConnection) new URL(snapshotUrl).openConnection();
            conn.setConnectTimeout(3000);
            conn.setReadTimeout(10000);
            if (conn.getResponseCode() >= 300) {
                log.warn("恢复灰度 snapshot 失败: {}", conn.getResponseCode());
                conn.disconnect();
                return null;
            }
            String snapshotJson = new String(conn.getInputStream().readAllBytes(), StandardCharsets.UTF_8);
            conn.disconnect();

            // 3. 本地构建 KnowledgePackage
            Map<String, Object> snapshotResp = objectMapper.readValue(snapshotJson,
                    new TypeReference<Map<String, Object>>() {});
            @SuppressWarnings("unchecked")
            Map<String, String> snapshotContent = (Map<String, String>) snapshotResp.get("snapshotContent");
            String version = (String) snapshotResp.get("version");
            if (snapshotContent == null || snapshotContent.isEmpty()) {
                log.warn("灰度 snapshot 内容为空: {}", norm);
                return null;
            }

            KnowledgePackage kp = snapshotPackageBuilder.build(snapshotContent);
            cacheGrayPackage(norm, kp);
            if (version != null) versionMap.put(norm, version);
            log.info("灰度包恢复成功: {}, version={}", norm, version);
            return kp;
        } catch (Exception e) {
            log.warn("恢复灰度状态失败: {} - {}", norm, e.getMessage());
            return null;
        }
    }

    /** 直接缓存灰度包（不走 putKnowledge 的 __gray 后缀逻辑） */
    private void cacheGrayPackage(String norm, KnowledgePackage kp) {
        grayCache.put(norm, kp);
    }

    /** 解析 server 返回的活跃状态列表，恢复路由规则和版本号到本地 */
    private void applyRoutingStates(String json) throws Exception {
        List<Map<String, Object>> states = objectMapper.readValue(json,
                new TypeReference<List<Map<String, Object>>>() {});
        for (Map<String, Object> state : states) {
            String pkgId = (String) state.get("packageId");
            String normPkg = normalize(pkgId);
            GrayscaleRoutingRule rule = new GrayscaleRoutingRule(
                    (String) state.get("strategy"),
                    state.get("percentage") != null ? Integer.valueOf(state.get("percentage").toString()) : null,
                    (String) state.get("conditionExpr"));
            routingRules.put(normPkg, rule);
            String ver = (String) state.get("version");
            if (ver != null) versionMap.put(normPkg, ver);
            log.info("恢复灰度路由: {}, strategy={}", normPkg, rule.strategy());
        }
    }

    private String normalize(String packageId) {
        if (packageId.startsWith("/")) packageId = packageId.substring(1);
        return packageId;
    }
}
