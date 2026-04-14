package com.caritasem.ruleuler.monitoring;

/**
 * 执行追踪行，对应 ClickHouse execution_trace_log 表。
 */
public record TraceLogRow(
        String executionId,
        int seq,
        String msgType,
        String msgText,
        String parsedName,
        String passFail,
        String project,
        String packageId,
        String flowId,
        long createdAt
) {}
