package com.campus.contest.module.system.dto;

import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.Data;

import java.util.List;

/**
 * EP-078 替换角色权限请求，字段与 docs/api/openapi-v1.json 的 RolePermissionUpdateRequest 一致。
 */
@Data
public class RolePermissionUpdateRequest {

    @JsonProperty("menu_ids")
    private List<String> menuIds;

    @JsonProperty("version")
    private Integer version;
}
