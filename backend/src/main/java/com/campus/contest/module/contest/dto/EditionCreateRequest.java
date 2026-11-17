package com.campus.contest.module.contest.dto;

import com.fasterxml.jackson.annotation.JsonProperty;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import lombok.Data;

import java.time.OffsetDateTime;

@Data
public class EditionCreateRequest {

    @NotBlank
    @Size(max = 128)
    @JsonProperty("edition_name")
    private String editionName;

    @NotNull
    @Min(2000)
    @Max(2100)
    private Integer year;

    @JsonProperty("register_deadline")
    private OffsetDateTime registerDeadline;

    @JsonProperty("work_deadline")
    private OffsetDateTime workDeadline;

    @JsonProperty("review_start")
    private OffsetDateTime reviewStart;

    @JsonProperty("publish_start")
    private OffsetDateTime publishStart;

    @JsonProperty("publish_end")
    private OffsetDateTime publishEnd;

    @NotNull
    @Min(1)
    @Max(5)
    @JsonProperty("min_team_size")
    private Integer minTeamSize;

    @NotNull
    @Min(1)
    @Max(5)
    @JsonProperty("max_team_size")
    private Integer maxTeamSize;

    @JsonProperty("topic_mode")
    private String topicMode;

    @JsonProperty("topic_review_required")
    private Boolean topicReviewRequired;

    @JsonProperty("topic_allocation_mode")
    private String topicAllocationMode;

    @Min(0)
    @Max(3)
    @JsonProperty("topic_change_limit")
    private Integer topicChangeLimit;

    @NotBlank
    @JsonProperty("team_size_mode")
    private String teamSizeMode;

    @Min(1)
    @Max(10)
    @JsonProperty("max_teams_per_teacher")
    private Integer maxTeamsPerTeacher;
}
