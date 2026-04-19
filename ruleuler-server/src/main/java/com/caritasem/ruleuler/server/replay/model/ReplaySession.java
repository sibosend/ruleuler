package com.caritasem.ruleuler.server.replay.model;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ReplaySession {
    private Long id;
    private Long taskId;
    private String originalExecutionId;
    private String replayInput;
    private String originalOutput;
    private String replayOutput;
    private String diffResult;
    private String missingCategories;
    private String missingVariables;
    private String filledVariables;
    private String completenessStatus;
    private Integer execMs;
    private Integer originalExecMs;
    private String status;
    private String errorMessage;
    private Long createdAt;
}
