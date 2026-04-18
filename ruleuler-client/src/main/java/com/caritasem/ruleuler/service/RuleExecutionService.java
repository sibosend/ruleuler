package com.caritasem.ruleuler.service;

import com.alibaba.fastjson2.JSONObject;
import com.bstek.urule.Utils;
import com.bstek.urule.model.GeneralEntity;
import com.bstek.urule.runtime.KnowledgePackage;
import com.bstek.urule.runtime.KnowledgeSession;
import com.bstek.urule.runtime.KnowledgeSessionFactory;
import com.bstek.urule.runtime.service.KnowledgeService;
import com.caritasem.ruleuler.dto.RuleExecutionResult;
import com.caritasem.ruleuler.grayscale.GrayscaleContext;
import com.caritasem.ruleuler.grayscale.GrayscaleKnowledgeCache;
import com.caritasem.ruleuler.grayscale.GrayscaleMetricsReporter;
import com.caritasem.ruleuler.monitoring.TraceContext;
import com.caritasem.ruleuler.monitoring.VarEventProducer;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.UUID;

@Service
public class RuleExecutionService {

    private static final Logger log = LoggerFactory.getLogger(RuleExecutionService.class);

    @Autowired(required = false)
    private VarEventProducer varEventProducer;

    @Autowired
    private GrayscaleKnowledgeCache grayscaleCache;

    @Autowired(required = false)
    private GrayscaleMetricsReporter grayscaleReporter;

    public RuleExecutionResult execute(String project, String knowledge,
                                        String process, Map<String, JSONObject> body) {
        String executionId = UUID.randomUUID().toString();
        long startMs = System.currentTimeMillis();
        String knowledgePackageId = project + "/" + knowledge;

        // 灰度路由上下文
        String routingKey = executionId;
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
                    return new RuleExecutionResult(400, "未知变量类别: " + category, null, null);
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

            // 合并输出（只返回被规则修改的字段）
            Map<String, Object> result = com.caritasem.ruleuler.base.RuleOutputCollector
                    .collectOutput(session, entities, body);

            // 监控 — 成功
            long execMs = System.currentTimeMillis() - startMs;
            Boolean routedToGray = GrayscaleContext.wasRoutedToGray();
            String grayscaleBucket = (routedToGray != null && routedToGray) ? "GRAY" : "BASE";
            if (varEventProducer != null) {
                try {
                    varEventProducer.produceSuccess(executionId, project, knowledgePackageId, process,
                            execMs, body, entities, session, knowledgePackage, grayscaleBucket);
                } catch (Exception ex) {
                    log.warn("监控采集异常", ex);
                }
            }
            if (grayscaleReporter != null && routedToGray != null) {
                String ver = routedToGray ? "GRAY" : "BASE";
                grayscaleReporter.report(knowledgePackageId, ver, true, execMs);
            }

            // 构建元数据
            String version = grayscaleCache.getVersion(knowledgePackageId);
            Map<String, Object> meta = new LinkedHashMap<>();
            meta.put("executionId", executionId);
            meta.put("packageId", knowledgePackageId);
            if (version != null) meta.put("version", version);
            meta.put("route", routedToGray != null && routedToGray ? "GRAY" : "BASE");

            return new RuleExecutionResult(200, "ok", result, meta);

        } catch (Exception e) {
            // 监控 — 失败
            long execMs = System.currentTimeMillis() - startMs;
            Boolean failedRoutedToGray = GrayscaleContext.wasRoutedToGray();
            String failedBucket = (failedRoutedToGray != null && failedRoutedToGray) ? "GRAY" : "BASE";
            if (varEventProducer != null) {
                try {
                    varEventProducer.produceFailure(executionId, project, project + "/" + knowledge, process, execMs, failedBucket);
                } catch (Exception ex) {
                    log.warn("监控采集异常", ex);
                }
            }
            e.printStackTrace();

            String version = grayscaleCache.getVersion(knowledgePackageId);
            Map<String, Object> meta = new LinkedHashMap<>();
            meta.put("executionId", executionId);
            meta.put("packageId", knowledgePackageId);
            if (version != null) meta.put("version", version);

            return new RuleExecutionResult(500, e.getMessage(), null, meta);
        } finally {
            GrayscaleContext.clear();
        }
    }
}
