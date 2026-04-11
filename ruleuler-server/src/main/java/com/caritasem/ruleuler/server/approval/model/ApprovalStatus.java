package com.caritasem.ruleuler.server.approval.model;

/**
 * 审批单状态。
 * TESTING: 提交后自动测试执行中
 * PENDING: 待审核（测试完成或无测试）
 * APPROVED→PUBLISHED, APPROVED→PUBLISH_FAILED, PUBLISH_FAILED→PUBLISHED
 */
public enum ApprovalStatus {
    TESTING,
    PENDING,
    APPROVED,
    REJECTED,
    PUBLISH_FAILED,
    PUBLISHED
}
