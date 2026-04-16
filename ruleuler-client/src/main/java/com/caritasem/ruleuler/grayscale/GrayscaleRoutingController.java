package com.caritasem.ruleuler.grayscale;

import jakarta.servlet.http.HttpServletRequest;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.*;

import java.nio.charset.StandardCharsets;

import java.util.Map;

/**
 * 接收 server 推送的路由规则和版本号。
 */
@RestController
@RequestMapping("/api/grayscale")
public class GrayscaleRoutingController {

    @Autowired
    private GrayscaleKnowledgeCache cache;

    /**
     * 激活/更新路由规则 + 版本号
     * body: {packageId, version, strategy, percentage, conditionExpr}
     */
    @PostMapping("/routing")
    public Map<String, String> activateRouting(@RequestBody Map<String, Object> body) {
        String packageId = (String) body.get("packageId");
        String version = (String) body.get("version");
        String strategy = (String) body.get("strategy");
        Integer percentage = body.get("percentage") != null ? Integer.valueOf(body.get("percentage").toString()) : null;
        String conditionExpr = (String) body.get("conditionExpr");

        GrayscaleKnowledgeCache.GrayscaleRoutingRule rule =
                new GrayscaleKnowledgeCache.GrayscaleRoutingRule(strategy, percentage, conditionExpr);
        cache.activateRouting(packageId, rule, version);
        return Map.of("status", "ok");
    }

    /**
     * 仅更新版本号（普通发布时 server 推送）
     * body: {packageId, version}
     */
    @PostMapping("/routing/version")
    public Map<String, String> updateVersion(@RequestBody Map<String, Object> body) {
        String packageId = (String) body.get("packageId");
        String version = (String) body.get("version");
        cache.setVersion(packageId, version);
        return Map.of("status", "ok");
    }

    /**
     * 停用路由规则
     */
    @DeleteMapping("/routing/**")
    public Map<String, String> deactivateRouting(HttpServletRequest request) {
        // path: /api/grayscale/routing/{packageId}
        String path = request.getRequestURI().substring("/api/grayscale/routing/".length());
        String packageId = java.net.URLDecoder.decode(path, StandardCharsets.UTF_8);
        cache.deactivateRouting(packageId);
        return Map.of("status", "ok");
    }
}
