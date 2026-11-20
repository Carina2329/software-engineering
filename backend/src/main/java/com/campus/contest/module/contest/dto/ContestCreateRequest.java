package com.campus.contest.module.contest.dto;

import com.fasterxml.jackson.annotation.JsonProperty;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.Data;

@Data
public class ContestCreateRequest {

    @NotBlank
    @Size(max = 128)
    @JsonProperty("contest_name")
    private String contestName;

    @JsonProperty("level")
    private String level;

    @NotBlank
    @Size(max = 32)
    @JsonProperty("series_code")
    private String seriesCode;

    @Size(max = 128)
    @JsonProperty("organizer")
    private String organizer;

    @Size(max = 1000)
    @JsonProperty("intro")
    private String intro;
}
