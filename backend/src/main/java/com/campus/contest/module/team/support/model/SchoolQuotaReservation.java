package com.campus.contest.module.team.support.model;

public record SchoolQuotaReservation(
        Long quotaId,
        Long schoolId,
        Integer maxTeams,
        Integer usedTeams,
        boolean limited,
        boolean held
) {
}
