package com.caritasem.ruleuler.controller;

import com.alibaba.fastjson2.JSONObject;
import com.bstek.urule.Utils;
import com.bstek.urule.model.GeneralEntity;
import com.bstek.urule.runtime.KnowledgePackage;
import com.bstek.urule.runtime.KnowledgeSession;
import com.bstek.urule.runtime.KnowledgeSessionFactory;
import com.bstek.urule.runtime.service.KnowledgeService;
import com.caritasem.ruleuler.dto.RespDTO;
import com.caritasem.ruleuler.grayscale.GrayscaleContext;
import com.caritasem.ruleuler.grayscale.GrayscaleKnowledgeCache;
import com.caritasem.ruleuler.monitoring.TraceContext;
import com.caritasem.ruleuler.monitoring.VarEventProducer;
import jakarta.servlet.http.HttpServletRequest;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.web.context.WebServerApplicationContext;
import org.springframework.web.bind.annotation.*;

import java.net.InetAddress;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.UUID;

@RestController
public class RuleController {

    @Autowired
    private WebServerApplicationContext webServerAppContext;

    @Autowired(required = false)
    private VarEventProducer varEventProducer;

    @Autowired
    private GrayscaleKnowledgeCache grayscaleCache;

    private static Logger log = LoggerFactory.getLogger(RuleController.class);

    @GetMapping("health")
    public HashMap<String, Object> health() {
        HashMap<String, Object> result = new HashMap<>();
        result.put("code", 200);
        result.put("status", "UP");
        result.put("service", "urule.client");
        result.put("timestamp", System.currentTimeMillis());

        try {
            String ip = InetAddress.getLocalHost().getHostAddress();
            int port = webServerAppContext.getWebServer().getPort();
            result.put("ip", ip);
            result.put("port", port);
        } catch (Exception e) {
            result.put("ip", "unknown");
            result.put("port", "unknown");
        }

        return result;
    }

    @PostMapping("process/{project}/{knowledge}/{process}")
    public RespDTO process(@RequestBody Map<String, JSONObject> body,
            @PathVariable String project,
            @PathVariable String process,
            @PathVariable String knowledge,
            HttpServletRequest httpRequest) {
        String executionId = UUID.randomUUID().toString();
        long startMs = System.currentTimeMillis();
        String knowledgePackageId = project + "/" + knowledge;

        // 设置灰度路由上下文
        String routingKey = httpRequest.getHeader("X-Routing-Key");
        if (routingKey == null) routingKey = executionId;
        Map<String, Object> routingAttrs = new HashMap<>();
        for (Map.Entry<String, JSONObject> entry : body.entrySet()) {
            routingAttrs.put(entry.getKey(), entry.getValue());
        }
        GrayscaleContext.set(new GrayscaleContext.ContextInfo(routingKey, routingAttrs));

        try {
            KnowledgeService knowledgeService = (KnowledgeService) Utils.getApplicationContext()
                    .getBean(KnowledgeService.BEAN_ID);

            KnowledgePackage knowledgePackage = knowledgeService.getKnowledge(knowledgePackageId);
            KnowledgeSession session = KnowledgeSessionFactory.newKnowledgeSession(knowledgePackage);
            Map<String, String> varCategoryMap = knowledgePackage.getVariableCateogoryMap();

            Map<String, GeneralEntity> entities = new LinkedHashMap<>();
            for (Map.Entry<String, JSONObject> entry : body.entrySet()) {
                String category = entry.getKey();
                String clazz = varCategoryMap.get(category);
                if (clazz == null) {
                    return new RespDTO(400, "未知变量类别: " + category);
                }
                log.info("knowledgePackageId:{}, category:{}, clazz:{}", knowledgePackageId, category, clazz);
                GeneralEntity entity = new GeneralEntity(clazz);
                for (Map.Entry<String, Object> field : entry.getValue().entrySet()) {
                    entity.put(field.getKey(), field.getValue());
                }
                entities.put(category, entity);
                session.insert(entity);
            }

            TraceContext.set(new TraceContext.TraceInfo(executionId, project, knowledgePackageId, process));
            try {
                session.startProcess(process);
                session.writeLogFile();
            } finally {
                TraceContext.clear();
            }

            // 合并参数输出和变量输出（只返回规则引擎写入的字段，排除原样输入）
            Map<String, Object> result = new LinkedHashMap<>();
            Map<String, Object> params = session.getParameters();
            if (params != null) {
                result.putAll(params);
            }
            for (Map.Entry<String, GeneralEntity> e : entities.entrySet()) {
                String category = e.getKey();
                GeneralEntity entity = e.getValue();
                JSONObject input = body.get(category);
                Map<String, Object> varOutput = new LinkedHashMap<>();
                for (Object key : entity.keySet()) {
                    String k = String.valueOf(key);
                    Object newVal = entity.get(key);
                    Object oldVal = input.get(k);
                    // 跳过和输入完全一样的字段
                    if (oldVal != null && oldVal.equals(newVal)) {
                        continue;
                    }
                    varOutput.put(k, newVal);
                }
                if (!varOutput.isEmpty()) {
                    result.put(category, varOutput);
                }
            }

            // 监控采集 — 成功路径
            long execMs = System.currentTimeMillis() - startMs;
            if (varEventProducer != null) {
                try {
                    varEventProducer.produceSuccess(executionId, project, knowledgePackageId, process,
                            execMs, body, entities, session, knowledgePackage);
                } catch (Exception ex) {
                    log.warn("监控采集异常", ex);
                }
            }

            // 构建响应（含系统元数据）
            RespDTO resp = new RespDTO(200, "ok", result);
            String version = grayscaleCache.getVersion(knowledgePackageId);
            Map<String, Object> meta = new LinkedHashMap<>();
            meta.put("executionId", executionId);
            meta.put("packageId", knowledgePackageId);
            if (version != null) meta.put("version", version);
            resp.setMeta(meta);
            return resp;

        } catch (Exception e) {
            // 监控采集 — 失败路径
            long execMs = System.currentTimeMillis() - startMs;
            if (varEventProducer != null) {
                try {
                    varEventProducer.produceFailure(executionId, project, project + "/" + knowledge, process, execMs);
                } catch (Exception ex) {
                    log.warn("监控采集异常", ex);
                }
            }
            e.printStackTrace();

            RespDTO resp = new RespDTO(500, e.getMessage());
            String version = grayscaleCache.getVersion(knowledgePackageId);
            Map<String, Object> meta = new LinkedHashMap<>();
            meta.put("executionId", executionId);
            meta.put("packageId", knowledgePackageId);
            if (version != null) meta.put("version", version);
            resp.setMeta(meta);
            return resp;
        } finally {
            GrayscaleContext.clear();
        }
    }
}
