package com.campus.contest.module.contest.mapper;

import lombok.Data;

import java.util.ArrayList;
import java.util.List;

@Data
public class ContestAccessCriteria {

    private boolean manager;
    private boolean global;
    private List<Long> organizationIds = new ArrayList<>();
    private List<Long> provinceCodes = new ArrayList<>();
    private List<Long> eventIds = new ArrayList<>();
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

    private String sortColumn;
    private String sortDirection = "DESC";
}
