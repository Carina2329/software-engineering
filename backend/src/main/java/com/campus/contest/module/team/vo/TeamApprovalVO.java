package com.campus.contest.module.team.vo;

import com.fasterxml.jackson.annotation.JsonProperty;

public record TeamApprovalVO(
        @JsonProperty("team_id") String teamId,
        String status,
        int version,
        @JsonProperty("registration_id") String registrationId
) {
}
