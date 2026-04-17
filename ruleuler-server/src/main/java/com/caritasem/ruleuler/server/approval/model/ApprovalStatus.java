package com.caritasem.ruleuler.server.approval.model;

/**
 * 审批单状态。
 * TESTING: 提交后自动测试执行中
 * PENDING: 待审核（测试完成或无测试）
 * APPROVED: 待上线
 * GRAYSCALE: 灰度发布中
 * REJECTED: 已拒绝
 * PUBLISH_FAILED: 上线失败
 * PUBLISHED: 已上线
 */
public enum ApprovalStatus {
    TESTING,
    PENDING,
    APPROVED,
    GRAYSCALE,
    REJECTED,
    PUBLISH_FAILED,
    PUBLISHED
}
