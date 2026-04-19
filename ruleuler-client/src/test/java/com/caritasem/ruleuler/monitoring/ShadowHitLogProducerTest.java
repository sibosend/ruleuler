package com.caritasem.ruleuler.monitoring;

import com.bstek.urule.runtime.shadow.ShadowHitInfo;
import net.jqwik.api.*;
import org.junit.jupiter.api.*;

import java.util.List;
import java.util.Map;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.LinkedBlockingQueue;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Feature: shadow-mode-backtesting
 *
 * ShadowHitLogProducer 单元测试 + 属性测试。
 */
class ShadowHitLogProducerTest {

    @Test
    void 正常投递() {
        BlockingQueue<ShadowHitLogRow> queue = new LinkedBlockingQueue<>(100);
        ShadowHitLogProducer producer = new ShadowHitLogProducer(queue);

        List<ShadowHitInfo> hits = List.of(
                new ShadowHitInfo("rule1", Map.of("a", 1), Map.of("b", 2), 10, null)
        );
        producer.produce("exec-1", "proj", "proj/pkg", "flow", hits);

        assertEquals(1, queue.size());
        ShadowHitLogRow row = queue.poll();
        assertEquals("exec-1", row.executionId());
        assertEquals("rule1", row.ruleName());
        assertEquals(10, row.execMs());
        assertNull(row.errorMsg());
    }

    @Test
    void 队列满丢弃() {
        BlockingQueue<ShadowHitLogRow> queue = new LinkedBlockingQueue<>(1);
        queue.offer(new ShadowHitLogRow("x", "x", "x", "x", "x", "{}", "{}", 0, null, 0));
        ShadowHitLogProducer producer = new ShadowHitLogProducer(queue);

        List<ShadowHitInfo> hits = List.of(
                new ShadowHitInfo("rule1", Map.of(), Map.of(), 0, null)
        );
        producer.produce("exec-2", "proj", "proj/pkg", "flow", hits);

        // 队列仍然是满的 1 条
        assertEquals(1, queue.size());
    }

    /**
     * Feature: shadow-mode-backtesting, Property 7: Shadow 日志记录完整且 diff 正确
     * Validates: Requirements 3.1, 3.3
     */
    @Property(tries = 100)
    void 日志字段完整(@ForAll String ruleName, @ForAll long execMs,
                     @ForAll("nullableStrings") String errorMsg) {
        BlockingQueue<ShadowHitLogRow> queue = new LinkedBlockingQueue<>(1000);
        ShadowHitLogProducer producer = new ShadowHitLogProducer(queue);

        Map<String, Object> input = Map.of("FlightInfo", Map.of("score", 5));
        Map<String, Object> output = Map.of("FlightInfo", Map.of("score", 10));
        List<ShadowHitInfo> hits = List.of(
                new ShadowHitInfo(ruleName, input, output, execMs, errorMsg)
        );

        producer.produce("exec-id", "proj", "proj/pkg", "flow", hits);

        ShadowHitLogRow row = queue.poll();
        assertNotNull(row);
        assertEquals("exec-id", row.executionId());
        assertEquals("proj", row.project());
        assertEquals("proj/pkg", row.packageId());
        assertEquals("flow", row.flowId());
        assertEquals(ruleName, row.ruleName());
        assertEquals(execMs, row.execMs());
        assertEquals(errorMsg, row.errorMsg());
        assertFalse(row.inputSnapshot().isEmpty());
        assertFalse(row.outputSnapshot().isEmpty());
    }

    @Provide
    Arbitrary<String> nullableStrings() {
        return Arbitraries.strings().ofMaxLength(20).injectNull(0.3);
    }
}
