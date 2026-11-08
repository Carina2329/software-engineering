package com.campus.contest.module.system.dto;

import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.Data;

/**
 * EP-075 修改角色请求，字段与 docs/api/openapi-v1.json 的 RoleUpdateRequest 一致。
 * version 必须携带，用于乐观锁校验。
 */
@Data
public class RoleUpdateRequest {

    @JsonProperty("role_name")
    private String roleName;

    @JsonProperty("data_scope")
    private String dataScope;

    @JsonProperty("version")
    private Integer version;
}
