package com.caritasem.ruleuler.server.approval.model;

/**
 * 审批单状态。
 * 合法流转：PENDING→APPROVED, PENDING→REJECTED, APPROVED→PUBLISHED, APPROVED→PUBLISH_FAILED, PUBLISH_FAILED→PUBLISHED
 */
public enum ApprovalStatus {
    PENDING,
    APPROVED,
    REJECTED,
    PUBLISH_FAILED,
    PUBLISHED
}
