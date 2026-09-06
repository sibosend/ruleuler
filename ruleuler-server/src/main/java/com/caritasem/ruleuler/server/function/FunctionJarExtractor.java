package com.caritasem.ruleuler.server.function;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.zip.ZipEntry;
import java.util.zip.ZipInputStream;

public final class FunctionJarExtractor {

    public static final String AL_ENTRY = "META-INF/ruleuler/functions.al.xml";

    private FunctionJarExtractor() {}

    public static String extractAlXml(byte[] jarBytes, int maxEntries, int maxXmlBytes) {
        if (jarBytes == null || jarBytes.length == 0) {
            throw new IllegalArgumentException("jar 为空");
        }
        try (ZipInputStream zis = new ZipInputStream(new ByteArrayInputStream(jarBytes))) {
            ZipEntry entry;
            int count = 0;
            String xml = null;
            while ((entry = zis.getNextEntry()) != null) {
                count++;
                if (count > maxEntries) {
                    throw new IllegalArgumentException("jar 条目数超过上限");
                }
                String name = entry.getName();
                if (name.contains("..") || name.startsWith("/") || name.startsWith("\\")) {
                    throw new IllegalArgumentException("非法 zip 路径: " + name);
                }
                if (AL_ENTRY.equals(name)) {
                    xml = readLimited(zis, maxXmlBytes);
                }
            }
            if (xml == null) {
                throw new IllegalArgumentException("jar 缺少 " + AL_ENTRY);
            }
            return xml;
        } catch (IOException e) {
            throw new IllegalArgumentException("jar 无法读取: " + e.getMessage());
        }
    }

    private static String readLimited(ZipInputStream zis, int maxXmlBytes) throws IOException {
        ByteArrayOutputStream bos = new ByteArrayOutputStream();
        byte[] buf = new byte[4096];
        int n;
        int total = 0;
        while ((n = zis.read(buf)) >= 0) {
            total += n;
            if (total > maxXmlBytes) {
                throw new IllegalArgumentException("functions.al.xml 超过大小上限");
            }
            bos.write(buf, 0, n);
        }
        return bos.toString(StandardCharsets.UTF_8);
    }
}
