package com.caritasem.ruleuler.server.approval.model;

/**
 * 审批单状态。
 * 合法流转：PENDING→APPROVED, PENDING→REJECTED, APPROVED→PUBLISH_FAILED
 */
public enum ApprovalStatus {
    PENDING,
    APPROVED,
    REJECTED,
    PUBLISH_FAILED
}
