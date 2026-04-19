package com.caritasem.ruleuler.replay;

import com.caritasem.ruleuler.dto.RuleExecutionResult;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.*;

import java.util.LinkedHashMap;
import java.util.Map;

@RestController
@RequestMapping("/api/replay")
public class ReplayController {

    @Autowired
    private ReplayExecutor replayExecutor;

    @PostMapping("/execute")
    public Map<String, Object> execute(@RequestBody Map<String, Object> request) {
        String project = (String) request.get("project");
        String packageId = (String) request.get("packageId");
        String flowId = (String) request.get("flowId");

        @SuppressWarnings("unchecked")
        Map<String, Map<String, Object>> input = (Map<String, Map<String, Object>>) request.get("input");

        long start = System.currentTimeMillis();
        RuleExecutionResult result = replayExecutor.replay(project, packageId, flowId, input);
        long execMs = System.currentTimeMillis() - start;

        Map<String, Object> response = new LinkedHashMap<>();
        response.put("status", result.status());
        response.put("data", result.data());
        response.put("execMs", execMs);
        if (result.status() != 200) {
            response.put("error", result.msg());
        }
        return response;
    }
}
