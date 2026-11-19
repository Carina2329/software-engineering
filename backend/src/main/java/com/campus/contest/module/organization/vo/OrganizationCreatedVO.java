package com.campus.contest.module.organization.vo;

import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class OrganizationCreatedVO {

    private String id;

    @JsonProperty("org_code")
    private String orgCode;
}
