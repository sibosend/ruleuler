package com.caritasem.ruleuler.base;

import com.bstek.urule.model.GeneralEntity;
import com.bstek.urule.runtime.KnowledgeSession;

import java.util.LinkedHashMap;
import java.util.Map;

/**
 * 统一输出采集：只返回被规则修改过的字段。
 * params 全量输出；entity 变量只输出与输入不同的字段。
 */
public final class RuleOutputCollector {

    private RuleOutputCollector() {}

    public static Map<String, Object> collectOutput(KnowledgeSession session,
                                                     Map<String, GeneralEntity> entities,
                                                     Map<String, ? extends Map<String, ?>> inputByCategory) {
        Map<String, Object> result = new LinkedHashMap<>();
        Map<String, Object> params = session.getParameters();
        if (params != null) {
            result.putAll(params);
        }
        for (Map.Entry<String, GeneralEntity> e : entities.entrySet()) {
            String category = e.getKey();
            GeneralEntity entity = e.getValue();
            Map<String, ?> input = inputByCategory != null ? inputByCategory.get(category) : null;
            Map<String, Object> varOutput = new LinkedHashMap<>();
            for (Object key : entity.keySet()) {
                String k = String.valueOf(key);
                Object newVal = entity.get(key);
                Object oldVal = input != null ? input.get(k) : null;
                if (oldVal != null && oldVal.equals(newVal)) {
                    continue;
                }
                varOutput.put(k, newVal);
            }
            if (!varOutput.isEmpty()) {
                result.put(category, varOutput);
            }
        }
        return result;
    }
}
