package com.caritasem.ruleuler.server.approval.model;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder(toBuilder = true)
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
    /** 规则级变动详情 JSON，如 [{"rule":"行3","change":"MODIFIED","prev":"...","curr":"..."}] */
    private String details;
}
