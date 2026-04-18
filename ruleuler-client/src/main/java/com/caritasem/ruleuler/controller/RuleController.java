package com.caritasem.ruleuler.controller;

import com.alibaba.fastjson2.JSONObject;
import com.caritasem.ruleuler.dto.RespDTO;
import com.caritasem.ruleuler.dto.RuleExecutionResult;
import com.caritasem.ruleuler.service.RuleExecutionService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.web.server.context.WebServerApplicationContext;
import org.springframework.web.bind.annotation.*;

import java.net.InetAddress;
import java.util.HashMap;
import java.util.Map;

@RestController
public class RuleController {

    @Autowired
    private WebServerApplicationContext webServerAppContext;

    @Autowired
    private RuleExecutionService ruleExecutionService;

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
            @PathVariable String knowledge) {
        RuleExecutionResult r = ruleExecutionService.execute(project, knowledge, process, body);
        RespDTO resp = new RespDTO(r.status(), r.msg(), r.data());
        resp.setMeta(r.meta());
        return resp;
    }
}
