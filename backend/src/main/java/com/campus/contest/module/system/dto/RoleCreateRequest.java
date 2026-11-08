package com.campus.contest.module.system.dto;

import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.Data;

/**
 * EP-074 创建角色请求，字段与 docs/api/openapi-v1.json 的 RoleCreateRequest 一致。
 */
@Data
public class RoleCreateRequest {

    @JsonProperty("role_code")
    private String roleCode;

    @JsonProperty("role_name")
    private String roleName;

    @JsonProperty("data_scope")
    private String dataScope;
}
