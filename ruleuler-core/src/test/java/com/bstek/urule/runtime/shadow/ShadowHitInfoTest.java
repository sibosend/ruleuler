package com.bstek.urule.runtime.shadow;

import org.junit.jupiter.api.*;
import java.util.Map;
import static org.junit.jupiter.api.Assertions.*;

/**
 * Feature: shadow-mode-backtesting
 * ShadowHitInfo 基本验证。
 */
class ShadowHitInfoTest {

    @Test
    void getter正常() {
        Map<String, Object> input = Map.of("a", 1);
        Map<String, Object> output = Map.of("b", 2);
        ShadowHitInfo info = new ShadowHitInfo("rule1", input, output, 42, null);

        assertEquals("rule1", info.getRuleName());
        assertEquals(input, info.getInputSnapshot());
        assertEquals(output, info.getOutputSnapshot());
        assertEquals(42, info.getExecMs());
        assertNull(info.getErrorMsg());
    }

    @Test
    void errorMsg非null() {
        ShadowHitInfo info = new ShadowHitInfo("rule2", Map.of(), Map.of(), 0, "NPE");
        assertEquals("NPE", info.getErrorMsg());
    }
}
