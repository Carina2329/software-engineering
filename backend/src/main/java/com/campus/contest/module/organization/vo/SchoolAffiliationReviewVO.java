package com.campus.contest.module.organization.vo;

import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.AllArgsConstructor;
import lombok.Data;

@Data
@AllArgsConstructor
public class SchoolAffiliationReviewVO {

    @JsonProperty("user_id")
    private String userId;

    private String status;

    private Integer version;
}
