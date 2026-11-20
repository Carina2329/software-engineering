package com.campus.contest.module.organization.vo;

import com.campus.contest.module.organization.entity.OrganizationDO;
import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.Data;

@Data
public class OrganizationItemVO {

    private String id;

    @JsonProperty("org_code")
    private String orgCode;

    @JsonProperty("org_name")
    private String orgName;

    @JsonProperty("org_type")
    private String orgType;

    @JsonProperty("province_code")
    private String provinceCode;

    private Integer status;

    private Integer version;

    public static OrganizationItemVO from(OrganizationDO organization) {
        OrganizationItemVO item = new OrganizationItemVO();
        item.setId(String.valueOf(organization.getId()));
        item.setOrgCode(organization.getOrgCode());
        item.setOrgName(organization.getOrgName());
        item.setOrgType(organization.getOrgType());
        item.setProvinceCode(organization.getProvinceCode());
        item.setStatus(organization.getStatus());
        item.setVersion(organization.getVersion());
        return item;
    }
}
