package com.caritasem.ruleuler.replay;

import com.alibaba.fastjson2.JSONObject;
import com.caritasem.ruleuler.dto.RuleExecutionResult;
import com.caritasem.ruleuler.grayscale.GrayscaleContext;
import com.caritasem.ruleuler.service.RuleExecutionService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.util.LinkedHashMap;
import java.util.Map;

@Service
public class ReplayExecutor {

    @Autowired
    private RuleExecutionService ruleExecutionService;

    public RuleExecutionResult replay(String project, String packageId, String flowId,
                                      Map<String, Map<String, Object>> input) {
        Map<String, JSONObject> body = new LinkedHashMap<>();
        for (Map.Entry<String, Map<String, Object>> entry : input.entrySet()) {
            JSONObject json = new JSONObject();
            json.putAll(entry.getValue());
            body.put(entry.getKey(), json);
        }
        // 回放不走灰度路由，确保用 base 包执行
        GrayscaleContext.skipGrayscale();
        return ruleExecutionService.execute(project, packageId, flowId, body);
    }
}
