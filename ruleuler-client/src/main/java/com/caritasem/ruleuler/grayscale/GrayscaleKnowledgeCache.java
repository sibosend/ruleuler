package com.caritasem.ruleuler.grayscale;

import com.bstek.urule.runtime.KnowledgePackage;
import com.bstek.urule.runtime.cache.MemoryKnowledgeCache;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

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

    private final MemoryKnowledgeCache delegate = new MemoryKnowledgeCache();
    private final ConcurrentHashMap<String, KnowledgePackage> grayCache = new ConcurrentHashMap<>();
    private final ConcurrentHashMap<String, GrayscaleRoutingRule> routingRules = new ConcurrentHashMap<>();
    private final ConcurrentHashMap<String, String> versionMap = new ConcurrentHashMap<>();

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

    // ---- 路由逻辑 ----

    private boolean shouldRouteToGray(String norm, GrayscaleRoutingRule rule, GrayscaleContext.ContextInfo ctx) {
        if ("PERCENTAGE".equals(rule.strategy())) {
            int hash = Math.abs(ctx.routingKey().hashCode());
            return (hash % 100) < rule.percentage();
        } else if ("CONDITION".equals(rule.strategy()) && rule.conditionExpr() != null) {
            try {
                List<ConditionEvaluator.ConditionItem> conditions = objectMapper.readValue(
                        rule.conditionExpr(),
                        new TypeReference<List<ConditionEvaluator.ConditionItem>>() {});
                return ConditionEvaluator.evaluate(conditions, ctx.attributes());
            } catch (Exception e) {
                log.warn("条件求值失败: {}", e.getMessage());
                return false;
            }
        }
        return false;
    }

    /** grayCache miss 时从 server 恢复 */
    private KnowledgePackage recoverGrayFromServer(String norm) {
        // TODO: 调 server GET /api/grayscale/active-states 恢复
        log.warn("grayCache miss，灰度版本暂不可用: {}", norm);
        return null;
    }

    private String normalize(String packageId) {
        if (packageId.startsWith("/")) packageId = packageId.substring(1);
        return packageId;
    }
}
