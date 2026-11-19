package com.campus.contest.module.organization.dto;

import com.fasterxml.jackson.annotation.JsonIgnore;
import com.fasterxml.jackson.annotation.JsonProperty;

import java.time.LocalDateTime;

/**
 * EP-091 修改或撤销范围角色。
 */
public class RoleAssignmentUpdateRequest {

    private String scopeType;
    private String scopeId;
    private Boolean active;
    private LocalDateTime validTo;
    private Integer version;
    private boolean scopePresent;
    private boolean validToPresent;

    public String getScopeType() {
        return scopeType;
    }

    @JsonProperty("scope_type")
    public void setScopeType(String scopeType) {
        this.scopeType = scopeType;
        this.scopePresent = true;
    }

    public String getScopeId() {
        return scopeId;
    }

    @JsonProperty("scope_id")
    public void setScopeId(String scopeId) {
        this.scopeId = scopeId;
        this.scopePresent = true;
    }

    public Boolean getActive() {
        return active;
    }

    @JsonProperty("active")
    public void setActive(Boolean active) {
        this.active = active;
    }

    public LocalDateTime getValidTo() {
        return validTo;
    }

    @JsonProperty("valid_to")
    public void setValidTo(LocalDateTime validTo) {
        this.validTo = validTo;
        this.validToPresent = true;
    }

    public Integer getVersion() {
        return version;
    }

    @JsonProperty("version")
    public void setVersion(Integer version) {
        this.version = version;
    }

    @JsonIgnore
    public boolean hasScope() {
        return scopePresent;
    }

    @JsonIgnore
    public boolean hasValidTo() {
        return validToPresent;
    }
}
