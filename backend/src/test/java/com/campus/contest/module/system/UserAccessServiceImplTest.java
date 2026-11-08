package com.campus.contest.module.system;

import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.campus.contest.TestInfrastructure;
import com.campus.contest.common.BizException;
import com.campus.contest.common.ErrorCode;
import com.campus.contest.module.system.dto.UserAccessUpdateRequest;
import com.campus.contest.module.system.dto.UserPageQuery;
import com.campus.contest.module.system.entity.SysRoleDO;
import com.campus.contest.module.system.entity.SysUserDO;
import com.campus.contest.module.system.entity.SysUserRoleDO;
import com.campus.contest.module.system.mapper.SysRoleMapper;
import com.campus.contest.module.system.mapper.SysUserMapper;
import com.campus.contest.module.system.mapper.SysUserRoleMapper;
import com.campus.contest.module.system.service.RbacSideEffectService;
import com.campus.contest.module.system.service.impl.UserAccessServiceImpl;
import com.campus.contest.module.system.vo.IdVersionVO;
import com.campus.contest.module.system.vo.UserItemVO;
import com.campus.contest.module.system.vo.UserPageVO;
import com.campus.contest.security.CurrentUserProvider;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * 用户列表与用户角色分配的业务规则单元测试。
 */
@ExtendWith(MockitoExtension.class)
class UserAccessServiceImplTest {

    private static final Long OPERATOR_ID = 1L;
    private static final Long TARGET_USER_ID = 7L;

    @Mock
    private SysUserMapper userMapper;
    @Mock
    private SysRoleMapper roleMapper;
    @Mock
    private SysUserRoleMapper userRoleMapper;
    @Mock
    private RbacSideEffectService sideEffectService;
    @Mock
    private CurrentUserProvider currentUserProvider;

    private final ObjectMapper objectMapper = new ObjectMapper();

    private UserAccessServiceImpl userAccessService;

    @BeforeEach
    void setUp() {
        userAccessService = new UserAccessServiceImpl(
                userMapper,
                roleMapper,
                userRoleMapper,
                TestInfrastructure.passthroughIdempotencyService(),
                sideEffectService,
                currentUserProvider,
                TestInfrastructure.transactionTemplate(),
                objectMapper
        );
        lenient().when(currentUserProvider.requireUserId()).thenReturn(OPERATOR_ID);
    }

    @Test
    @DisplayName("用户列表按角色推导 user_type，并计算 has_more")
    void pageUsersDerivesUserTypeAndHasMore() {
        SysUserDO admin = user(1L, "admin01", 1);
        SysUserDO student = user(2L, "student01", 1);
        IPage<SysUserDO> page = new Page<>(1, 2);
        page.setRecords(List.of(admin, student));
        page.setTotal(3);

        when(userMapper.selectPage(any(), any())).thenReturn(page);
        when(userRoleMapper.selectList(any())).thenReturn(List.of(
                userRole(1L, 10L), userRole(2L, 40L)
        ));
        when(roleMapper.selectBatchIds(any())).thenReturn(List.of(
                role(10L, "ADMIN"), role(40L, "STUDENT")
        ));

        UserPageVO result = userAccessService.pageUsers(query(1, 2, null, null, null));

        assertThat(result.getItems()).hasSize(2);
        assertThat(result.getItems().get(0).getUserType()).isEqualTo("ADMIN");
        assertThat(result.getItems().get(1).getUserType()).isEqualTo("STUDENT");
        assertThat(result.getTotal()).isEqualTo(3L);
        assertThat(result.getHasMore()).isTrue();
    }

    @Test
    @DisplayName("按 user_type 筛选时只返回拥有该角色的用户")
    void pageUsersFiltersByUserType() {
        when(roleMapper.selectOne(any())).thenReturn(role(40L, "STUDENT"));
        when(userRoleMapper.selectList(any())).thenReturn(List.of(userRole(2L, 40L), userRole(3L, 40L)));

        IPage<SysUserDO> page = new Page<>(1, 20);
        page.setRecords(List.of(user(2L, "student01", 1)));
        page.setTotal(1);
        when(userMapper.selectPage(any(), any())).thenReturn(page);
        when(roleMapper.selectBatchIds(any())).thenReturn(List.of(role(40L, "STUDENT")));

        UserPageVO result = userAccessService.pageUsers(query(1, 20, null, "student", null));

        assertThat(result.getItems()).hasSize(1);
        assertThat(result.getItems().get(0).getRoleCodes()).containsExactly("STUDENT");
    }

    @Test
    @DisplayName("user_type 没有对应用户时返回空分页，不报错")
    void pageUsersReturnsEmptyWhenNoUserMatchesType() {
        when(roleMapper.selectOne(any())).thenReturn(null);

        UserPageVO result = userAccessService.pageUsers(query(1, 20, null, "TEACHER", null));

        assertThat(result.getItems()).isEmpty();
        assertThat(result.getTotal()).isZero();
    }

    @Test
    @DisplayName("size 超出 1-100 返回 400")
    void pageUsersRejectsInvalidSize() {
        assertThatThrownBy(() -> userAccessService.pageUsers(query(1, 101, null, null, null)))
                .isInstanceOfSatisfying(BizException.class, error ->
                        assertThat(error.getCode()).isEqualTo(ErrorCode.INVALID_PARAM.getCode()));
    }

    @Test
    @DisplayName("sort 只接受白名单字段，未知字段返回 400")
    void pageUsersRejectsUnknownSort() {
        assertThatThrownBy(() -> userAccessService.pageUsers(query(1, 20, "role_code", null, null)))
                .isInstanceOfSatisfying(BizException.class, error ->
                        assertThat(error.getCode()).isEqualTo(ErrorCode.INVALID_PARAM.getCode()));
    }

    @Test
    @DisplayName("修改用户角色成功后版本号加一，并撤销该用户旧会话")
    void updateUserAccessRevokesSessionsWhenRolesChange() {
        when(userMapper.selectById(TARGET_USER_ID)).thenReturn(user(TARGET_USER_ID, "student01", 1));
        when(userRoleMapper.selectList(any())).thenReturn(List.of(userRole(TARGET_USER_ID, 40L)));
        when(roleMapper.selectBatchIds(any())).thenReturn(List.of(role(40L, "STUDENT")));
        when(roleMapper.selectList(any())).thenReturn(List.of(role(31L, "TEACHER")));
        when(userMapper.updateById(any(SysUserDO.class))).thenReturn(1);

        UserAccessUpdateRequest request = new UserAccessUpdateRequest();
        request.setRoleCodes(List.of("TEACHER"));
        request.setVersion(0);

        IdVersionVO result = userAccessService.updateUserAccess(TARGET_USER_ID, request, "user-access-key");

        assertThat(result.getId()).isEqualTo("7");
        assertThat(result.getVersion()).isEqualTo(1);
        verify(userRoleMapper).delete(any());
        verify(userRoleMapper).insert(any(SysUserRoleDO.class));
        verify(sideEffectService).revokeSessionsOfUsers(eq(List.of(TARGET_USER_ID)), eq("USER_ACCESS_UPDATE"));
        verify(sideEffectService).audit(eq(OPERATOR_ID), eq("USER_ACCESS_UPDATE"), anyString(), eq(0));
    }

    @Test
    @DisplayName("只改状态为禁用同样撤销会话，防止被封禁用户继续使用旧令牌")
    void updateUserAccessRevokesSessionsWhenStatusChanges() {
        when(userMapper.selectById(TARGET_USER_ID)).thenReturn(user(TARGET_USER_ID, "student01", 1));
        when(userMapper.updateById(any(SysUserDO.class))).thenReturn(1);

        UserAccessUpdateRequest request = new UserAccessUpdateRequest();
        request.setStatus(0);
        request.setVersion(0);

        userAccessService.updateUserAccess(TARGET_USER_ID, request, "user-access-key");

        verify(sideEffectService).revokeSessionsOfUsers(eq(List.of(TARGET_USER_ID)), eq("USER_ACCESS_UPDATE"));
    }

    @Test
    @DisplayName("版本号落后时返回 409，不改角色也不撤销会话")
    void updateUserAccessRejectsStaleVersion() {
        when(userMapper.selectById(TARGET_USER_ID)).thenReturn(user(TARGET_USER_ID, "student01", 1));
        when(userRoleMapper.selectList(any())).thenReturn(List.of());
        when(roleMapper.selectList(any())).thenReturn(List.of(role(31L, "TEACHER")));
        when(userMapper.updateById(any(SysUserDO.class))).thenReturn(0);

        UserAccessUpdateRequest request = new UserAccessUpdateRequest();
        request.setRoleCodes(List.of("TEACHER"));
        request.setVersion(0);

        assertThatThrownBy(() -> userAccessService.updateUserAccess(TARGET_USER_ID, request, "user-access-key"))
                .isInstanceOfSatisfying(BizException.class, error ->
                        assertThat(error.getCode()).isEqualTo(ErrorCode.BUSINESS_CONFLICT.getCode()));
        verify(userRoleMapper, never()).delete(any());
        verify(sideEffectService, never()).revokeSessionsOfUsers(any(), anyString());
    }

    @Test
    @DisplayName("角色编码不存在返回 400，不允许写入未知角色")
    void updateUserAccessRejectsUnknownRole() {
        when(userMapper.selectById(TARGET_USER_ID)).thenReturn(user(TARGET_USER_ID, "student01", 1));
        when(userRoleMapper.selectList(any())).thenReturn(List.of());
        when(roleMapper.selectList(any())).thenReturn(List.of(role(31L, "TEACHER")));

        UserAccessUpdateRequest request = new UserAccessUpdateRequest();
        request.setRoleCodes(List.of("TEACHER", "HACKER"));
        request.setVersion(0);

        assertThatThrownBy(() -> userAccessService.updateUserAccess(TARGET_USER_ID, request, "user-access-key"))
                .isInstanceOfSatisfying(BizException.class, error ->
                        assertThat(error.getCode()).isEqualTo(ErrorCode.INVALID_PARAM.getCode()));
    }

    @Test
    @DisplayName("role_codes 为空数组返回 400，避免把用户变成没有任何权限的账号")
    void updateUserAccessRejectsEmptyRoles() {
        UserAccessUpdateRequest request = new UserAccessUpdateRequest();
        request.setRoleCodes(List.of());
        request.setVersion(0);

        assertThatThrownBy(() -> userAccessService.updateUserAccess(TARGET_USER_ID, request, "user-access-key"))
                .isInstanceOfSatisfying(BizException.class, error ->
                        assertThat(error.getCode()).isEqualTo(ErrorCode.INVALID_PARAM.getCode()));
    }

    @Test
    @DisplayName("status 只能为 0 或 1")
    void updateUserAccessRejectsInvalidStatus() {
        UserAccessUpdateRequest request = new UserAccessUpdateRequest();
        request.setStatus(2);
        request.setVersion(0);

        assertThatThrownBy(() -> userAccessService.updateUserAccess(TARGET_USER_ID, request, "user-access-key"))
                .isInstanceOfSatisfying(BizException.class, error ->
                        assertThat(error.getCode()).isEqualTo(ErrorCode.INVALID_PARAM.getCode()));
    }

    @Test
    @DisplayName("version 缺失返回 400")
    void updateUserAccessRequiresVersion() {
        UserAccessUpdateRequest request = new UserAccessUpdateRequest();
        request.setStatus(1);

        assertThatThrownBy(() -> userAccessService.updateUserAccess(TARGET_USER_ID, request, "user-access-key"))
                .isInstanceOfSatisfying(BizException.class, error ->
                        assertThat(error.getCode()).isEqualTo(ErrorCode.INVALID_PARAM.getCode()));
    }

    // ------------------------------------------------------------------
    // 测试数据构造
    // ------------------------------------------------------------------

    private UserPageQuery query(Integer page, Integer size, String sort, String userType, Integer status) {
        UserPageQuery query = new UserPageQuery();
        query.setPage(page);
        query.setSize(size);
        query.setSort(sort);
        query.setUserType(userType);
        query.setStatus(status);
        return query;
    }

    private SysUserDO user(Long id, String username, Integer status) {
        SysUserDO user = new SysUserDO();
        user.setId(id);
        user.setUsername(username);
        user.setRealName(username);
        user.setStatus(status);
        user.setVersion(0);
        return user;
    }

    private SysRoleDO role(Long id, String code) {
        SysRoleDO role = new SysRoleDO();
        role.setId(id);
        role.setRoleCode(code);
        role.setRoleName(code);
        role.setDataScope("SELF");
        return role;
    }

    private SysUserRoleDO userRole(Long userId, Long roleId) {
        SysUserRoleDO relation = new SysUserRoleDO();
        relation.setUserId(userId);
        relation.setRoleId(roleId);
        return relation;
    }
}
