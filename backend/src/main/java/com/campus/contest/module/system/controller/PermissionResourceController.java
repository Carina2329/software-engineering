package com.campus.contest.module.system.controller;

import com.campus.contest.common.Result;
import com.campus.contest.module.system.service.PermissionResourceService;
import com.campus.contest.module.system.vo.PermissionResourceVO;
import com.campus.contest.security.CurrentUserProvider;
import lombok.RequiredArgsConstructor;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

/**
 * EP-076 权限资源树、EP-008 当前用户菜单。
 *
 * /menus/current 面向所有已登录用户，不加 @PreAuthorize，
 * 由 SecurityConfig 的 anyRequest().authenticated() 兜底。
 */
@RestController
@RequestMapping("/api/v1/menus")
@RequiredArgsConstructor
public class PermissionResourceController {

    private final PermissionResourceService permissionResourceService;
    private final CurrentUserProvider currentUserProvider;

    /** EP-076 权限资源树 */
    @GetMapping
    @PreAuthorize("hasAuthority('system:menu:list')")
    public Result<List<PermissionResourceVO>> menuTree() {
        return Result.ok(permissionResourceService.menuTree());
    }

    /** EP-008 当前用户菜单 */
    @GetMapping("/current")
    public Result<List<PermissionResourceVO>> currentUserMenuTree() {
        return Result.ok(permissionResourceService.currentUserMenuTree(currentUserProvider.requireUserId()));
    }
}
