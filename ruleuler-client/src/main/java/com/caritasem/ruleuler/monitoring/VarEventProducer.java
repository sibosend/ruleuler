package com.caritasem.ruleuler.monitoring;

import com.alibaba.fastjson2.JSONObject;
import com.bstek.urule.model.GeneralEntity;
import com.bstek.urule.model.library.Datatype;
import com.bstek.urule.model.library.ResourceLibrary;
import com.bstek.urule.model.library.variable.Variable;
import com.bstek.urule.model.library.variable.VariableCategory;
import com.bstek.urule.runtime.KnowledgePackage;
import com.bstek.urule.runtime.KnowledgeSession;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.*;
import java.util.concurrent.BlockingQueue;

/**
 * 变量采集器：在 /process 执行完成后构造 VarLogRow 并投递到队列。
 * <p>
 * 非 Spring Bean，由调用方传入队列实例。
 */
public class VarEventProducer {

    private static final Logger log = LoggerFactory.getLogger(VarEventProducer.class);
    private static final String PARAM_CATEGORY = "__param__";

    private final BlockingQueue<VarLogRow> queue;

    public VarEventProducer(BlockingQueue<VarLogRow> queue) {
        this.queue = queue;
    }

    /**
     * 成功路径：记录输入/输出变量和参数。
     */
    public void produceSuccess(
            String executionId, String project, String packageId, String flowId,
            long execMs,
            Map<String, JSONObject> body,
            Map<String, GeneralEntity> entities,
            KnowledgeSession session,
            KnowledgePackage knowledgePackage,
            String grayscaleBucket
    ) {
        long now = System.currentTimeMillis();
        // 从变量库定义构建类型缓存：categoryName -> (varName -> Datatype)
        Map<String, Map<String, Datatype>> typeCache = buildTypeCache(knowledgePackage);

        // 1. 输入变量
        for (Map.Entry<String, JSONObject> entry : body.entrySet()) {
            String category = entry.getKey();
            Map<String, Datatype> varTypes = typeCache.get(category);
            if (varTypes == null) continue;

            for (Map.Entry<String, Object> field : entry.getValue().entrySet()) {
                String varName = field.getKey();
                Datatype dt = varTypes.get(varName);
                if (dt == null) continue; // 变量库中未定义，跳过

                VarTypeMapper.MapResult mr = VarTypeMapper.map(dt, field.getValue());
                if (mr.skip()) continue;

                offer(new VarLogRow(executionId, project, packageId, flowId,
                        category, varName, dt.name(), mr.valNum(), mr.valStr(),
                        "input", execMs, now, grayscaleBucket));
            }
        }

        // 2. 输出变量（仅记录变更字段）
        for (Map.Entry<String, GeneralEntity> entry : entities.entrySet()) {
            String category = entry.getKey();
            GeneralEntity entity = entry.getValue();
            JSONObject input = body.get(category);
            Map<String, Datatype> varTypes = typeCache.get(category);
            if (varTypes == null) continue;

            for (Map.Entry<String, Object> field : entity.entrySet()) {
                String varName = field.getKey();
                Object newVal = field.getValue();
                Object oldVal = (input != null) ? input.get(varName) : null;
                if (Objects.equals(oldVal, newVal)) continue;

                Datatype dt = varTypes.get(varName);
                if (dt == null) continue;

                VarTypeMapper.MapResult mr = VarTypeMapper.map(dt, newVal);
                if (mr.skip()) continue;

                offer(new VarLogRow(executionId, project, packageId, flowId,
                        category, varName, dt.name(), mr.valNum(), mr.valStr(),
                        "output", execMs, now, grayscaleBucket));
            }
        }

        // 3. 参数输出
        Map<String, Object> params = session.getParameters();
        Map<String, String> paramDefs = knowledgePackage.getParameters();
        if (params != null) {
            for (Map.Entry<String, Object> entry : params.entrySet()) {
                String paramName = entry.getKey();
                Object paramValue = entry.getValue();
                String typeName = paramDefs != null ? paramDefs.get(paramName) : null;

                Datatype dt = null;
                if (typeName != null) {
                    try {
                        dt = Datatype.valueOf(typeName);
                    } catch (IllegalArgumentException e) {
                        dt = null;
                    }
                }

                // 类型未定义时，根据实际值推断，保障参数监控不丢
                if (dt == null) {
                    if (paramValue == null) {
                        dt = Datatype.String;
                    } else if (paramValue instanceof Integer) {
                        dt = Datatype.Integer;
                    } else if (paramValue instanceof Long) {
                        dt = Datatype.Long;
                    } else if (paramValue instanceof Float || paramValue instanceof Double) {
                        dt = Datatype.Double;
                    } else if (paramValue instanceof Boolean) {
                        dt = Datatype.Boolean;
                    } else if (paramValue instanceof java.util.Date) {
                        dt = Datatype.Date;
                    } else {
                        dt = Datatype.String; // Fallback
                    }
                }

                VarTypeMapper.MapResult mr = VarTypeMapper.map(dt, paramValue);
                if (mr.skip()) continue;

                offer(new VarLogRow(executionId, project, packageId, flowId,
                        PARAM_CATEGORY, paramName, dt.name(), mr.valNum(), mr.valStr(),
                        "output", execMs, now, grayscaleBucket));
            }
        }
    }

    /**
     * 失败路径：只记录一行摘要。
     */
    public void produceFailure(
            String executionId, String project, String packageId, String flowId,
            long execMs,
            String grayscaleBucket
    ) {
        offer(new VarLogRow(executionId, project, packageId, flowId,
                "", "", "", null, null, "", execMs, System.currentTimeMillis(), grayscaleBucket));
    }

    /**
     * 从 KnowledgePackage 的 ResourceLibrary 构建类型缓存。
     */
    static Map<String, Map<String, Datatype>> buildTypeCache(KnowledgePackage knowledgePackage) {
        Map<String, Map<String, Datatype>> cache = new HashMap<>();
        ResourceLibrary lib = knowledgePackage.getRete().getResourceLibrary();
        List<VariableCategory> categories = lib.getVariableCategories();
        if (categories == null) return cache;

        for (VariableCategory vc : categories) {
            List<Variable> vars = vc.getVariables();
            if (vars == null) continue;
            Map<String, Datatype> varMap = new HashMap<>();
            for (Variable v : vars) {
                varMap.put(v.getName(), v.getType());
            }
            cache.put(vc.getName(), varMap);
        }
        return cache;
    }

    private void offer(VarLogRow row) {
        if (!queue.offer(row)) {
            log.warn("监控队列已满，丢弃变量行: executionId={}, var={}.{}",
                    row.executionId(), row.varCategory(), row.varName());
        }
    }
}
