package com.caritasem.ruleuler.server.approval.model;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class PublishSnapshot {
    private Long id;
    private String project;
    private String packageId;
    private Long approvalId;
    private String snapshotData; // JSON: {path: version_name}
    private Long createdAt;
}
