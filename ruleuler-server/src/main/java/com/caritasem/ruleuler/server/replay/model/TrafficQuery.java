package com.caritasem.ruleuler.server.replay.model;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class TrafficQuery {
    private String project;
    private String packageId;
    private String flowId;
    private Long startTime;
    private Long endTime;
    @Builder.Default
    private String sampleStrategy = "random";
    @Builder.Default
    private int sampleSize = 10000;
    @Builder.Default
    private String missingVarStrategy = "null";

    public static TrafficQuery defaultForApproval(String project, String packageId) {
        long now = System.currentTimeMillis();
        return TrafficQuery.builder()
                .project(project)
                .packageId(packageId)
                .startTime(now - 86400000L)
                .endTime(now)
                .sampleStrategy("random")
                .sampleSize(10000)
                .missingVarStrategy("segment")
                .build();
    }
}
