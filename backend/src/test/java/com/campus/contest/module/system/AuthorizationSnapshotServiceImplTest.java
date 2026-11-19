package com.campus.contest.module.system;

import com.campus.contest.common.BizException;
import com.campus.contest.common.ErrorCode;
import com.campus.contest.module.system.dto.AuthorizationSnapshot;
import com.campus.contest.module.system.entity.SysMenuDO;
import com.campus.contest.module.system.entity.SysRoleDO;
import com.campus.contest.module.system.entity.SysRoleMenuDO;
import com.campus.contest.module.system.entity.SysUserDO;
import com.campus.contest.module.system.entity.SysUserRoleDO;
import com.campus.contest.module.system.mapper.SysMenuMapper;
import com.campus.contest.module.system.mapper.SysRoleMapper;
import com.campus.contest.module.system.mapper.SysRoleMenuMapper;
import com.campus.contest.module.system.mapper.SysUserMapper;
import com.campus.contest.module.system.mapper.SysUserRoleMapper;
import com.campus.contest.module.system.service.impl.AuthorizationSnapshotServiceImpl;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.LocalDateTime;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyCollection;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class AuthorizationSnapshotServiceImplTest {

    @Mock
    private SysUserMapper userMapper;
    @Mock
    private SysRoleMapper roleMapper;
    @Mock
    private SysUserRoleMapper userRoleMapper;
    @Mock
    private SysMenuMapper menuMapper;
    @Mock
    private SysRoleMenuMapper roleMenuMapper;

    private AuthorizationSnapshotServiceImpl service;

    @BeforeEach
    void setUp() {
        service = new AuthorizationSnapshotServiceImpl(
                userMapper, roleMapper, userRoleMapper, menuMapper, roleMenuMapper
        );
    }

    @Test
    void loadsRolesPermissionsAndScopes() {
        SysUserDO user = new SysUserDO();
        user.setId(1L);
        user.setUsername("student_a");
        user.setStatus(1);

        SysRoleDO role = new SysRoleDO();
        role.setId(10L);
        role.setRoleCode("STUDENT");
        role.setDataScope("SELF");

        SysUserRoleDO userRole = new SysUserRoleDO();
        userRole.setUserId(1L);
        userRole.setRoleId(10L);
        userRole.setStatus(1);
        userRole.setDeleted(0);

        SysRoleMenuDO roleMenu = new SysRoleMenuDO();
        roleMenu.setRoleId(10L);
        roleMenu.setMenuId(20L);

        SysMenuDO menu = new SysMenuDO();
        menu.setId(20L);
        menu.setPerms("file:download");
        menu.setStatus(1);

        when(userMapper.selectById(1L)).thenReturn(user);
        when(userRoleMapper.selectList(any())).thenReturn(List.of(userRole));
        when(roleMapper.selectBatchIds(anyCollection())).thenReturn(List.of(role));
        when(roleMenuMapper.selectList(any())).thenReturn(List.of(roleMenu));
        when(menuMapper.selectBatchIds(anyCollection())).thenReturn(List.of(menu));

        AuthorizationSnapshot snapshot = service.loadRequired(1L);

        assertThat(snapshot.roleCodes()).containsExactly("STUDENT");
        assertThat(snapshot.permissionCodes()).containsExactly("file:download");
        assertThat(snapshot.dataScopes()).containsExactly("SELF");
    }

    @Test
    void ignoresDisabledExpiredDeletedAndNotYetEffectiveRoleGrants() {
        SysUserDO user = new SysUserDO();
        user.setId(1L);
        user.setUsername("school_admin_a");
        user.setStatus(1);

        SysRoleDO role = new SysRoleDO();
        role.setId(10L);
        role.setRoleCode("SCHOOL_ADMIN");
        role.setDataScope("ORGANIZATION");

        SysUserRoleDO active = userRole(10L, 1, 0, null, LocalDateTime.now().plusDays(1));
        SysUserRoleDO disabled = userRole(20L, 0, 0, null, null);
        SysUserRoleDO deleted = userRole(30L, 1, 1, null, null);
        SysUserRoleDO expired = userRole(40L, 1, 0, null, LocalDateTime.now().minusSeconds(1));
        SysUserRoleDO future = userRole(50L, 1, 0, LocalDateTime.now().plusDays(1), null);

        SysRoleMenuDO roleMenu = new SysRoleMenuDO();
        roleMenu.setRoleId(10L);
        roleMenu.setMenuId(20L);

        SysMenuDO menu = new SysMenuDO();
        menu.setId(20L);
        menu.setPerms("school:affiliation:list");
        menu.setStatus(1);

        when(userMapper.selectById(1L)).thenReturn(user);
        when(userRoleMapper.selectList(any())).thenReturn(
                List.of(active, disabled, deleted, expired, future)
        );
        when(roleMapper.selectBatchIds(anyCollection())).thenAnswer(invocation -> {
            Iterable<Long> requestedRoleIds = invocation.getArgument(0);
            assertThat(requestedRoleIds).containsExactly(10L);
            return List.of(role);
        });
        when(roleMenuMapper.selectList(any())).thenReturn(List.of(roleMenu));
        when(menuMapper.selectBatchIds(anyCollection())).thenReturn(List.of(menu));

        AuthorizationSnapshot snapshot = service.loadRequired(1L);

        assertThat(snapshot.roleCodes()).containsExactly("SCHOOL_ADMIN");
        assertThat(snapshot.permissionCodes()).containsExactly("school:affiliation:list");
        assertThat(snapshot.dataScopes()).containsExactly("ORGANIZATION");
    }

    @Test
    void rejectsMissingOrDisabledUserWithExistingUnauthorizedCode() {
        when(userMapper.selectById(99L)).thenReturn(null);

        assertThatThrownBy(() -> service.loadRequired(99L))
                .isInstanceOfSatisfying(BizException.class,
                        error -> assertThat(error.getCode())
                                .isEqualTo(ErrorCode.UNAUTHENTICATED.getCode()));
    }

    private SysUserRoleDO userRole(
            Long roleId,
            Integer status,
            Integer deleted,
            LocalDateTime validFrom,
            LocalDateTime validTo
    ) {
        SysUserRoleDO userRole = new SysUserRoleDO();
        userRole.setUserId(1L);
        userRole.setRoleId(roleId);
        userRole.setStatus(status);
        userRole.setDeleted(deleted);
        userRole.setValidFrom(validFrom);
        userRole.setValidTo(validTo);
        return userRole;
    }
}
