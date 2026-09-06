package com.caritasem.ruleuler.server.function;

import org.junit.jupiter.api.Test;

import java.io.ByteArrayOutputStream;
import java.nio.charset.StandardCharsets;
import java.util.zip.ZipEntry;
import java.util.zip.ZipOutputStream;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class FunctionJarExtractorTest {

    @Test
    void extractAlAndVersion() throws Exception {
        byte[] jar = zipTwo(
                FunctionJarExtractor.AL_ENTRY, "<action-library/>",
                "META-INF/ruleuler/geo-functions.version", "1.2.0");
        FunctionJarExtractor.Extracted extracted = FunctionJarExtractor.extract(jar, 20, 1024);
        assertTrue(extracted.alXml().contains("action-library"));
        assertEquals("1.2.0", extracted.versions().get("geo-functions"));
    }

    @Test
    void rejectZipSlip() throws Exception {
        byte[] jar = zip("../META-INF/ruleuler/functions.al.xml", "<action-library/>");
        assertThrows(IllegalArgumentException.class, () ->
                FunctionJarExtractor.extract(jar, 20, 1024));
    }

    @Test
    void rejectMissingAl() throws Exception {
        byte[] jar = zip("foo.txt", "x");
        assertThrows(IllegalArgumentException.class, () ->
                FunctionJarExtractor.extract(jar, 20, 1024));
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
                FunctionJarExtractor.extract(bos.toByteArray(), 3, 1024));
    }

    private static byte[] zip(String name, String content) throws Exception {
        return zipTwo(name, content, null, null);
    }

    private static byte[] zipTwo(String n1, String c1, String n2, String c2) throws Exception {
        ByteArrayOutputStream bos = new ByteArrayOutputStream();
        try (ZipOutputStream zos = new ZipOutputStream(bos)) {
            zos.putNextEntry(new ZipEntry(n1));
            zos.write(c1.getBytes(StandardCharsets.UTF_8));
            zos.closeEntry();
            if (n2 != null) {
                zos.putNextEntry(new ZipEntry(n2));
                zos.write(c2.getBytes(StandardCharsets.UTF_8));
                zos.closeEntry();
            }
        }
        return bos.toByteArray();
    }
}
