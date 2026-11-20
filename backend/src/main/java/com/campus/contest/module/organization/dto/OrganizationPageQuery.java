package com.campus.contest.module.organization.dto;

import lombok.Data;

@Data
public class OrganizationPageQuery {

    private Integer page;
    private Integer size;
    private String orgType;
    private String provinceCode;
    private String keyword;
}
