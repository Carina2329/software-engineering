package com.campus.contest.module.contest.vo;

import com.fasterxml.jackson.annotation.JsonProperty;

public record EditionSchoolQuotaVO(
        @JsonProperty("school_id") String schoolId,
        @JsonProperty("max_teams") Integer maxTeams,
        @JsonProperty("used_teams") int usedTeams
) {
}
