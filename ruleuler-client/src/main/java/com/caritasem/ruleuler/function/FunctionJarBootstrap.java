package com.caritasem.ruleuler.function;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.lang.management.ManagementFactory;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;

/**
 * 启动早期按项目拉 deps 落盘。PropertiesLauncher 已展开 classpath 时，
 * 落盘有变化则同 JVM 参数再拉起一次（用户看到一次 java -jar）。
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
        String projects = System.getProperty("ruleuler.projects");
        if (projects == null || projects.isBlank()) {
            projects = System.getenv("RULEULER_PROJECTS");
        }
        if (projects == null || projects.isBlank()) {
            log.info("未配置 ruleuler.projects / RULEULER_PROJECTS，跳过启动预拉 jar");
            return false;
        }
        Path libDir = FunctionJarStore.libDir();
        try {
            boolean changed = false;
            for (String project : projects.split(",")) {
                String p = project.trim();
                if (p.isEmpty()) {
                    continue;
                }
                changed |= FunctionJarHttp.syncProject(trimSlash(server), libDir, p);
            }
            log.info("函数 jar 启动同步完成, lib={}, changed={}", libDir, changed);
            return changed;
        } catch (Exception e) {
            throw new IllegalStateException("启动同步函数 jar 失败: " + e.getMessage(), e);
        }
    }

    static List<String> composeCommand(String javaBin, List<String> vmArgs, String sunJavaCommand) {
        if (sunJavaCommand == null || sunJavaCommand.isBlank()) {
            throw new IllegalStateException("无法重拉：无 sun.java.command");
        }
        List<String> cmd = new ArrayList<>();
        cmd.add(javaBin);
        cmd.addAll(vmArgs);
        String[] parts = sunJavaCommand.trim().split("\\s+");
        if (parts[0].endsWith(".jar")) {
            cmd.add("-jar");
        }
        cmd.addAll(List.of(parts));
        return cmd;
    }

    private static void relaunch() {
        String java = ProcessHandle.current().info().command()
                .orElseGet(() -> System.getProperty("java.home") + "/bin/java");
        List<String> cmd = composeCommand(
                java,
                ManagementFactory.getRuntimeMXBean().getInputArguments(),
                System.getProperty("sun.java.command"));
        log.info("函数 jar 已更新，重拉 client 以加载 classpath: {}", cmd);
        try {
            ProcessBuilder pb = new ProcessBuilder(cmd);
            pb.environment().put(SYNCED, "1");
            pb.inheritIO();
            Process child = pb.start();
            Runtime.getRuntime().addShutdownHook(new Thread(child::destroyForcibly));
            System.exit(child.waitFor());
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
