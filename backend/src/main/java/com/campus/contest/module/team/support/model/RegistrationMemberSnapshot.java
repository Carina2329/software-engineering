package com.campus.contest.module.team.support.model;

import com.fasterxml.jackson.annotation.JsonProperty;

public record RegistrationMemberSnapshot(
        @JsonProperty("member_id") Long memberId,
        @JsonProperty("user_id") Long userId,
        @JsonProperty("member_name") String memberName,
        @JsonProperty("school_id") Long schoolId,
        @JsonProperty("student_no") String studentNo,
        @JsonProperty("is_leader") boolean leader
) {
}
