package com.caritasem.ruleuler.grayscale;

import com.bstek.urule.runtime.KnowledgePackage;
import com.caritasem.ruleuler.function.FunctionDepsGate;
import jakarta.servlet.http.HttpServletRequest;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
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

    @Autowired
    private FunctionDepsGate functionDepsGate;

    /**
     * 激活路由规则 + 版本号（不含包内容，仅路由配置变更时用）
     */
    @PostMapping("/routing")
    public ResponseEntity<Map<String, String>> activateRouting(@RequestBody Map<String, Object> body) {
        String packageId = (String) body.get("packageId");
        String version = (String) body.get("version");
        String strategy = (String) body.get("strategy");
        Integer percentage = body.get("percentage") != null ? Integer.valueOf(body.get("percentage").toString()) : null;
        String conditionExpr = (String) body.get("conditionExpr");

        // 版本号校验：拒绝旧版本
        if (version != null && !cache.acceptVersion(packageId, version)) {
            log.warn("拒绝旧版本: packageId={}, version={}", packageId, version);
            return rejected("stale version");
        }

        GrayscaleKnowledgeCache.GrayscaleRoutingRule rule =
                new GrayscaleKnowledgeCache.GrayscaleRoutingRule(strategy, percentage, conditionExpr);
        cache.activateRouting(packageId, rule, version);
        return ok();
    }

    /**
     * 推送包内容（snapshot JSON）+ 版本号，本地构建 KnowledgePackage。
     * body: {packageId, version, snapshotContent: {path: xmlContent}}
     * 用于：灰度包推送、普通发布推送（替代 /knowledgepackagereceiver 的 KnowledgePackage 序列化方式）
     */
    @PostMapping("/package")
    public ResponseEntity<Map<String, String>> receivePackage(@RequestBody Map<String, Object> body) {
        String packageId = (String) body.get("packageId");
        String version = (String) body.get("version");

        // 版本号校验
        if (version != null && !cache.acceptVersion(packageId, version)) {
            log.warn("拒绝旧版本包: packageId={}, version={}", packageId, version);
            return rejected("stale version");
        }

        @SuppressWarnings("unchecked")
        Map<String, String> snapshotContent = (Map<String, String>) body.get("snapshotContent");
        if (snapshotContent == null || snapshotContent.isEmpty()) {
            return error("empty snapshotContent");
        }

        if (!functionDepsGate.ready(packageId)) {
            log.warn("函数版本不齐，拒收推送包: {}", packageId);
            return rejected("function deps mismatch");
        }

        try {
            KnowledgePackage kp = snapshotPackageBuilder.build(snapshotContent);
            cache.putKnowledge(packageId, kp);
            if (version != null) cache.setVersion(packageId, version);
            log.info("接收包成功: packageId={}, version={}, resources={}", packageId, version, snapshotContent.size());
            return ok();
        } catch (Exception e) {
            log.error("构建包失败: packageId={}, error={}", packageId, e.getMessage());
            return error(e.getMessage());
        }
    }

    /**
     * 仅更新版本号
     */
    @PostMapping("/routing/version")
    public ResponseEntity<Map<String, String>> updateVersion(@RequestBody Map<String, Object> body) {
        String packageId = (String) body.get("packageId");
        String version = (String) body.get("version");
        if (version != null && !cache.acceptVersion(packageId, version)) {
            return rejected("stale version");
        }
        cache.setVersion(packageId, version);
        return ok();
    }

    /**
     * 停用路由规则
     */
    @DeleteMapping("/routing/**")
    public ResponseEntity<Map<String, String>> deactivateRouting(HttpServletRequest request) {
        String path = request.getRequestURI().substring("/api/grayscale/routing/".length());
        String packageId = java.net.URLDecoder.decode(path, StandardCharsets.UTF_8);
        cache.deactivateRouting(packageId);
        return ok();
    }

    private static ResponseEntity<Map<String, String>> ok() {
        return ResponseEntity.ok(Map.of("status", "ok"));
    }

    private static ResponseEntity<Map<String, String>> rejected(String reason) {
        return ResponseEntity.status(HttpStatus.CONFLICT).body(Map.of("status", "rejected", "reason", reason));
    }

    private static ResponseEntity<Map<String, String>> error(String reason) {
        return ResponseEntity.badRequest().body(Map.of("status", "error", "reason", reason == null ? "" : reason));
    }

    /**
     * 诊断：检查路由规则 + cache 实例
     */
    @GetMapping("/debug")
    public Map<String, Object> debug() {
        Map<String, Object> info = new java.util.LinkedHashMap<>();
        info.put("cacheClass", cache.getClass().getName());

        // 检查 CacheUtils 用的是哪个 cache
        try {
            var cacheUtilsCache = com.bstek.urule.runtime.cache.CacheUtils.getKnowledgeCache();
            info.put("cacheUtilsClass", cacheUtilsCache != null ? cacheUtilsCache.getClass().getName() : "null");
            info.put("sameInstance", cache == cacheUtilsCache);
        } catch (Exception e) {
            info.put("cacheUtilsError", e.getMessage());
        }

        info.put("hasActiveRouting", cache.hasActiveRouting("airport_gate_allocation_db/gate_pkg"));
        return info;
    }
}
