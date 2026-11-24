package com.campus.contest.module.team.support.model;

public record RegistrationEligibilityResult(
        boolean canRegister,
        String registerState,
        String reason,
        Long entryGrantId,
        Long quotaSchoolId,
        Long quotaId,
        Integer maxTeams,
        Integer usedTeams
) {
}
