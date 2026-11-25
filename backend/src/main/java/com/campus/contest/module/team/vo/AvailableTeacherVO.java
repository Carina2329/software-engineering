package com.campus.contest.module.team.vo;

import com.fasterxml.jackson.annotation.JsonProperty;

public record AvailableTeacherVO(
        String id,
        @JsonProperty("real_name") String realName,
        @JsonProperty("school_id") String schoolId,
        @JsonProperty("school_name") String schoolName,
        String college,
        String title
) {
}
