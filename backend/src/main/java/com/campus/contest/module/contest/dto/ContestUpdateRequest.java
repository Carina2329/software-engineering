package com.campus.contest.module.contest.dto;

import com.fasterxml.jackson.annotation.JsonProperty;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import lombok.Data;

@Data
public class ContestUpdateRequest {

    @Size(min = 1, max = 128)
    @JsonProperty("contest_name")
    private String contestName;

    @JsonProperty("level")
    private String level;

    @Size(max = 128)
    @JsonProperty("organizer")
    private String organizer;

    @Size(max = 1000)
    @JsonProperty("intro")
    private String intro;

    @Size(min = 1, max = 32)
    @JsonProperty("series_code")
    private String seriesCode;

    @NotNull
    @JsonProperty("version")
    private Integer version;
}
