package com.caritasem.ruleuler.server.replay.model;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ReplayTask {
    private Long id;
    private String project;
    private String packageId;
    private String flowId;
    private String trafficQuery;
    private String sampleStrategy;
    private int sampleSize;
    private String missingVarStrategy;
    private int totalCount;
    private int executedCount;
    private int matchCount;
    private int mismatchCount;
    private int errorCount;
    private int incompleteCount;
    private String status;
    private String toleranceConfig;
    private Long startedAt;
    private Long finishedAt;
    private Long createdAt;
}
