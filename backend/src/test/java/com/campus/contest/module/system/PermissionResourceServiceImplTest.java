package com.campus.contest.module.system;

import com.campus.contest.module.system.entity.SysMenuDO;
import com.campus.contest.module.system.entity.SysRoleMenuDO;
import com.campus.contest.module.system.entity.SysUserRoleDO;
import com.campus.contest.module.system.mapper.SysMenuMapper;
import com.campus.contest.module.system.mapper.SysRoleMenuMapper;
import com.campus.contest.module.system.mapper.SysUserRoleMapper;
import com.campus.contest.module.system.service.impl.PermissionResourceServiceImpl;
import com.campus.contest.module.system.vo.PermissionResourceVO;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;

/**
 * 权限资源树组装单元测试：目录、菜单、按钮三级结构与权限编码。
 */
@ExtendWith(MockitoExtension.class)
class PermissionResourceServiceImplTest {

    @Mock
    private SysMenuMapper menuMapper;
    @Mock
    private SysUserRoleMapper userRoleMapper;
    @Mock
    private SysRoleMenuMapper roleMenuMapper;

    private PermissionResourceServiceImpl permissionResourceService;

    @BeforeEach
    void setUp() {
        permissionResourceService = new PermissionResourceServiceImpl(
                menuMapper, userRoleMapper, roleMenuMapper
        );
    }

    @Test
    @DisplayName("全量权限资源树按 parent_id 组装三级结构，并按 sort_no 升序")
    void menuTreeBuildsThreeLevels() {
        when(menuMapper.selectList(any())).thenReturn(List.of(
                menu(10L, null, "系统管理", 1, null, null, 1),
                menu(20L, 10L, "用户管理", 2, "/admin/users", "system:user:list", 2),
                menu(30L, 20L, "查看用户", 3, null, "system:user:list", 1),
                menu(40L, null, "文件中心", 2, "/files", "file:download", 3)
        ));

        List<PermissionResourceVO> tree = permissionResourceService.menuTree();

        assertThat(tree).hasSize(2);
        assertThat(tree.get(0).getMenuName()).isEqualTo("系统管理");
        assertThat(tree.get(0).getMenuType()).isEqualTo("DIRECTORY");
        assertThat(tree.get(0).getChildren()).hasSize(1);
        assertThat(tree.get(0).getChildren().get(0).getMenuType()).isEqualTo("MENU");
        assertThat(tree.get(0).getChildren().get(0).getChildren()).hasSize(1);
        assertThat(tree.get(0).getChildren().get(0).getChildren().get(0).getMenuType())
                .isEqualTo("BUTTON");
        assertThat(tree.get(1).getMenuName()).isEqualTo("文件中心");
        assertThat(tree.get(1).getPerms()).isEqualTo("file:download");
    }

    @Test
    @DisplayName("当前用户菜单按角色合并，并补齐被授权节点的父级")
    void currentUserMenuTreeMergesRolesAndKeepsAncestors() {
        when(userRoleMapper.selectList(any())).thenReturn(List.of(
                userRole(2L, 40L), userRole(2L, 41L)
        ));
        when(roleMenuMapper.selectList(any())).thenReturn(List.of(
                roleMenu(40L, 21L), roleMenu(41L, 22L)
        ));
        when(menuMapper.selectList(any())).thenReturn(List.of(
                menu(10L, null, "文件中心", 1, null, null, 1),
                menu(21L, 10L, "我的文件", 2, "/files/mine", "file:download", 1),
                menu(22L, 10L, "上传文件", 3, null, "file:upload", 1)
        ));

        List<PermissionResourceVO> tree = permissionResourceService.currentUserMenuTree(2L);

        assertThat(tree).hasSize(1);
        assertThat(tree.get(0).getMenuName()).isEqualTo("文件中心");
        assertThat(tree.get(0).getChildren()).hasSize(2);
        assertThat(tree.get(0).getChildren().get(0).getPerms()).isEqualTo("file:download");
        assertThat(tree.get(0).getChildren().get(1).getPerms()).isEqualTo("file:upload");
    }

    @Test
    @DisplayName("只授权按钮时必须自动补齐父级目录和菜单，不能出现孤立节点")
    void currentUserMenuTreeKeepsAncestorsOfButtonOnly() {
        when(userRoleMapper.selectList(any())).thenReturn(List.of(userRole(2L, 40L)));
        when(roleMenuMapper.selectList(any())).thenReturn(List.of(roleMenu(40L, 30L)));
        when(menuMapper.selectList(any())).thenReturn(List.of(
                menu(10L, null, "系统管理", 1, null, null, 1),
                menu(20L, 10L, "角色管理", 2, "/admin/roles", "system:role:list", 1),
                menu(30L, 20L, "创建角色", 3, null, "system:role:create", 1)
        ));

        List<PermissionResourceVO> tree = permissionResourceService.currentUserMenuTree(2L);

        assertThat(tree).hasSize(1);
        assertThat(tree.get(0).getId()).isEqualTo("10");
        assertThat(tree.get(0).getChildren()).hasSize(1);
        assertThat(tree.get(0).getChildren().get(0).getChildren().get(0).getPerms())
                .isEqualTo("system:role:create");
    }

    @Test
    @DisplayName("停用的权限资源不出现在当前用户菜单中")
    void currentUserMenuTreeExcludesDisabledResource() {
        when(userRoleMapper.selectList(any())).thenReturn(List.of(userRole(2L, 40L)));
        when(roleMenuMapper.selectList(any())).thenReturn(List.of(roleMenu(40L, 21L)));
        when(menuMapper.selectList(any())).thenReturn(List.of(
                menu(10L, null, "文件中心", 1, null, null, 1),
                menu(21L, 10L, "旧菜单", 2, "/files/old", "file:download", 0)
        ));

        List<PermissionResourceVO> tree = permissionResourceService.currentUserMenuTree(2L);

        assertThat(tree).hasSize(1);
        assertThat(tree.get(0).getChildren()).isEmpty();
    }

    @Test
    @DisplayName("用户没有任何角色时返回空树，不回退成全部菜单")
    void currentUserMenuTreeIsEmptyWithoutRoles() {
        when(userRoleMapper.selectList(any())).thenReturn(List.of());

        List<PermissionResourceVO> tree = permissionResourceService.currentUserMenuTree(9L);

        assertThat(tree).isEmpty();
    }

    private SysMenuDO menu(Long id, Long parentId, String name, Integer type,
                           String path, String perms, Integer status) {
        SysMenuDO menu = new SysMenuDO();
        menu.setId(id);
        menu.setParentId(parentId);
        menu.setMenuName(name);
        menu.setMenuType(type);
        menu.setPath(path);
        menu.setPerms(perms);
        menu.setSortNo(id.intValue());
        menu.setVisible(1);
        menu.setStatus(status);
        return menu;
    }

    private SysUserRoleDO userRole(Long userId, Long roleId) {
        SysUserRoleDO relation = new SysUserRoleDO();
        relation.setUserId(userId);
        relation.setRoleId(roleId);
        return relation;
    }

    private SysRoleMenuDO roleMenu(Long roleId, Long menuId) {
        SysRoleMenuDO relation = new SysRoleMenuDO();
        relation.setRoleId(roleId);
        relation.setMenuId(menuId);
        return relation;
    }
}
