package com.campus.contest.module.contest.dto;

import com.fasterxml.jackson.annotation.JsonProperty;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import lombok.Data;

import java.time.OffsetDateTime;

@Data
public class MilestoneUpdateRequest {

    @Size(min = 1, max = 64)
    @JsonProperty("node_name")
    private String nodeName;

    @JsonProperty("start_time")
    private OffsetDateTime startTime;

    @JsonProperty("end_time")
    private OffsetDateTime endTime;

    @JsonProperty("enabled")
    private Boolean enabled;

    @NotNull
    @JsonProperty("version")
    private Integer version;
}
