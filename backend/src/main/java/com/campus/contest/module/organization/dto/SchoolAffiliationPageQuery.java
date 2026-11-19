package com.campus.contest.module.organization.dto;

import lombok.Data;

@Data
public class SchoolAffiliationPageQuery {

    private String status;
    private String schoolId;
    private String keyword;
    private Integer page;
    private Integer size;
}
