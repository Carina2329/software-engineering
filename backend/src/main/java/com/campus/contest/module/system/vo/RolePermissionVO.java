package com.campus.contest.module.system.vo;

import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.Data;

import java.util.ArrayList;
import java.util.List;

/**
 * EP-077 / EP-078 角色权限响应。resources 为可勾选的权限资源树。
 */
@Data
public class RolePermissionVO {

    @JsonProperty("role_id")
    private String roleId;

    private Integer version;

    @JsonProperty("menu_ids")
    private List<String> menuIds = new ArrayList<>();

    private List<PermissionResourceVO> resources = new ArrayList<>();

    public static RolePermissionVO of(
            Long roleId,
            Integer version,
            List<String> menuIds,
            List<PermissionResourceVO> resources
    ) {
        RolePermissionVO vo = new RolePermissionVO();
        vo.setRoleId(roleId == null ? null : String.valueOf(roleId));
        vo.setVersion(version);
        vo.setMenuIds(menuIds == null ? new ArrayList<>() : new ArrayList<>(menuIds));
        vo.setResources(resources == null ? new ArrayList<>() : resources);
        return vo;
    }
}
