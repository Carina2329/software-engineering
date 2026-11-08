package com.campus.contest.module.system.service;

import com.campus.contest.module.system.dto.RoleCreateRequest;
import com.campus.contest.module.system.dto.RolePermissionUpdateRequest;
import com.campus.contest.module.system.dto.RoleUpdateRequest;
import com.campus.contest.module.system.vo.IdVersionVO;
import com.campus.contest.module.system.vo.RolePermissionVO;
import com.campus.contest.module.system.vo.RoleVO;
import com.campus.contest.module.system.vo.RoleVersionVO;

import java.util.List;

/**
 * 角色管理与角色授权（EP-073 ~ EP-078）。
 */
public interface RoleService {

    /** EP-073 角色列表 */
    List<RoleVO> listRoles();

    /** EP-074 创建角色，role_code 唯一，必须携带 Idempotency-Key */
    RoleVO createRole(RoleCreateRequest request, String idempotencyKey);

    /** EP-075 修改角色，必须携带 role.version 和 Idempotency-Key */
    IdVersionVO updateRole(Long roleId, RoleUpdateRequest request, String idempotencyKey);

    /** EP-077 查询角色权限 */
    RolePermissionVO getRolePermissions(Long roleId);

    /** EP-078 替换角色权限，必须携带 role.version 和 Idempotency-Key */
    RoleVersionVO replaceRolePermissions(
            Long roleId,
            RolePermissionUpdateRequest request,
            String idempotencyKey
    );
}
