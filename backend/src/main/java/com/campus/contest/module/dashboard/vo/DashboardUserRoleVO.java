package com.campus.contest.module.dashboard.vo;

import com.fasterxml.jackson.annotation.JsonProperty;

public record DashboardUserRoleVO(
        @JsonProperty("role_code") String roleCode,
        @JsonProperty("role_name") String roleName,
        @JsonProperty("user_count") long userCount
) {
}
