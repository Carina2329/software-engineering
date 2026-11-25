package com.campus.contest.module.team.dto;

import com.fasterxml.jackson.annotation.JsonProperty;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.Data;

@Data
public class EntryGrantBatchItem {

    @NotBlank
    @JsonProperty("action")
    private String action;

    @JsonProperty("grant_id")
    private String grantId;

    @JsonProperty("version")
    private Integer version;

    @JsonProperty("grant_type")
    private String grantType;

    @JsonProperty("leader_user_id")
    private String leaderUserId;

    @Size(max = 32)
    @JsonProperty("leader_name")
    private String leaderName;

    @JsonProperty("school_id")
    private String schoolId;

    @Size(max = 20)
    @JsonProperty("student_no")
    private String studentNo;

    @JsonProperty("source_edition_id")
    private String sourceEditionId;

    @Size(max = 16)
    @JsonProperty("source_team_code")
    private String sourceTeamCode;

    @Size(max = 500)
    @JsonProperty("reason")
    private String reason;
}
