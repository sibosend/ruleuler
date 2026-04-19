package com.caritasem.ruleuler.monitoring;

/**
 * 影子命中日志行，对应 ClickHouse shadow_hit_log 表。
 */
public record ShadowHitLogRow(
    String executionId,
    String project,
    String packageId,
    String flowId,
    String ruleName,
    String inputSnapshot,
    String outputSnapshot,
    long execMs,
    String errorMsg,
    long createdAt
) {}
