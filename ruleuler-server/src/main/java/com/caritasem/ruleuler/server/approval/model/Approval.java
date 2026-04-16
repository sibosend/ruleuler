package com.caritasem.ruleuler.server.approval.model;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class Approval {
    private Long id;
    private String project;
    private String packageId;
    private String packageName;
    private ApprovalStatus status;
    private String submitter;
    private String approver;
    private String comment;
    private String failReason;
    private String publisher;
    private Long submittedAt;
    private Long approvedAt;
    private Long publishedAt;
    private Long testRunId;
    private String description;  // 变更说明
    private int version;  // 系统版本号
}
