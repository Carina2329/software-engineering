package com.campus.contest.module.organization.dto;

import lombok.Data;

@Data
public class SchoolPageQuery {

    private Integer page;
    private Integer size;
    private String keyword;
    private String provinceCode;
}
