package com.campus.contest.module.team.dto;

import com.fasterxml.jackson.annotation.JsonProperty;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import lombok.Data;

import java.util.List;

@Data
public class TeamRegistrationSubmitRequest {

    @NotNull
    @JsonProperty("version")
    private Integer version;

    @JsonProperty("teacher_id")
    private String teacherId;

    @NotEmpty
    @JsonProperty("attachment_file_ids")
    private List<String> attachmentFileIds;

    @Size(max = 500)
    @JsonProperty("remark")
    private String remark;
}
