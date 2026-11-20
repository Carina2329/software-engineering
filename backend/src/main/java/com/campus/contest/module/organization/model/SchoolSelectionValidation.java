package com.campus.contest.module.organization.model;

/**
 * 供 EP-004 档案任务调用的学校选择校验结果。持久化和档案审计仍由档案任务事务负责。
 */
public record SchoolSelectionValidation(
        Long userId,
        Long schoolId,
        String targetStatus,
        Integer version,
        boolean noChange,
        boolean clearPreviousReview
) {
}
