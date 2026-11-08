package com.campus.contest.module.system.vo;

import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * EP-078 替换角色权限响应。
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
public class RoleVersionVO {

    @JsonProperty("role_id")
    private String roleId;

    private Integer version;
}
