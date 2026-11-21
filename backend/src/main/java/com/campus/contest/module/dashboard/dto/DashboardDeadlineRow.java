package com.campus.contest.module.dashboard.dto;

import java.time.LocalDateTime;

public record DashboardDeadlineRow(
        Long editionId,
        String editionName,
        String contestName,
        String level,
        LocalDateTime registerDeadline
) {
}
