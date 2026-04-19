package com.caritasem.ruleuler.server.replay.model;

import java.util.List;

public record ReplayReport(
    long taskId,
    Summary summary,
    List<VariableStat> variableStats,
    TimingComparison timing
) {
    public record Summary(
        int totalCount,
        int matchCount,
        int mismatchCount,
        int errorCount,
        int incompleteCount,
        double matchRate
    ) {}

    public record VariableStat(
        String category,
        String name,
        int changeCount,
        int totalCompared,
        double changeRate
    ) {}

    public record TimingComparison(
        double originalAvgMs,
        double replayAvgMs,
        double originalP50Ms,
        double replayP50Ms,
        double originalP95Ms,
        double replayP95Ms
    ) {}
}
