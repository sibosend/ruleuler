package com.caritasem.ruleuler.function;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;

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
}
