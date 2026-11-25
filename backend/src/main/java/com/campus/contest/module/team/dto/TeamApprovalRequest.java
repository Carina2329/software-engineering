package com.campus.contest.module.team.dto;

import com.fasterxml.jackson.annotation.JsonProperty;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import lombok.Data;

@Data
public class TeamApprovalRequest {

    @NotBlank
    @JsonProperty("stage")
    private String stage;

    @NotBlank
    @JsonProperty("decision")
    private String decision;

    @Size(max = 500)
    @JsonProperty("reason")
    private String reason;

    @NotNull
    @JsonProperty("version")
    private Integer version;
}
