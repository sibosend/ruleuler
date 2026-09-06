package com.caritasem.ruleuler.function;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.net.InetAddress;
import java.nio.file.Path;
import java.util.List;
import java.util.Objects;
import java.util.concurrent.ConcurrentHashMap;

/**
 * 函数 deps 校验 + 落盘 + 节流上报。失败/不齐返回 false，不抛。
 * pull（remoteService）和 push（/api/grayscale/package）共用。
 */
public class FunctionDepsGate {

    private static final Logger log = LoggerFactory.getLogger(FunctionDepsGate.class);

    private final String serverUrl;
    private final String clientHost;
    private final long cacheMs;
    private final ConcurrentHashMap<String, Cached> depsCache = new ConcurrentHashMap<>();
    private final ConcurrentHashMap<String, String> lastStatus = new ConcurrentHashMap<>();

    record Cached(List<FunctionJarHttp.JarDep> deps, long at) {}

    public FunctionDepsGate(String serverUrl, long cacheMs) {
        this.serverUrl = FunctionJarBootstrap.trimSlash(serverUrl);
        this.cacheMs = cacheMs;
        this.clientHost = resolveHost();
    }

    public boolean ready(String packageId) {
        try {
            List<FunctionJarHttp.JarDep> deps = cachedDeps(packageId);
            if (deps.isEmpty()) {
                return true;
            }
            Path libDir = FunctionJarStore.libDir();
            String project = projectOf(packageId);
            boolean ok = true;
            for (FunctionJarHttp.JarDep dep : deps) {
                try {
                    FunctionJarHttp.ensureOnDisk(serverUrl, libDir, project, dep);
                } catch (Exception e) {
                    log.error("函数 jar 落盘失败 {}: {}", dep.functionPackage(), e.getMessage());
                    reportIfChanged(packageId, dep.functionPackage(), dep.version(), null, "error");
                    ok = false;
                    continue;
                }
                String actual = FunctionJarStore.loadedVersion(dep.functionPackage());
                String status = statusOf(dep.version(), actual);
                if ("mismatch".equals(status)) {
                    log.warn("函数版本不齐 {} expected={} actual={}，拒载 {}",
                            dep.functionPackage(), dep.version(), actual, packageId);
                    ok = false;
                }
                reportIfChanged(packageId, dep.functionPackage(), dep.version(), actual, status);
            }
            return ok;
        } catch (Exception e) {
            log.error("函数 deps 校验失败: {}", e.getMessage());
            return false;
        }
    }

    static String statusOf(String expected, String actual) {
        return Objects.equals(expected, actual) ? "ok" : "mismatch";
    }

    static String projectOf(String packageId) {
        String id = packageId.startsWith("/") ? packageId.substring(1) : packageId;
        int slash = id.indexOf('/');
        if (slash < 0) {
            throw new IllegalArgumentException("packageId 格式必须是 project/package");
        }
        return id.substring(0, slash);
    }

    boolean noteAndShouldReport(String packageId, String functionPackage, String status) {
        String key = packageId + "|" + functionPackage;
        String prev = lastStatus.put(key, status);
        return !status.equals(prev);
    }

    private List<FunctionJarHttp.JarDep> cachedDeps(String packageId) throws Exception {
        long now = System.currentTimeMillis();
        Cached hit = depsCache.get(packageId);
        if (hit != null && now - hit.at() < cacheMs) {
            return hit.deps();
        }
        List<FunctionJarHttp.JarDep> deps = FunctionJarHttp.listForPackage(serverUrl, packageId);
        depsCache.put(packageId, new Cached(deps, now));
        return deps;
    }

    private void reportIfChanged(String packageId, String functionPackage,
                                 String expected, String actual, String status) {
        if (!noteAndShouldReport(packageId, functionPackage, status)) {
            return;
        }
        FunctionJarHttp.report(serverUrl, clientHost, packageId, functionPackage, expected, actual, status);
    }

    private static String resolveHost() {
        try {
            return InetAddress.getLocalHost().getHostName();
        } catch (Exception e) {
            return "unknown";
        }
    }
}
