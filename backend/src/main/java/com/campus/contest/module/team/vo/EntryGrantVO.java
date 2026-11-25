package com.campus.contest.module.team.vo;

import com.fasterxml.jackson.annotation.JsonProperty;

public record EntryGrantVO(
        String id,
        @JsonProperty("grant_type") String grantType,
        @JsonProperty("leader_user_id") String leaderUserId,
        @JsonProperty("leader_name") String leaderName,
        @JsonProperty("school_id") String schoolId,
        @JsonProperty("student_no") String studentNo,
        @JsonProperty("source_edition_id") String sourceEditionId,
        @JsonProperty("source_team_code") String sourceTeamCode,
        @JsonProperty("target_team_id") String targetTeamId,
        String status,
        String reason,
        Integer version
) {
}
