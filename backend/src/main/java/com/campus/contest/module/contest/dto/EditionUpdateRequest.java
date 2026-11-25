package com.campus.contest.module.contest.dto;

import com.fasterxml.jackson.annotation.JsonProperty;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import lombok.Data;

import java.time.OffsetDateTime;
import java.util.List;

@Data
public class EditionUpdateRequest {

    @Size(min = 1, max = 128)
    @JsonProperty("edition_name")
    private String editionName;

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

    @JsonProperty("min_team_size")
    private Integer minTeamSize;

    @JsonProperty("max_team_size")
    private Integer maxTeamSize;

    @JsonProperty("topic_mode")
    private String topicMode;

    @JsonProperty("topic_review_required")
    private Boolean topicReviewRequired;

    @JsonProperty("topic_allocation_mode")
    private String topicAllocationMode;

    @JsonProperty("topic_change_limit")
    private Integer topicChangeLimit;

    @NotNull
    @JsonProperty("version")
    private Integer version;

    @JsonProperty("team_size_mode")
    private String teamSizeMode;

    @JsonProperty("max_teams_per_teacher")
    private Integer maxTeamsPerTeacher;

    @JsonProperty("owner_org_id")
    private String ownerOrgId;

    @JsonProperty("level")
    private String level;

    @JsonProperty("school_id")
    private String schoolId;

    @JsonProperty("province_code")
    private String provinceCode;

    @JsonProperty("visibility")
    private String visibility;

    @JsonProperty("entry_mode")
    private String entryMode;

    @JsonProperty("cross_school_team_allowed")
    private Boolean crossSchoolTeamAllowed;

    @Valid
    @JsonProperty("school_quotas")
    private List<EditionSchoolQuotaRequest> schoolQuotas;
}
