package com.campus.contest.module.system;

import com.campus.contest.common.BizException;
import com.campus.contest.common.ErrorCode;
import com.campus.contest.module.system.dto.RoleCreateRequest;
import com.campus.contest.module.system.dto.RolePermissionUpdateRequest;
import com.campus.contest.module.system.dto.RoleUpdateRequest;
import com.campus.contest.module.system.entity.SysMenuDO;
import com.campus.contest.module.system.entity.SysRoleDO;
import com.campus.contest.module.system.entity.SysRoleMenuDO;
import com.campus.contest.module.system.entity.SysUserRoleDO;
import com.campus.contest.module.system.mapper.SysMenuMapper;
import com.campus.contest.module.system.mapper.SysRoleMapper;
import com.campus.contest.module.system.mapper.SysRoleMenuMapper;
import com.campus.contest.module.system.mapper.SysUserRoleMapper;
import com.campus.contest.module.system.service.PermissionResourceService;
import com.campus.contest.module.system.service.RbacSideEffectService;
import com.campus.contest.module.system.service.impl.RoleServiceImpl;
import com.campus.contest.module.system.vo.IdVersionVO;
import com.campus.contest.module.system.vo.RolePermissionVO;
import com.campus.contest.module.system.vo.RoleVO;
import com.campus.contest.module.system.vo.RoleVersionVO;
import com.campus.contest.security.CurrentUserProvider;
import com.campus.contest.TestInfrastructure;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.atLeastOnce;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * 角色管理、角色授权的业务规则与副作用单元测试。
 */
@ExtendWith(MockitoExtension.class)
class RoleServiceImplTest {

    private static final Long OPERATOR_ID = 1L;

    @Mock
    private SysRoleMapper roleMapper;
    @Mock
    private SysRoleMenuMapper roleMenuMapper;
    @Mock
    private SysUserRoleMapper userRoleMapper;
    @Mock
    private SysMenuMapper menuMapper;
    @Mock
    private PermissionResourceService permissionResourceService;
    @Mock
    private RbacSideEffectService sideEffectService;
    @Mock
    private CurrentUserProvider currentUserProvider;

    private final ObjectMapper objectMapper = new ObjectMapper();

    private RoleServiceImpl roleService;

    @BeforeEach
    void setUp() {
        roleService = new RoleServiceImpl(
                roleMapper,
                roleMenuMapper,
                userRoleMapper,
                menuMapper,
                permissionResourceService,
                TestInfrastructure.passthroughIdempotencyService(),
                sideEffectService,
                currentUserProvider,
                TestInfrastructure.transactionTemplate(),
                objectMapper
        );
        lenient().when(currentUserProvider.requireUserId()).thenReturn(OPERATOR_ID);
    }

    @Test
    @DisplayName("创建角色成功后返回角色信息，并写入创建审计")
    void createRoleReturnsVoAndWritesAudit() {
        when(roleMapper.selectCount(any())).thenReturn(0L);
        when(roleMapper.insert(any(SysRoleDO.class))).thenAnswer(invocation -> {
            SysRoleDO entity = invocation.getArgument(0);
            entity.setId(50L);
            entity.setVersion(0);
            return 1;
        });

        RoleCreateRequest request = new RoleCreateRequest();
        request.setRoleCode("TEACHER_ASSIST");
        request.setRoleName("助教");
        request.setDataScope("self");

        RoleVO role = roleService.createRole(request, "role-create-key");

        assertThat(role.getId()).isEqualTo("50");
        assertThat(role.getRoleCode()).isEqualTo("TEACHER_ASSIST");
        // data_scope 统一大写落库，避免同一枚举出现两种写法
        assertThat(role.getDataScope()).isEqualTo("SELF");
        verify(sideEffectService).audit(eq(OPERATOR_ID), eq("ROLE_CREATE"), anyString(), eq(0));
    }

    @Test
    @DisplayName("角色编码重复创建返回 409 DUPLICATE_RELATION")
    void createRoleRejectsDuplicateCode() {
        when(roleMapper.selectCount(any())).thenReturn(1L);

        RoleCreateRequest request = new RoleCreateRequest();
        request.setRoleCode("STUDENT");
        request.setRoleName("学生");
        request.setDataScope("SELF");

        assertThatThrownBy(() -> roleService.createRole(request, "role-create-key"))
                .isInstanceOfSatisfying(BizException.class, error ->
                        assertThat(error.getCode()).isEqualTo(ErrorCode.DUPLICATE_RELATION.getCode()));
        verify(roleMapper, never()).insert(any(SysRoleDO.class));
    }

    @Test
    @DisplayName("角色编码格式非法返回 400，不能落库")
    void createRoleRejectsInvalidCode() {
        RoleCreateRequest request = new RoleCreateRequest();
        request.setRoleCode("teacher");
        request.setRoleName("助教");
        request.setDataScope("SELF");

        assertThatThrownBy(() -> roleService.createRole(request, "role-create-key"))
                .isInstanceOfSatisfying(BizException.class, error ->
                        assertThat(error.getCode()).isEqualTo(ErrorCode.INVALID_PARAM.getCode()));
    }

    @Test
    @DisplayName("缺少 Idempotency-Key 的创建请求返回 400")
    void createRoleRequiresIdempotencyKey() {
        RoleCreateRequest request = new RoleCreateRequest();
        request.setRoleCode("REVIEWER_ASSIST");
        request.setRoleName("评审助理");
        request.setDataScope("ASSIGNED");

        assertThatThrownBy(() -> roleService.createRole(request, "  "))
                .isInstanceOfSatisfying(BizException.class, error ->
                        assertThat(error.getCode()).isEqualTo(ErrorCode.INVALID_PARAM.getCode()));
    }

    @Test
    @DisplayName("版本号落后时修改角色返回 409，不写审计也不撤销会话")
    void updateRoleRejectsStaleVersion() {
        when(roleMapper.selectById(20L)).thenReturn(role(20L, "REVIEWER", "评委", "ASSIGNED", 3));
        when(roleMapper.updateById(any(SysRoleDO.class))).thenReturn(0);

        RoleUpdateRequest request = new RoleUpdateRequest();
        request.setRoleName("评委组");
        request.setVersion(1);

        assertThatThrownBy(() -> roleService.updateRole(20L, request, "role-update-key"))
                .isInstanceOfSatisfying(BizException.class, error ->
                        assertThat(error.getCode()).isEqualTo(ErrorCode.BUSINESS_CONFLICT.getCode()));
        verify(sideEffectService, never()).audit(any(), anyString(), anyString(), any());
        verify(sideEffectService, never()).revokeSessionsOfUsers(any(), anyString());
    }

    @Test
    @DisplayName("数据范围变化后返回新版本号，并撤销该角色下所有用户的旧会话")
    void updateRoleRevokesSessionsWhenDataScopeChanges() {
        when(roleMapper.selectById(20L)).thenReturn(role(20L, "REVIEWER", "评委", "ASSIGNED", 3));
        when(roleMapper.updateById(any(SysRoleDO.class))).thenReturn(1);
        when(userRoleMapper.selectList(any())).thenReturn(List.of(userRole(11L, 20L), userRole(12L, 20L)));

        RoleUpdateRequest request = new RoleUpdateRequest();
        request.setDataScope("ALL");
        request.setVersion(3);

        IdVersionVO result = roleService.updateRole(20L, request, "role-update-key");

        assertThat(result.getId()).isEqualTo("20");
        assertThat(result.getVersion()).isEqualTo(4);
        verify(sideEffectService).revokeSessionsOfUsers(
                eq(List.of(11L, 12L)),
                eq("ROLE_UPDATE")
        );
    }

    @Test
    @DisplayName("只改角色名称时不撤销会话，避免无谓打断在线用户")
    void updateRoleKeepsSessionsWhenOnlyNameChanges() {
        when(roleMapper.selectById(20L)).thenReturn(role(20L, "REVIEWER", "评委", "ASSIGNED", 3));
        when(roleMapper.updateById(any(SysRoleDO.class))).thenReturn(1);

        RoleUpdateRequest request = new RoleUpdateRequest();
        request.setRoleName("评委组");
        request.setVersion(3);

        roleService.updateRole(20L, request, "role-update-key");

        verify(sideEffectService, never()).revokeSessionsOfUsers(any(), anyString());
    }

    @Test
    @DisplayName("查询角色权限返回已授权资源 ID 和完整权限资源树")
    void getRolePermissionsReturnsGrantedMenus() {
        when(roleMapper.selectById(20L)).thenReturn(role(20L, "REVIEWER", "评委", "ASSIGNED", 3));
        when(roleMenuMapper.selectList(any())).thenReturn(List.of(roleMenu(20L, 31L), roleMenu(20L, 40L)));
        when(permissionResourceService.menuTree()).thenReturn(List.of());

        RolePermissionVO permissions = roleService.getRolePermissions(20L);

        assertThat(permissions.getRoleId()).isEqualTo("20");
        assertThat(permissions.getVersion()).isEqualTo(3);
        assertThat(permissions.getMenuIds()).containsExactly("31", "40");
    }

    @Test
    @DisplayName("角色不存在时查询权限返回 404")
    void getRolePermissionsRejectsMissingRole() {
        when(roleMapper.selectById(99L)).thenReturn(null);

        assertThatThrownBy(() -> roleService.getRolePermissions(99L))
                .isInstanceOfSatisfying(BizException.class, error ->
                        assertThat(error.getCode()).isEqualTo(ErrorCode.RESOURCE_NOT_FOUND.getCode()));
    }

    @Test
    @DisplayName("替换角色权限成功后版本号加一，并撤销拥有该角色的用户会话")
    void replaceRolePermissionsBumpsVersionAndRevokesSessions() {
        when(roleMapper.selectById(20L)).thenReturn(role(20L, "REVIEWER", "评委", "ASSIGNED", 3));
        when(menuMapper.selectBatchIds(any())).thenReturn(List.of(menu(31L), menu(40L)));
        when(roleMenuMapper.selectList(any())).thenReturn(List.of(roleMenu(20L, 31L)));
        when(roleMapper.updateById(any(SysRoleDO.class))).thenReturn(1);
        when(userRoleMapper.selectList(any())).thenReturn(List.of(userRole(11L, 20L)));

        RolePermissionUpdateRequest request = new RolePermissionUpdateRequest();
        request.setMenuIds(List.of("31", "40", "40"));
        request.setVersion(3);

        RoleVersionVO result = roleService.replaceRolePermissions(20L, request, "role-auth-key");

        assertThat(result.getRoleId()).isEqualTo("20");
        assertThat(result.getVersion()).isEqualTo(4);
        // 重复的 menu_id 去重后只写两条关联
        verify(roleMenuMapper, times(2)).insert(any(SysRoleMenuDO.class));
        verify(roleMenuMapper, times(1)).delete(any());
        verify(sideEffectService).revokeSessionsOfUsers(eq(List.of(11L)), eq("ROLE_AUTHORIZE"));

        ArgumentCaptor<SysRoleMenuDO> captor = ArgumentCaptor.forClass(SysRoleMenuDO.class);
        verify(roleMenuMapper, atLeastOnce()).insert(captor.capture());
        assertThat(captor.getAllValues()).allSatisfy(relation ->
                assertThat(relation.getRoleId()).isEqualTo(20L));
    }

    @Test
    @DisplayName("权限资源不存在时授权请求返回 400，且不落库")
    void replaceRolePermissionsRejectsUnknownMenu() {
        when(roleMapper.selectById(20L)).thenReturn(role(20L, "REVIEWER", "评委", "ASSIGNED", 3));
        when(menuMapper.selectBatchIds(any())).thenReturn(List.of(menu(31L)));

        RolePermissionUpdateRequest request = new RolePermissionUpdateRequest();
        request.setMenuIds(List.of("31", "99"));
        request.setVersion(3);

        assertThatThrownBy(() -> roleService.replaceRolePermissions(20L, request, "role-auth-key"))
                .isInstanceOfSatisfying(BizException.class, error ->
                        assertThat(error.getCode()).isEqualTo(ErrorCode.INVALID_PARAM.getCode()));
        verify(roleMenuMapper, never()).delete(any());
    }

    @Test
    @DisplayName("授权请求版本号落后时返回 409，且不改关联")
    void replaceRolePermissionsRejectsStaleVersion() {
        when(roleMapper.selectById(20L)).thenReturn(role(20L, "REVIEWER", "评委", "ASSIGNED", 3));
        when(menuMapper.selectBatchIds(any())).thenReturn(List.of(menu(31L)));
        when(roleMenuMapper.selectList(any())).thenReturn(List.of(roleMenu(20L, 31L)));
        when(roleMapper.updateById(any(SysRoleDO.class))).thenReturn(0);

        RolePermissionUpdateRequest request = new RolePermissionUpdateRequest();
        request.setMenuIds(List.of("31"));
        request.setVersion(1);

        assertThatThrownBy(() -> roleService.replaceRolePermissions(20L, request, "role-auth-key"))
                .isInstanceOfSatisfying(BizException.class, error ->
                        assertThat(error.getCode()).isEqualTo(ErrorCode.BUSINESS_CONFLICT.getCode()));
        verify(roleMenuMapper, never()).delete(any());
        verify(sideEffectService, never()).revokeSessionsOfUsers(any(), anyString());
    }

    // ------------------------------------------------------------------
    // 测试数据构造
    // ------------------------------------------------------------------

    private SysRoleDO role(Long id, String code, String name, String dataScope, Integer version) {
        SysRoleDO role = new SysRoleDO();
        role.setId(id);
        role.setRoleCode(code);
        role.setRoleName(name);
        role.setDataScope(dataScope);
        role.setVersion(version);
        return role;
    }

    private SysRoleMenuDO roleMenu(Long roleId, Long menuId) {
        SysRoleMenuDO relation = new SysRoleMenuDO();
        relation.setRoleId(roleId);
        relation.setMenuId(menuId);
        return relation;
    }

    private SysUserRoleDO userRole(Long userId, Long roleId) {
        SysUserRoleDO relation = new SysUserRoleDO();
        relation.setUserId(userId);
        relation.setRoleId(roleId);
        return relation;
    }

    private SysMenuDO menu(Long id) {
        SysMenuDO menu = new SysMenuDO();
        menu.setId(id);
        menu.setStatus(1);
        return menu;
    }
}
