package com.campus.contest.module.system.service.impl;

import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.campus.contest.module.system.entity.SysMenuDO;
import com.campus.contest.module.system.entity.SysRoleMenuDO;
import com.campus.contest.module.system.entity.SysUserRoleDO;
import com.campus.contest.module.system.mapper.SysMenuMapper;
import com.campus.contest.module.system.mapper.SysRoleMenuMapper;
import com.campus.contest.module.system.mapper.SysUserRoleMapper;
import com.campus.contest.module.system.service.PermissionResourceService;
import com.campus.contest.module.system.vo.PermissionResourceVO;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * 权限资源树查询实现。
 *
 * 树结构完全来自 sys_menu，不在代码里复制一份权限清单；perms 即权限编码的唯一事实源。
 */
@Service
@RequiredArgsConstructor
public class PermissionResourceServiceImpl implements PermissionResourceService {

    /** sys_menu.status：1 启用，0 停用 */
    private static final int RESOURCE_ENABLED = 1;

    private final SysMenuMapper menuMapper;
    private final SysUserRoleMapper userRoleMapper;
    private final SysRoleMenuMapper roleMenuMapper;

    @Override
    public List<PermissionResourceVO> menuTree() {
        return buildTree(loadAllMenus());
    }

    @Override
    public List<PermissionResourceVO> currentUserMenuTree(Long userId) {
        if (userId == null) {
            return List.of();
        }
        Set<Long> roleIds = userRoleMapper.selectList(
                        Wrappers.<SysUserRoleDO>lambdaQuery().eq(SysUserRoleDO::getUserId, userId)
                ).stream()
                .map(SysUserRoleDO::getRoleId)
                .filter(Objects::nonNull)
                .collect(Collectors.toCollection(LinkedHashSet::new));
        if (roleIds.isEmpty()) {
            return List.of();
        }

        Set<Long> grantedMenuIds = roleMenuMapper.selectList(
                        Wrappers.<SysRoleMenuDO>lambdaQuery().in(SysRoleMenuDO::getRoleId, roleIds)
                ).stream()
                .map(SysRoleMenuDO::getMenuId)
                .filter(Objects::nonNull)
                .collect(Collectors.toCollection(LinkedHashSet::new));
        if (grantedMenuIds.isEmpty()) {
            return List.of();
        }

        List<SysMenuDO> allMenus = loadAllMenus();
        Map<Long, SysMenuDO> byId = new LinkedHashMap<>();
        allMenus.forEach(menu -> byId.put(menu.getId(), menu));

        // 授权节点可能只包含按钮，必须把祖先目录和菜单一起补齐，避免出现孤立的根节点
        Set<Long> closure = new LinkedHashSet<>();
        for (Long grantedId : grantedMenuIds) {
            SysMenuDO current = byId.get(grantedId);
            while (current != null && closure.add(current.getId())) {
                Long parentId = current.getParentId();
                current = parentId == null ? null : byId.get(parentId);
            }
        }

        List<SysMenuDO> visibleMenus = allMenus.stream()
                .filter(menu -> closure.contains(menu.getId()))
                .filter(menu -> Objects.equals(RESOURCE_ENABLED, menu.getStatus()))
                .toList();
        return buildTree(visibleMenus);
    }

    private List<SysMenuDO> loadAllMenus() {
        return menuMapper.selectList(
                Wrappers.<SysMenuDO>lambdaQuery()
                        .orderByAsc(SysMenuDO::getSortNo)
                        .orderByAsc(SysMenuDO::getId)
        );
    }

    /**
     * 按 parent_id 组装三级树；父节点不在集合内的节点提升为根节点，保证输出完整。
     */
    private List<PermissionResourceVO> buildTree(List<SysMenuDO> menus) {
        if (menus == null || menus.isEmpty()) {
            return List.of();
        }
        List<SysMenuDO> ordered = new ArrayList<>(menus);
        ordered.sort((left, right) -> {
            int bySort = Integer.compare(
                    left.getSortNo() == null ? 0 : left.getSortNo(),
                    right.getSortNo() == null ? 0 : right.getSortNo()
            );
            if (bySort != 0) {
                return bySort;
            }
            return Long.compare(
                    left.getId() == null ? 0L : left.getId(),
                    right.getId() == null ? 0L : right.getId()
            );
        });

        Map<Long, PermissionResourceVO> nodes = new LinkedHashMap<>();
        ordered.forEach(menu -> nodes.put(menu.getId(), PermissionResourceVO.from(menu)));

        List<PermissionResourceVO> roots = new ArrayList<>();
        for (SysMenuDO menu : ordered) {
            PermissionResourceVO node = nodes.get(menu.getId());
            Long parentId = menu.getParentId();
            PermissionResourceVO parent = parentId == null ? null : nodes.get(parentId);
            if (parent == null || parent == node) {
                roots.add(node);
            } else {
                parent.getChildren().add(node);
            }
        }
        return roots;
    }
}
