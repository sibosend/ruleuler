package com.caritasem.ruleuler.grayscale;

import com.bstek.urule.runtime.KnowledgePackage;
import jakarta.servlet.http.HttpServletRequest;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.*;

import java.nio.charset.StandardCharsets;
import java.util.Map;

/**
 * 接收 server 推送的路由规则、版本号和包内容。
 * 包内容为 snapshot JSON {path: xmlContent}，本地构建 KnowledgePackage，不走 Jackson 1.x 序列化。
 */
@RestController
@RequestMapping("/api/grayscale")
public class GrayscaleRoutingController {

    private static final Logger log = LoggerFactory.getLogger(GrayscaleRoutingController.class);

    @Autowired
    private GrayscaleKnowledgeCache cache;

    @Autowired
    private SnapshotPackageBuilder snapshotPackageBuilder;

    /**
     * 激活路由规则 + 版本号（不含包内容，仅路由配置变更时用）
     */
    @PostMapping("/routing")
    public Map<String, String> activateRouting(@RequestBody Map<String, Object> body) {
        String packageId = (String) body.get("packageId");
        String version = (String) body.get("version");
        String strategy = (String) body.get("strategy");
        Integer percentage = body.get("percentage") != null ? Integer.valueOf(body.get("percentage").toString()) : null;
        String conditionExpr = (String) body.get("conditionExpr");

        // 版本号校验：拒绝旧版本
        if (version != null && !cache.acceptVersion(packageId, version)) {
            log.warn("拒绝旧版本: packageId={}, version={}", packageId, version);
            return Map.of("status", "rejected", "reason", "stale version");
        }

        GrayscaleKnowledgeCache.GrayscaleRoutingRule rule =
                new GrayscaleKnowledgeCache.GrayscaleRoutingRule(strategy, percentage, conditionExpr);
        cache.activateRouting(packageId, rule, version);
        return Map.of("status", "ok");
    }

    /**
     * 推送包内容（snapshot JSON）+ 版本号，本地构建 KnowledgePackage。
     * body: {packageId, version, snapshotContent: {path: xmlContent}}
     * 用于：灰度包推送、普通发布推送（替代 /knowledgepackagereceiver 的 KnowledgePackage 序列化方式）
     */
    @PostMapping("/package")
    public Map<String, String> receivePackage(@RequestBody Map<String, Object> body) {
        String packageId = (String) body.get("packageId");
        String version = (String) body.get("version");

        // 版本号校验
        if (version != null && !cache.acceptVersion(packageId, version)) {
            log.warn("拒绝旧版本包: packageId={}, version={}", packageId, version);
            return Map.of("status", "rejected", "reason", "stale version");
        }

        @SuppressWarnings("unchecked")
        Map<String, String> snapshotContent = (Map<String, String>) body.get("snapshotContent");
        if (snapshotContent == null || snapshotContent.isEmpty()) {
            return Map.of("status", "error", "reason", "empty snapshotContent");
        }

        try {
            KnowledgePackage kp = snapshotPackageBuilder.build(snapshotContent);
            cache.putKnowledge(packageId, kp);
            if (version != null) cache.setVersion(packageId, version);
            log.info("接收包成功: packageId={}, version={}, resources={}", packageId, version, snapshotContent.size());
            return Map.of("status", "ok");
        } catch (Exception e) {
            log.error("构建包失败: packageId={}, error={}", packageId, e.getMessage());
            return Map.of("status", "error", "reason", e.getMessage());
        }
    }

    /**
     * 仅更新版本号
     */
    @PostMapping("/routing/version")
    public Map<String, String> updateVersion(@RequestBody Map<String, Object> body) {
        String packageId = (String) body.get("packageId");
        String version = (String) body.get("version");
        if (version != null && !cache.acceptVersion(packageId, version)) {
            return Map.of("status", "rejected", "reason", "stale version");
        }
        cache.setVersion(packageId, version);
        return Map.of("status", "ok");
    }

    /**
     * 停用路由规则
     */
    @DeleteMapping("/routing/**")
    public Map<String, String> deactivateRouting(HttpServletRequest request) {
        String path = request.getRequestURI().substring("/api/grayscale/routing/".length());
        String packageId = java.net.URLDecoder.decode(path, StandardCharsets.UTF_8);
        cache.deactivateRouting(packageId);
        return Map.of("status", "ok");
    }
}
