package com.campus.contest.module.organization.dto;

import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.Data;

@Data
public class SchoolAffiliationReviewRequest {

    @JsonProperty("decision")
    private String decision;

    @JsonProperty("reason")
    private String reason;

    @JsonProperty("version")
    private Integer version;
}
