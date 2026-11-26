package com.campus.contest.module.team.dto;

import com.fasterxml.jackson.annotation.JsonProperty;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import lombok.Data;

@Data
public class TeamRegistrationSubmitRequest {

    @NotNull
    @JsonProperty("version")
    private Integer version;

    @JsonProperty("teacher_id")
    private String teacherId;

    @Size(max = 500)
    @JsonProperty("remark")
    private String remark;
}
