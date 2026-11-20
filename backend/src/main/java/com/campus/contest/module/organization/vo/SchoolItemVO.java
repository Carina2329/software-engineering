package com.campus.contest.module.organization.vo;

import com.campus.contest.module.organization.entity.OrganizationDO;
import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.Data;

@Data
public class SchoolItemVO {

    private String id;

    @JsonProperty("school_code")
    private String schoolCode;

    @JsonProperty("school_name")
    private String schoolName;

    @JsonProperty("province_code")
    private String provinceCode;

    @JsonProperty("province_name")
    private String provinceName;

    public static SchoolItemVO from(OrganizationDO school, String provinceName) {
        SchoolItemVO item = new SchoolItemVO();
        item.setId(String.valueOf(school.getId()));
        item.setSchoolCode(school.getOrgCode());
        item.setSchoolName(school.getOrgName());
        item.setProvinceCode(school.getProvinceCode());
        item.setProvinceName(provinceName);
        return item;
    }
}
