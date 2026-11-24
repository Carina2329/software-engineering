package com.campus.contest.module.team.vo;

import com.fasterxml.jackson.annotation.JsonProperty;

public record TeamCreateVO(
        String id,
        @JsonProperty("team_code") String teamCode,
        String status,
        @JsonProperty("leader_member_id") String leaderMemberId
) {
}
