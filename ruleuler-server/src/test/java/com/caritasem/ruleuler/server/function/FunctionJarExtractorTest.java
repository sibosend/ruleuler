package com.caritasem.ruleuler.server.function;

import org.junit.jupiter.api.Test;

import java.io.ByteArrayOutputStream;
import java.nio.charset.StandardCharsets;
import java.util.zip.ZipEntry;
import java.util.zip.ZipOutputStream;

import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class FunctionJarExtractorTest {

    @Test
    void extractAlXml() throws Exception {
        byte[] jar = zip(FunctionJarExtractor.AL_ENTRY, "<action-library/>");
        String xml = FunctionJarExtractor.extractAlXml(jar, 20, 1024);
        assertTrue(xml.contains("action-library"));
    }

    @Test
    void rejectZipSlip() throws Exception {
        byte[] jar = zip("../META-INF/ruleuler/functions.al.xml", "<action-library/>");
        assertThrows(IllegalArgumentException.class, () ->
                FunctionJarExtractor.extractAlXml(jar, 20, 1024));
    }

    @Test
    void rejectMissingAl() throws Exception {
        byte[] jar = zip("foo.txt", "x");
        assertThrows(IllegalArgumentException.class, () ->
                FunctionJarExtractor.extractAlXml(jar, 20, 1024));
    }

    @Test
    void rejectTooManyEntries() throws Exception {
        ByteArrayOutputStream bos = new ByteArrayOutputStream();
        try (ZipOutputStream zos = new ZipOutputStream(bos)) {
            for (int i = 0; i < 5; i++) {
                zos.putNextEntry(new ZipEntry("e" + i));
                zos.write('x');
                zos.closeEntry();
            }
        }
        assertThrows(IllegalArgumentException.class, () ->
                FunctionJarExtractor.extractAlXml(bos.toByteArray(), 3, 1024));
    }

    private static byte[] zip(String name, String content) throws Exception {
        ByteArrayOutputStream bos = new ByteArrayOutputStream();
        try (ZipOutputStream zos = new ZipOutputStream(bos)) {
            zos.putNextEntry(new ZipEntry(name));
            zos.write(content.getBytes(StandardCharsets.UTF_8));
            zos.closeEntry();
        }
        return bos.toByteArray();
    }
}
