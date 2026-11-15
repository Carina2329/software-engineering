package com.campus.contest.module.system.service;

import com.campus.contest.module.system.vo.PermissionResourceVO;

import java.util.List;

/**
 * 权限资源树（sys_menu）只读查询。
 *
 * sys_menu 是目录、菜单和按钮/API 权限的统一资源树，perms 即 @PreAuthorize 使用的权限编码。
 */
public interface PermissionResourceService {

    /**
     * EP-076 全量权限资源树，按 sort_no、id 升序返回，供管理员勾选授权。
     */
    List<PermissionResourceVO> menuTree();

    /**
     * EP-008 当前用户菜单树：按用户角色合并 sys_role_menu，返回目录、菜单、按钮三级树。
     */
    List<PermissionResourceVO> currentUserMenuTree(Long userId);
}
