package com.campus.contest.module.contest.dto;

import com.fasterxml.jackson.annotation.JsonProperty;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import lombok.Data;

@Data
public class EditionSchoolQuotaRequest {

    @NotBlank
    @JsonProperty("school_id")
    private String schoolId;

    @Min(1)
    @JsonProperty("max_teams")
    private Integer maxTeams;
}
