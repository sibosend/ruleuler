package com.caritasem.ruleuler.monitoring;

import net.jqwik.api.*;

import javax.sql.DataSource;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.LinkedBlockingQueue;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.*;

/**
 * VarLogFlusher 属性测试。
 * Feature: variable-monitoring, Property 5: 队列 flush 完整性
 */
class VarLogFlusherProperties {

    // ========== Property 5: 队列 flush 完整性 ==========

    /**
     * Property 5: 队列 flush 完整性
     * 随机 VarLogRow 列表投入队列，flush 后验证写入目标收到全部行（不丢不重）。
     * Validates: Requirements 1.6
     */
    @Property(tries = 100)
    @Label("Feature: variable-monitoring, Property 5: 队列 flush 完整性")
    void allQueuedRowsAreFlushed(@ForAll("varLogRows") List<VarLogRow> rows) throws Exception {
        // 1. mock JDBC 链路
        DataSource ds = mock(DataSource.class);
        Connection conn = mock(Connection.class);
        PreparedStatement ps = mock(PreparedStatement.class);
        when(ds.getConnection()).thenReturn(conn);
        when(conn.prepareStatement(anyString())).thenReturn(ps);

        // 2. 将行放入队列，再 drain 出来作为 batch
        LinkedBlockingQueue<VarLogRow> queue = new LinkedBlockingQueue<>(rows.size() + 1);
        queue.addAll(rows);

        List<VarLogRow> batch = new ArrayList<>();
        queue.drainTo(batch);

        // 3. 调用 flush（package-private 方法，同包可访问）
        VarLogFlusher flusher = new VarLogFlusher(queue, ds, 500, 1000);
        flusher.flush(batch);

        // 4. 验证：addBatch 调用次数 == 行数，executeBatch 调用恰好 1 次
        verify(ps, times(rows.size())).addBatch();
        verify(ps, times(1)).executeBatch();

        // 5. 验证队列已被完全消费
        assertThat(queue).isEmpty();
    }

    // ========== 生成器 ==========

    /** 生成 1~200 条随机 VarLogRow */
    @Provide
    Arbitrary<List<VarLogRow>> varLogRows() {
        return varLogRow().list().ofMinSize(1).ofMaxSize(200);
    }

    /** 单条 VarLogRow 生成器（VarLogRow 有 13 个字段，超过 combine 上限 8，分两步组合） */
    private Arbitrary<VarLogRow> varLogRow() {
        Arbitrary<String> s = Arbitraries.strings().alpha().ofMinLength(1).ofMaxLength(8);
        Arbitrary<String> ioType = Arbitraries.of("input", "output");
        Arbitrary<String> varType = Arbitraries.of(
                "Integer", "Double", "Float", "Long", "BigDecimal",
                "Boolean", "String", "Char", "Enum", "Date");
        Arbitrary<Double> valNum = Arbitraries.doubles().between(-1e6, 1e6).injectNull(0.3);
        Arbitrary<String> valStr = s.injectNull(0.3);
        Arbitrary<Long> execMs = Arbitraries.longs().between(0, 10000);
        Arbitrary<Long> createdAt = Arbitraries.longs().between(1_700_000_000_000L, 1_800_000_000_000L);
        Arbitrary<String> grayscaleBucket = Arbitraries.of("BASE", "GRAY");

        // 前 8 个字段
        return Combinators.combine(s, s, s, s, s, s, varType, valNum)
                .flatAs((executionId, project, packageId, flowId, varCategory, varName, vt, vn) ->
                        // 后 5 个字段
                        Combinators.combine(valStr, ioType, execMs, createdAt, grayscaleBucket)
                                .as((vs, io, ms, ca, gb) ->
                                        new VarLogRow(executionId, project, packageId, flowId,
                                                varCategory, varName, vt, vn, vs, io, ms, ca, gb)));
    }
}
