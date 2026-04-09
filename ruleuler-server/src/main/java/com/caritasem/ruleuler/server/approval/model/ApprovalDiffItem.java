package com.caritasem.ruleuler.server.approval.model;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ApprovalDiffItem {
    private Long id;
    private Long approvalId;
    private String componentPath;
    private String componentName;
    private String componentType;
    private String changeType; // ADDED / MODIFIED / DELETED
    private String prevVersion;
    private String currVersion;
}
