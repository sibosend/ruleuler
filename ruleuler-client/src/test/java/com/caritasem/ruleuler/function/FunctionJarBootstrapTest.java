package com.caritasem.ruleuler.function;

import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class FunctionJarBootstrapTest {

    @Test
    void composeJarCommand() {
        List<String> cmd = FunctionJarBootstrap.composeCommand(
                "/usr/bin/java",
                List.of("-Dloader.path=lib/", "-Dserver.port=16001"),
                "dist/client/app.jar");
        assertEquals("/usr/bin/java", cmd.get(0));
        assertTrue(cmd.contains("-jar"));
        assertTrue(cmd.contains("dist/client/app.jar"));
    }

    @Test
    void rejectEmptySunCommand() {
        assertThrows(IllegalStateException.class, () ->
                FunctionJarBootstrap.composeCommand("java", List.of(), "  "));
    }
}
