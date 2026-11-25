package com.campus.contest.module.team.vo;

import com.fasterxml.jackson.annotation.JsonProperty;

import java.time.LocalDateTime;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;

public record TeamMemberVO(
        @JsonProperty("member_id") String memberId,
        @JsonProperty("user_id") String userId,
        @JsonProperty("member_name") String memberName,
        @JsonProperty("school_id") String schoolId,
        @JsonProperty("student_no") String studentNo,
        @JsonProperty("is_leader") boolean leader,
        @JsonProperty("join_time") OffsetDateTime joinTime,
        String status
) {
    private static final ZoneOffset SHANGHAI_OFFSET = ZoneOffset.ofHours(8);

    public static TeamMemberVO from(
            Long memberId,
            Long userId,
            String memberName,
            Long schoolId,
            String studentNo,
            boolean leader,
            LocalDateTime joinTime
    ) {
        return new TeamMemberVO(
                String.valueOf(memberId),
                userId == null ? null : String.valueOf(userId),
                memberName,
                String.valueOf(schoolId),
                studentNo,
                leader,
                joinTime == null ? null : joinTime.atOffset(SHANGHAI_OFFSET),
                "IN"
        );
    }
}
