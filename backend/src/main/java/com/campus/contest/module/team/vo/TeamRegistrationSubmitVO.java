package com.campus.contest.module.team.vo;

import com.fasterxml.jackson.annotation.JsonProperty;

public record TeamRegistrationSubmitVO(
        @JsonProperty("team_id") String teamId,
        String status,
        int version,
        @JsonProperty("registration_id") String registrationId,
        @JsonProperty("round_no") int roundNo
) {
}
