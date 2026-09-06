package com.caritasem.ruleuler.function;

import com.bstek.urule.runtime.KnowledgePackage;
import com.bstek.urule.runtime.service.RemoteService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.net.InetAddress;
import java.nio.file.Path;
import java.util.List;
import java.util.Objects;

/**
 * 覆盖 urule.remoteService。返回包之前做 deps / 落盘 / 版本比对。
 * 不齐 return null，不改 core。不拦 KnowledgeCache。
 */
public class FunctionAwareRemoteService implements RemoteService {

    private static final Logger log = LoggerFactory.getLogger(FunctionAwareRemoteService.class);

    private final RemoteService delegate;
    private final String serverUrl;
    private final String clientHost;

    public FunctionAwareRemoteService(RemoteService delegate, String serverUrl) {
        this.delegate = delegate;
        this.serverUrl = FunctionJarBootstrap.trimSlash(serverUrl);
        this.clientHost = resolveHost();
    }

    @Override
    public KnowledgePackage getKnowledge(String packageId, String timestamp) {
        if (!depsReady(packageId)) {
            return null;
        }
        return delegate.getKnowledge(packageId, timestamp);
    }

    private boolean depsReady(String packageId) {
        try {
            List<FunctionJarHttp.JarDep> deps = FunctionJarHttp.listForPackage(serverUrl, packageId);
            if (deps.isEmpty()) {
                return true;
            }
            Path libDir = FunctionJarStore.libDir();
            boolean ok = true;
            for (FunctionJarHttp.JarDep dep : deps) {
                FunctionJarHttp.ensureOnDisk(serverUrl, libDir, dep);
                String actual = FunctionJarStore.loadedVersion(dep.functionPackage());
                if (!Objects.equals(dep.version(), actual)) {
                    log.warn("函数版本不齐 {} expected={} actual={}，拒载 {}",
                            dep.functionPackage(), dep.version(), actual, packageId);
                    FunctionJarHttp.report(serverUrl, clientHost, packageId,
                            dep.functionPackage(), dep.version(), actual, "mismatch");
                    ok = false;
                } else {
                    FunctionJarHttp.report(serverUrl, clientHost, packageId,
                            dep.functionPackage(), dep.version(), actual, "ok");
                }
            }
            return ok;
        } catch (Exception e) {
            log.error("函数 deps 校验失败: {}", e.getMessage());
            throw new IllegalStateException("函数 deps 校验失败: " + e.getMessage(), e);
        }
    }

    private static String resolveHost() {
        try {
            return InetAddress.getLocalHost().getHostName();
        } catch (Exception e) {
            return "unknown";
        }
    }
}
