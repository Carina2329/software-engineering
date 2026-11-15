package com.campus.contest.module.system.vo;

import com.campus.contest.module.system.entity.SysRoleDO;
import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.Data;

/**
 * EP-073 / EP-074 角色响应，对应 OpenAPI 的 Role。
 */
@Data
public class RoleVO {

    private String id;

    @JsonProperty("role_code")
    private String roleCode;

    @JsonProperty("role_name")
    private String roleName;

    @JsonProperty("data_scope")
    private String dataScope;

    private Integer version;

    public static RoleVO from(SysRoleDO role) {
        RoleVO vo = new RoleVO();
        vo.setId(role.getId() == null ? null : String.valueOf(role.getId()));
        vo.setRoleCode(role.getRoleCode());
        vo.setRoleName(role.getRoleName());
        vo.setDataScope(role.getDataScope());
        vo.setVersion(role.getVersion());
        return vo;
    }
}
