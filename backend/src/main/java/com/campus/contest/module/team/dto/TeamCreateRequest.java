package com.campus.contest.module.team.dto;

import com.fasterxml.jackson.annotation.JsonProperty;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.Data;

@Data
public class TeamCreateRequest {

    @NotBlank
    @Size(max = 64)
    @JsonProperty("team_name")
    private String teamName;

    @JsonProperty("version")
    private Integer version;
}
