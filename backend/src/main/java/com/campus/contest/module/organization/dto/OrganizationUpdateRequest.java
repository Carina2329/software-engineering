package com.campus.contest.module.organization.dto;

import com.fasterxml.jackson.annotation.JsonIgnore;
import com.fasterxml.jackson.annotation.JsonProperty;

/**
 * EP-089 修改组织。显式记录 province_code 是否出现，以区分“保持不变”和“设置为空”。
 */
public class OrganizationUpdateRequest {

    private String orgName;
    private String provinceCode;
    private Integer status;
    private Integer version;
    private boolean orgNamePresent;
    private boolean provinceCodePresent;

    public String getOrgName() {
        return orgName;
    }

    @JsonProperty("org_name")
    public void setOrgName(String orgName) {
        this.orgNamePresent = true;
        this.orgName = orgName;
    }

    public String getProvinceCode() {
        return provinceCode;
    }

    @JsonProperty("province_code")
    public void setProvinceCode(String provinceCode) {
        this.provinceCodePresent = true;
        this.provinceCode = provinceCode;
    }

    public Integer getStatus() {
        return status;
    }

    @JsonProperty("status")
    public void setStatus(Integer status) {
        this.status = status;
    }

    public Integer getVersion() {
        return version;
    }

    @JsonProperty("version")
    public void setVersion(Integer version) {
        this.version = version;
    }

    @JsonIgnore
    public boolean hasOrgName() {
        return orgNamePresent;
    }

    @JsonIgnore
    public boolean hasProvinceCode() {
        return provinceCodePresent;
    }
}
