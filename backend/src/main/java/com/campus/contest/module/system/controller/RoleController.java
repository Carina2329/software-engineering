package com.campus.contest.module.system.controller;

import com.campus.contest.common.Result;
import com.campus.contest.module.system.dto.RoleCreateRequest;
import com.campus.contest.module.system.dto.RolePermissionUpdateRequest;
import com.campus.contest.module.system.dto.RoleUpdateRequest;
import com.campus.contest.module.system.service.RoleService;
import com.campus.contest.module.system.vo.IdVersionVO;
import com.campus.contest.module.system.vo.RolePermissionVO;
import com.campus.contest.module.system.vo.RoleVO;
import com.campus.contest.module.system.vo.RoleVersionVO;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

/**
 * EP-073 ~ EP-075、EP-077、EP-078：角色管理与角色授权。
 *
 * 权限编码来自 sys_menu.perms 种子数据，和 @PreAuthorize 使用同一套编码，
 * 不在代码里硬编码一份独立权限清单。
 */
@RestController
@RequestMapping("/api/v1/roles")
@RequiredArgsConstructor
public class RoleController {

    private final RoleService roleService;

    /** EP-073 角色列表 */
    @GetMapping
    @PreAuthorize("hasAuthority('system:role:list')")
    public Result<List<RoleVO>> listRoles() {
        return Result.ok(roleService.listRoles());
    }

    /** EP-074 创建角色 */
    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    @PreAuthorize("hasAuthority('system:role:create')")
    public Result<RoleVO> createRole(
            @RequestBody RoleCreateRequest request,
            @RequestHeader(value = "Idempotency-Key", required = false) String idempotencyKey
    ) {
        return Result.ok(roleService.createRole(request, idempotencyKey));
    }

    /** EP-075 修改角色 */
    @PatchMapping("/{role_id}")
    @PreAuthorize("hasAuthority('system:role:update')")
    public Result<IdVersionVO> updateRole(
            @PathVariable("role_id") Long roleId,
            @RequestBody RoleUpdateRequest request,
            @RequestHeader(value = "Idempotency-Key", required = false) String idempotencyKey
    ) {
        return Result.ok(roleService.updateRole(roleId, request, idempotencyKey));
    }

    /** EP-077 查询角色权限 */
    @GetMapping("/{role_id}/menus")
    @PreAuthorize("hasAuthority('system:role:list')")
    public Result<RolePermissionVO> getRolePermissions(@PathVariable("role_id") Long roleId) {
        return Result.ok(roleService.getRolePermissions(roleId));
    }

    /** EP-078 替换角色权限 */
    @PutMapping("/{role_id}/menus")
    @PreAuthorize("hasAuthority('system:role:authorize')")
    public Result<RoleVersionVO> replaceRolePermissions(
            @PathVariable("role_id") Long roleId,
            @RequestBody RolePermissionUpdateRequest request,
            @RequestHeader(value = "Idempotency-Key", required = false) String idempotencyKey
    ) {
        return Result.ok(roleService.replaceRolePermissions(roleId, request, idempotencyKey));
    }
}
