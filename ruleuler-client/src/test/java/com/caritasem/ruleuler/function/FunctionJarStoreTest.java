package com.caritasem.ruleuler.function;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class FunctionJarStoreTest {

    @TempDir
    Path dir;

    @Test
    void writeReplaceDeletesOld() throws Exception {
        Path file = FunctionJarStore.file(dir, "geo-functions");
        Files.writeString(file, "old");
        FunctionJarStore.writeReplace(dir, "geo-functions", "new".getBytes(StandardCharsets.UTF_8));
        assertEquals("new", Files.readString(file));
        assertFalse(Files.exists(dir.resolve("geo-functions-1.0.0.jar")));
    }

    @Test
    void ensureOnDiskSkipsReadWhenConfirmed() throws Exception {
        Path jar = FunctionJarStore.file(dir, "geo");
        Files.writeString(jar, "bytes");
        FunctionJarStore.confirmChecksum("geo", "deadbeef");
        FunctionJarHttp.JarDep dep = new FunctionJarHttp.JarDep("geo", "1.0", "deadbeef");
        assertFalse(FunctionJarHttp.ensureOnDisk("http://unused", dir, "proj", dep));
    }

    @Test
    void writeReplaceInvalidatesConfirmedChecksum() throws Exception {
        FunctionJarStore.confirmChecksum("geo-functions", "abc");
        assertTrue(FunctionJarStore.checksumConfirmed("geo-functions", "abc"));
        FunctionJarStore.writeReplace(dir, "geo-functions", "new".getBytes(StandardCharsets.UTF_8));
        assertFalse(FunctionJarStore.checksumConfirmed("geo-functions", "abc"));
        FunctionJarStore.confirmChecksum("geo-functions", "def");
        assertTrue(FunctionJarStore.checksumConfirmed("geo-functions", "def"));
    }
}
