package com.campus.contest.module.team.support.model;

public record RegistrationEligibilityContext(
        Long teamId,
        Long editionId,
        Long registrationId,
        RegistrationCheckMode mode
) {
}
