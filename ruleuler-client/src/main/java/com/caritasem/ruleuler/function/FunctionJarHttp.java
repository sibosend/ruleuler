package com.caritasem.ruleuler.function;

import tools.jackson.core.type.TypeReference;
import tools.jackson.databind.ObjectMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.io.InputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

public final class FunctionJarHttp {

    private static final Logger log = LoggerFactory.getLogger(FunctionJarHttp.class);
    private static final ObjectMapper MAPPER = new ObjectMapper();

    private FunctionJarHttp() {}

    public record JarDep(String functionPackage, String version, String checksum) {}

    public static boolean syncProject(String serverUrl, Path libDir, String project) throws IOException {
        List<JarDep> jars = listForProject(serverUrl, project);
        boolean changed = false;
        for (JarDep dep : jars) {
            changed |= ensureOnDisk(serverUrl, libDir, project, dep);
        }
        return changed;
    }

    public static List<JarDep> listForProject(String serverUrl, String project) throws IOException {
        String url = serverUrl + "/api/function/deps?project="
                + URLEncoder.encode(project, StandardCharsets.UTF_8);
        String json = get(url);
        Map<String, Object> body = MAPPER.readValue(json, new TypeReference<Map<String, Object>>() {});
        return parseList(body.get("jars"));
    }

    public static List<JarDep> listForPackage(String serverUrl, String packageId) throws IOException {
        String url = serverUrl + "/api/function/deps?packageId="
                + URLEncoder.encode(packageId, StandardCharsets.UTF_8);
        String json = get(url);
        Map<String, Object> body = MAPPER.readValue(json, new TypeReference<Map<String, Object>>() {});
        return parseList(body.get("deps"));
    }

    public static boolean ensureOnDisk(String serverUrl, Path libDir, String project, JarDep dep) throws IOException {
        Path file = FunctionJarStore.file(libDir, dep.functionPackage());
        if (Files.exists(file) && FunctionJarStore.checksumConfirmed(dep.functionPackage(), dep.checksum())) {
            return false;
        }
        if (Files.exists(file) && dep.checksum().equals(FunctionJarStore.checksum(file))) {
            FunctionJarStore.confirmChecksum(dep.functionPackage(), dep.checksum());
            return false;
        }
        byte[] bytes = download(serverUrl, project, dep.functionPackage(), dep.version(), dep.checksum());
        String actual = FunctionJarStore.sha256(bytes);
        if (!dep.checksum().equals(actual)) {
            throw new IOException("下载校验和不符: " + dep.functionPackage());
        }
        FunctionJarStore.writeReplace(libDir, dep.functionPackage(), bytes);
        FunctionJarStore.confirmChecksum(dep.functionPackage(), dep.checksum());
        log.info("已落盘函数 jar: {} {}", dep.functionPackage(), dep.version());
        return true;
    }

    public static boolean report(String serverUrl, String clientHost, String packageId,
                                 String functionPackage, String expected, String actual, String status) {
        try {
            String json = MAPPER.writeValueAsString(Map.of(
                    "clientHost", clientHost,
                    "packageId", packageId,
                    "functionPackage", functionPackage,
                    "expectedVersion", expected == null ? "" : expected,
                    "actualVersion", actual == null ? "" : actual,
                    "status", status));
            post(serverUrl + "/api/function/status", json);
            return true;
        } catch (Exception e) {
            log.warn("上报函数版本失败: {}", e.getMessage());
            return false;
        }
    }

    @SuppressWarnings("unchecked")
    private static List<JarDep> parseList(Object raw) {
        List<JarDep> out = new ArrayList<>();
        if (!(raw instanceof List<?> list)) {
            return out;
        }
        for (Object o : list) {
            if (!(o instanceof Map<?, ?> m)) {
                continue;
            }
            Map<String, Object> row = (Map<String, Object>) m;
            out.add(new JarDep(
                    String.valueOf(row.get("functionPackage")),
                    String.valueOf(row.get("version")),
                    String.valueOf(row.get("checksum"))));
        }
        return out;
    }

    private static byte[] download(String serverUrl, String project, String functionPackage,
                                   String version, String checksum) throws IOException {
        String url = serverUrl + "/api/function/jars?project="
                + URLEncoder.encode(project, StandardCharsets.UTF_8)
                + "&functionPackage=" + URLEncoder.encode(functionPackage, StandardCharsets.UTF_8)
                + "&version=" + URLEncoder.encode(version, StandardCharsets.UTF_8)
                + "&checksum=" + URLEncoder.encode(checksum, StandardCharsets.UTF_8);
        HttpURLConnection conn = open(url, "GET");
        try {
            int code = conn.getResponseCode();
            if (code >= 300) {
                throw new IOException("下载 jar HTTP " + code);
            }
            try (InputStream in = conn.getInputStream()) {
                return in.readAllBytes();
            }
        } finally {
            conn.disconnect();
        }
    }

    private static String get(String url) throws IOException {
        HttpURLConnection conn = open(url, "GET");
        try {
            int code = conn.getResponseCode();
            if (code >= 300) {
                throw new IOException("GET " + url + " HTTP " + code);
            }
            try (InputStream in = conn.getInputStream()) {
                return new String(in.readAllBytes(), StandardCharsets.UTF_8);
            }
        } finally {
            conn.disconnect();
        }
    }

    private static void post(String url, String json) throws IOException {
        HttpURLConnection conn = open(url, "POST");
        try {
            conn.setDoOutput(true);
            conn.setRequestProperty("Content-Type", "application/json");
            conn.getOutputStream().write(json.getBytes(StandardCharsets.UTF_8));
            conn.getResponseCode();
        } finally {
            conn.disconnect();
        }
    }

    private static HttpURLConnection open(String url, String method) throws IOException {
        HttpURLConnection conn = (HttpURLConnection) new URL(url).openConnection();
        conn.setRequestMethod(method);
        conn.setConnectTimeout(5000);
        conn.setReadTimeout(30000);
        return conn;
    }
}
