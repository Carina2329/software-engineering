package com.campus.contest.module.team.vo;

import com.fasterxml.jackson.annotation.JsonProperty;

public record TeamPageItemVO(
        String id,
        @JsonProperty("team_name") String teamName,
        @JsonProperty("team_code") String teamCode,
        String status,
        @JsonProperty("member_count") int memberCount
) {
}
