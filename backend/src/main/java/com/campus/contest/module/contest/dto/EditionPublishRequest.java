package com.campus.contest.module.contest.dto;

import com.fasterxml.jackson.annotation.JsonProperty;
import jakarta.validation.constraints.NotNull;
import lombok.Data;

@Data
public class EditionPublishRequest {

    @NotNull
    @JsonProperty("version")
    private Integer version;
}
