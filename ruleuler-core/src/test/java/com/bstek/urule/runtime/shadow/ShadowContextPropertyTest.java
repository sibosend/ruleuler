package com.bstek.urule.runtime.shadow;

import net.jqwik.api.*;
import org.junit.jupiter.api.*;

import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Feature: shadow-mode-backtesting
 *
 * ShadowContext ThreadLocal 隔离和 clear 行为测试。
 */
class ShadowContextPropertyTest {

    @AfterEach
    void cleanup() {
        ShadowContext.clear();
    }

    @Test
    void clear后为空() {
        ShadowContext.addHit(new ShadowHitInfo("r1", Map.of(), Map.of(), 0, null));
        ShadowContext.clear();
        assertTrue(ShadowContext.getHits().isEmpty());
    }

    @Test
    void 多次addHit累积() {
        ShadowContext.clear();
        ShadowContext.addHit(new ShadowHitInfo("r1", Map.of(), Map.of(), 1, null));
        ShadowContext.addHit(new ShadowHitInfo("r2", Map.of(), Map.of(), 2, null));
        List<ShadowHitInfo> hits = ShadowContext.getHits();
        assertEquals(2, hits.size());
        assertEquals("r1", hits.get(0).getRuleName());
        assertEquals("r2", hits.get(1).getRuleName());
    }

    /**
     * Feature: shadow-mode-backtesting
     * 验证 ThreadLocal 隔离：不同线程的 ShadowContext 互不干扰。
     */
    @Property(tries = 20)
    void threadIsolation(@ForAll String threadName) throws Exception {
        ShadowContext.clear();
        ShadowContext.addHit(new ShadowHitInfo("main", Map.of(), Map.of(), 0, null));

        Thread t = new Thread(() -> {
            assertTrue(ShadowContext.getHits().isEmpty());
            ShadowContext.addHit(new ShadowHitInfo("child", Map.of(), Map.of(), 0, null));
            assertEquals(1, ShadowContext.getHits().size());
        }, threadName);
        t.start();
        t.join(1000);

        assertEquals(1, ShadowContext.getHits().size());
        assertEquals("main", ShadowContext.getHits().get(0).getRuleName());
    }
}
