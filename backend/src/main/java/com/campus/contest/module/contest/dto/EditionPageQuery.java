package com.campus.contest.module.contest.dto;

import lombok.Data;

@Data
public class EditionPageQuery {
    private Integer page;
    private Integer size;
    private String sort;
    private Integer year;
    private String status;
    private String level;
    private String ownerOrgId;
    private String provinceCode;
    private String schoolId;
    private String visibility;
}
