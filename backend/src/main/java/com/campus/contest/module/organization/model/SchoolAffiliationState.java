package com.campus.contest.module.organization.model;

import java.time.LocalDateTime;

public record SchoolAffiliationState(
        Long userId,
        Long schoolId,
        String schoolName,
        String provinceCode,
        String status,
        String reviewReason,
        Long reviewedBy,
        LocalDateTime reviewedAt,
        Integer version
) {
}
