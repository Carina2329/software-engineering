package com.campus.contest.module.organization.dto;

import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.Data;

@Data
public class OrganizationCreateRequest {

    @JsonProperty("org_code")
    private String orgCode;

    @JsonProperty("org_name")
    private String orgName;

    @JsonProperty("org_type")
    private String orgType;

    @JsonProperty("province_code")
    private String provinceCode;
}
