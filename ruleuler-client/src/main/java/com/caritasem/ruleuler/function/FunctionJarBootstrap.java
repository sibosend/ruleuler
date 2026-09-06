package com.caritasem.ruleuler.function;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.lang.management.ManagementFactory;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

/**
 * 启动早期拉 deps 落盘。PropertiesLauncher 已展开 classpath 时，
 * 若本次写了新 jar，同命令行再拉起一次，让 loader.path 吃到。
 */
public final class FunctionJarBootstrap {

    private static final Logger log = LoggerFactory.getLogger(FunctionJarBootstrap.class);
    private static final String SYNCED = "RULEULER_FUNCTION_SYNCED";

    private FunctionJarBootstrap() {}

    public static void main(String[] args) throws Exception {
        syncFromSystemProperties();
    }

    public static void syncAndRelaunchIfNeeded() {
        if ("1".equals(System.getenv(SYNCED))) {
            return;
        }
        boolean changed = syncFromSystemProperties();
        if (!changed) {
            return;
        }
        relaunch();
    }

    public static boolean syncFromSystemProperties() {
        String server = System.getProperty("urule.resporityServerUrl");
        if (server == null || server.isBlank()) {
            server = System.getenv("SERVER_URL");
        }
        if (server == null || server.isBlank()) {
            throw new IllegalStateException("未配置 urule.resporityServerUrl / SERVER_URL");
        }
        Path libDir = FunctionJarStore.libDir();
        try {
            boolean changed = FunctionJarHttp.syncAll(trimSlash(server), libDir);
            log.info("函数 jar 启动同步完成, lib={}, changed={}", libDir, changed);
            return changed;
        } catch (Exception e) {
            throw new IllegalStateException("启动同步函数 jar 失败: " + e.getMessage(), e);
        }
    }

    private static void relaunch() {
        Optional<String> command = ProcessHandle.current().info().command();
        Optional<String[]> arguments = ProcessHandle.current().info().arguments();
        if (command.isEmpty() || arguments.isEmpty()) {
            List<String> vm = ManagementFactory.getRuntimeMXBean().getInputArguments();
            throw new IllegalStateException(
                    "已落下函数 jar，但无法重拉进程（无 commandLine）。vmArgs=" + vm);
        }
        List<String> cmd = new ArrayList<>();
        cmd.add(command.get());
        cmd.addAll(List.of(arguments.get()));
        log.info("函数 jar 已更新，重拉 client 以加载 classpath: {}", cmd);
        try {
            ProcessBuilder pb = new ProcessBuilder(cmd);
            pb.environment().put(SYNCED, "1");
            pb.inheritIO();
            System.exit(pb.start().waitFor());
        } catch (Exception e) {
            throw new IllegalStateException("重拉 client 失败: " + e.getMessage(), e);
        }
    }

    static String trimSlash(String url) {
        if (url.endsWith("/")) {
            return url.substring(0, url.length() - 1);
        }
        return url;
    }
}
