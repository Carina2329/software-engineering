package com.campus.contest.module.contest.mapper;

import lombok.Data;

import java.util.LinkedHashSet;
import java.util.Set;

@Data
public class ContestAccessCriteria {

    private boolean manager;
    private boolean global;
    private Set<Long> organizationIds = new LinkedHashSet<>();
    private Set<Long> provinceCodes = new LinkedHashSet<>();
    private Set<Long> eventIds = new LinkedHashSet<>();
    private Long viewerSchoolId;
    private Long viewerProvinceCode;

    private Long contestId;
    private Long editionId;
    private Integer year;
    private Integer status;
    private String seriesLevel;
    private String level;
    private Long ownerOrgId;
    private String provinceCode;
    private Long schoolId;
    private String visibility;
    private String keyword;

    private String sortColumn = "c.created_at";
    private String sortDirection = "DESC";
}
