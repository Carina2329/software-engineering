package com.campus.contest.module.team.dto;

import com.fasterxml.jackson.annotation.JsonProperty;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import lombok.Data;

import java.util.List;

@Data
public class TeamUpdateRequest {

    @JsonProperty("team_name")
    private String teamName;

    @NotNull
    @JsonProperty("version")
    private Integer version;

    @Valid
    @Size(max = 4)
    @JsonProperty("members")
    private List<TeamMemberInput> members;
}
