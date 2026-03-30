package com.caritasem.ruleuler.server.controller;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.web.context.WebServerApplicationContext;
import org.springframework.web.bind.annotation.*;

import java.net.InetAddress;
import java.util.HashMap;

/**
 * Desc
 *
 * @author zengxzh@yonyou.com
 * @version V1.0.0
 * @date 2018/1/4
 */
@RestController
public class RuleController {

    @Autowired
    private WebServerApplicationContext webServerAppContext;

    @GetMapping("health")
    public HashMap<String, Object> health() {
        HashMap<String, Object> result = new HashMap<>();
        result.put("code", 200);
        result.put("status", "UP");
        result.put("service", "urule.server");
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
}
