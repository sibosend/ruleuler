package com.caritasem.ruleuler.server.function;

import jakarta.servlet.http.HttpServletResponse;
import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.io.IOException;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/function")
public class FunctionSyncController {

    private final FunctionDepsService depsService;
    private final FunctionJarDao jarDao;
    private final FunctionStatusDao statusDao;

    public FunctionSyncController(FunctionDepsService depsService,
                                  FunctionJarDao jarDao,
                                  FunctionStatusDao statusDao) {
        this.depsService = depsService;
        this.jarDao = jarDao;
        this.statusDao = statusDao;
    }

    @GetMapping("/deps")
    public Map<String, Object> deps(@RequestParam(required = false) String packageId) {
        if (packageId == null || packageId.isBlank()) {
            return Map.of("jars", rename(depsService.allCurrentJars()));
        }
        return Map.of("packageId", packageId, "deps", rename(depsService.depsForPackage(packageId)));
    }

    @GetMapping("/jars")
    public void download(@RequestParam String functionPackage,
                         @RequestParam String version,
                         @RequestParam String checksum,
                         HttpServletResponse response) throws IOException {
        byte[] blob = jarDao.findBlob(functionPackage, version, checksum)
                .orElseThrow(() -> new IllegalArgumentException("函数包不存在"));
        response.setContentType("application/java-archive");
        response.setHeader("Content-Disposition", "attachment; filename=\"" + functionPackage + ".jar\"");
        response.setContentLength(blob.length);
        response.getOutputStream().write(blob);
    }

    @PostMapping(value = "/status", consumes = MediaType.APPLICATION_JSON_VALUE)
    public Map<String, String> report(@RequestBody Map<String, Object> body) {
        String clientHost = required(body, "clientHost");
        String packageId = required(body, "packageId");
        String functionPackage = required(body, "functionPackage");
        String expected = body.get("expectedVersion") == null ? null : String.valueOf(body.get("expectedVersion"));
        String actual = body.get("actualVersion") == null ? null : String.valueOf(body.get("actualVersion"));
        String status = required(body, "status");
        statusDao.upsert(clientHost, packageId, functionPackage, expected, actual, status);
        return Map.of("ok", "true");
    }

    private static List<Map<String, Object>> rename(List<Map<String, Object>> rows) {
        return rows.stream().map(row -> {
            Map<String, Object> m = new LinkedHashMap<>();
            m.put("functionPackage", row.get("function_package"));
            m.put("version", row.get("version"));
            m.put("checksum", row.get("checksum"));
            return m;
        }).toList();
    }

    private static String required(Map<String, Object> body, String key) {
        Object v = body.get(key);
        if (v == null || String.valueOf(v).isBlank()) {
            throw new IllegalArgumentException(key + " 必填");
        }
        return String.valueOf(v);
    }
}
