package com.campus.contest.module.team.vo;

import com.fasterxml.jackson.annotation.JsonProperty;

import java.util.List;

public record TeamDetailVO(
        String id,
        @JsonProperty("edition_id") String editionId,
        @JsonProperty("team_name") String teamName,
        @JsonProperty("team_code") String teamCode,
        @JsonProperty("topic_id") String topicId,
        @JsonProperty("leader_id") String leaderId,
        @JsonProperty("teacher_id") String teacherId,
        String status,
        @JsonProperty("member_count") int memberCount,
        List<String> members,
        int version,
        @JsonProperty("current_registration_id") String currentRegistrationId,
        @JsonProperty("current_registration_version") Integer currentRegistrationVersion,
        @JsonProperty("current_registration_status") String currentRegistrationStatus,
        @JsonProperty("current_reject_stage") String currentRejectStage,
        @JsonProperty("current_reject_reason") String currentRejectReason,
        @JsonProperty("current_teacher_id") String currentTeacherId,
        @JsonProperty("current_teacher_name") String currentTeacherName,
        @JsonProperty("current_teacher_application_status") String currentTeacherApplicationStatus,
        @JsonProperty("can_register") boolean canRegister,
        @JsonProperty("register_state") String registerState,
        @JsonProperty("register_reason") String registerReason
) {
}
