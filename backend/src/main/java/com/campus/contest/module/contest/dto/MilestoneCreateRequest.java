package com.campus.contest.module.contest.dto;

import com.fasterxml.jackson.annotation.JsonProperty;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import lombok.Data;

import java.time.OffsetDateTime;

@Data
public class MilestoneCreateRequest {

    @NotBlank
    @JsonProperty("node_type")
    private String nodeType;

    @NotBlank
    @Size(max = 64)
    @JsonProperty("node_name")
    private String nodeName;

    @NotNull
    @JsonProperty("start_time")
    private OffsetDateTime startTime;

    @NotNull
    @JsonProperty("end_time")
    private OffsetDateTime endTime;

    @JsonProperty("enabled")
    private Boolean enabled;
}
