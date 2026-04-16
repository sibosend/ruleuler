package com.caritasem.ruleuler.server.grayscale.model;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class GrayscaleRule {
    private Long id;
    private String project;
    private String packageId;
    private Long approvalId;
    private Long snapshotId;
    private GrayscaleStrategy strategy;
    private Integer percentage;
    private String conditionExpr;  // JSON array of ConditionItem
    private GrayscaleRuleStatus status;
    private String description;
    private String createdBy;
    private Long createdAt;
    private Long updatedAt;
}
