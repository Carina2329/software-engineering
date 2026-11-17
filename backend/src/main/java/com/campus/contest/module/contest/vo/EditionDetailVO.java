package com.campus.contest.module.contest.vo;

import com.campus.contest.module.contest.entity.ContestEditionDO;
import com.campus.contest.module.contest.enums.EditionStatus;
import com.campus.contest.module.contest.enums.TeamSizeMode;
import com.campus.contest.module.contest.enums.TopicAllocationMode;
import com.campus.contest.module.contest.enums.TopicMode;
import com.fasterxml.jackson.annotation.JsonProperty;

import java.time.LocalDateTime;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;

public record EditionDetailVO(
        String id,
        @JsonProperty("contest_id") String contestId,
        @JsonProperty("edition_name") String editionName,
        int year,
        String status,
        @JsonProperty("topic_mode") String topicMode,
        @JsonProperty("topic_review_required") boolean topicReviewRequired,
        @JsonProperty("topic_allocation_mode") String topicAllocationMode,
        @JsonProperty("topic_change_limit") int topicChangeLimit,
        @JsonProperty("register_deadline") OffsetDateTime registerDeadline,
        @JsonProperty("work_deadline") OffsetDateTime workDeadline,
        @JsonProperty("min_team_size") int minTeamSize,
        @JsonProperty("max_team_size") int maxTeamSize,
        int version,
        @JsonProperty("team_size_mode") String teamSizeMode,
        @JsonProperty("max_teams_per_teacher") int maxTeamsPerTeacher,
        @JsonProperty("review_start") OffsetDateTime reviewStart,
        @JsonProperty("publish_start") OffsetDateTime publishStart,
        @JsonProperty("publish_end") OffsetDateTime publishEnd,
        @JsonProperty("registration_open") boolean registrationOpen,
        @JsonProperty("submission_open") boolean submissionOpen,
        @JsonProperty("read_only") boolean readOnly
) {
    private static final ZoneOffset SHANGHAI_OFFSET = ZoneOffset.ofHours(8);

    public static EditionDetailVO from(
            ContestEditionDO edition,
            boolean registrationOpen,
            boolean submissionOpen,
            boolean readOnly
    ) {
        return new EditionDetailVO(
                String.valueOf(edition.getId()),
                String.valueOf(edition.getContestId()),
                edition.getEditionName(),
                edition.getYear(),
                EditionStatus.fromDatabaseValue(edition.getStatus()).name(),
                edition.getTopicMode() == null ? null : TopicMode.valueOf(edition.getTopicMode()).name(),
                Integer.valueOf(1).equals(edition.getTopicReviewRequired()),
                TopicAllocationMode.valueOf(edition.getTopicAllocationMode()).name(),
                edition.getTopicChangeLimit(),
                offset(edition.getRegisterDeadline()),
                offset(edition.getWorkDeadline()),
                edition.getMinTeamSize(),
                edition.getMaxTeamSize(),
                edition.getVersion(),
                TeamSizeMode.valueOf(edition.getTeamSizeMode()).name(),
                edition.getMaxTeamsPerTeacher(),
                offset(edition.getReviewStart()),
                offset(edition.getPublishStart()),
                offset(edition.getPublishEnd()),
                registrationOpen,
                submissionOpen,
                readOnly
        );
    }

    private static OffsetDateTime offset(LocalDateTime value) {
        return value == null ? null : value.atOffset(SHANGHAI_OFFSET);
    }
}
