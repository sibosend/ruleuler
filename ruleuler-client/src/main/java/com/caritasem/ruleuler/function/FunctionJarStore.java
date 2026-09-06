package com.caritasem.ruleuler.function;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.MessageDigest;
import java.util.HexFormat;

public final class FunctionJarStore {

    private FunctionJarStore() {}

    public static Path file(Path dir, String functionPackage) {
        return dir.resolve(functionPackage + ".jar");
    }

    public static void writeReplace(Path dir, String functionPackage, byte[] bytes) throws IOException {
        Files.createDirectories(dir);
        Path target = file(dir, functionPackage);
        Files.deleteIfExists(target);
        Files.write(target, bytes);
    }

    public static String checksum(Path file) throws IOException {
        return sha256(Files.readAllBytes(file));
    }

    public static String sha256(byte[] bytes) {
        try {
            return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(bytes));
        } catch (Exception e) {
            throw new IllegalStateException(e);
        }
    }

    public static Path libDir() {
        String loaderPath = System.getProperty("loader.path");
        if (loaderPath == null || loaderPath.isBlank()) {
            throw new IllegalStateException("未配置 loader.path");
        }
        return Path.of(loaderPath.split("[,;]")[0].trim());
    }

    public static String loadedVersion(String functionPackage) {
        var cl = Thread.currentThread().getContextClassLoader();
        if (cl == null) {
            cl = FunctionJarStore.class.getClassLoader();
        }
        var url = cl.getResource("META-INF/ruleuler/" + functionPackage + ".version");
        if (url == null) {
            return null;
        }
        try (var in = url.openStream()) {
            return new String(in.readAllBytes()).trim();
        } catch (IOException e) {
            return null;
        }
    }
}
