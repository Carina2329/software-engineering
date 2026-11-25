package com.campus.contest.module.team.vo;

import com.fasterxml.jackson.annotation.JsonProperty;

public record TeamRegistrationWithdrawVO(
        @JsonProperty("team_id") String teamId,
        @JsonProperty("registration_id") String registrationId,
        String status,
        int version
) {
}
