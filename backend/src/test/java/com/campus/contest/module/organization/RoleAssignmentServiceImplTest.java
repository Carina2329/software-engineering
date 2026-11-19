package com.campus.contest.module.organization;

import com.campus.contest.TestInfrastructure;
import com.campus.contest.common.BizException;
import com.campus.contest.common.ErrorCode;
import com.campus.contest.common.idempotency.IdempotencyService;
import com.campus.contest.module.organization.dto.RoleAssignmentCreateRequest;
import com.campus.contest.module.organization.dto.RoleAssignmentUpdateRequest;
import com.campus.contest.module.organization.entity.ContestEditionScopeDO;
import com.campus.contest.module.organization.entity.OrganizationDO;
import com.campus.contest.module.organization.mapper.ContestEditionScopeMapper;
import com.campus.contest.module.organization.mapper.OrganizationMapper;
import com.campus.contest.module.organization.mapper.RoleAssignmentMapper;
import com.campus.contest.module.organization.model.EffectiveGrant;
import com.campus.contest.module.organization.model.ScopeType;
import com.campus.contest.module.organization.service.OrganizationScopeService;
import com.campus.contest.module.organization.service.impl.RoleAssignmentServiceImpl;
import com.campus.contest.module.system.dto.AuditLogCommand;
import com.campus.contest.module.system.entity.SysRoleDO;
import com.campus.contest.module.system.entity.SysUserDO;
import com.campus.contest.module.system.entity.SysUserRoleDO;
import com.campus.contest.module.system.mapper.SysRoleMapper;
import com.campus.contest.module.system.mapper.SysUserMapper;
import com.campus.contest.module.system.service.AuditLogService;
import com.campus.contest.module.system.service.DictionaryQueryService;
import com.campus.contest.module.system.service.RbacSideEffectService;
import com.campus.contest.module.system.vo.IdVersionVO;
import com.campus.contest.security.CurrentUserProvider;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.transaction.TransactionDefinition;
import org.springframework.transaction.support.AbstractPlatformTransactionManager;
import org.springframework.transaction.support.DefaultTransactionStatus;
import org.springframework.transaction.support.TransactionTemplate;

import java.time.LocalDateTime;
import java.util.List;
import java.util.function.Supplier;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class RoleAssignmentServiceImplTest {

    private static final Long OPERATOR_ID = 1L;
    private static final Long TARGET_USER_ID = 5L;

    @Mock
    private RoleAssignmentMapper assignmentMapper;
    @Mock
    private SysRoleMapper roleMapper;
    @Mock
    private SysUserMapper userMapper;
    @Mock
    private OrganizationMapper organizationMapper;
    @Mock
    private ContestEditionScopeMapper contestEditionMapper;
    @Mock
    private DictionaryQueryService dictionaryQueryService;
    @Mock
    private OrganizationScopeService organizationScopeService;
    @Mock
    private IdempotencyService idempotencyService;
    @Mock
    private AuditLogService auditLogService;
    @Mock
    private RbacSideEffectService sideEffectService;
    @Mock
    private CurrentUserProvider currentUserProvider;

    private RoleAssignmentServiceImpl service;

    @BeforeEach
    void setUp() {
        service = new RoleAssignmentServiceImpl(
                assignmentMapper,
                roleMapper,
                userMapper,
                organizationMapper,
                contestEditionMapper,
                dictionaryQueryService,
                organizationScopeService,
                TestInfrastructure.passthroughIdempotencyService(),
                auditLogService,
                sideEffectService,
                currentUserProvider,
                TestInfrastructure.transactionTemplate(),
                new ObjectMapper()
        );
        lenient().when(currentUserProvider.requireUserId()).thenReturn(OPERATOR_ID);
    }

    @Test
    @DisplayName("平台管理员授予学校管理员并撤销目标用户会话")
    void platformAdminAssignsSchoolAdmin() {
        when(userMapper.selectById(TARGET_USER_ID)).thenReturn(user(TARGET_USER_ID, 1));
        when(roleMapper.selectOne(any())).thenReturn(role(20L, "SCHOOL_ADMIN"));
        when(organizationMapper.selectById(10L)).thenReturn(
                organization(10L, "SCHOOL", "510000", 1)
        );
        when(organizationScopeService.loadEffectiveGrants(OPERATOR_ID)).thenReturn(
                List.of(globalAdminGrant())
        );
        when(assignmentMapper.selectCount(any())).thenReturn(0L);
        when(assignmentMapper.insert(any(SysUserRoleDO.class))).thenAnswer(invocation -> {
            SysUserRoleDO assignment = invocation.getArgument(0);
            assignment.setId(100L);
            assignment.setVersion(0);
            return 1;
        });

        IdVersionVO result = service.createAssignment(
                createRequest(TARGET_USER_ID, "SCHOOL_ADMIN", "ORGANIZATION", 10L),
                "create-key"
        );

        assertThat(result.getId()).isEqualTo("100");
        assertThat(result.getVersion()).isZero();
        ArgumentCaptor<SysUserRoleDO> captor = ArgumentCaptor.forClass(SysUserRoleDO.class);
        verify(assignmentMapper).insert(captor.capture());
        assertThat(captor.getValue().getScopeType()).isEqualTo("ORGANIZATION");
        assertThat(captor.getValue().getScopeId()).isEqualTo(10L);
        verify(auditLogService).record(any(AuditLogCommand.class));
        verify(sideEffectService).revokeSessionsOfUsers(
                List.of(TARGET_USER_ID),
                "ROLE_ASSIGNMENT_CREATE"
        );
    }

    @Test
    @DisplayName("学校管理员只能在本校授予赛事负责人")
    void schoolAdminScopeMatrixIsEnforced() {
        when(userMapper.selectById(TARGET_USER_ID)).thenReturn(user(TARGET_USER_ID, 1));
        when(roleMapper.selectOne(any())).thenReturn(role(21L, "EVENT_MANAGER"));
        when(organizationMapper.selectById(10L)).thenReturn(
                organization(10L, "SCHOOL", "510000", 1)
        );
        when(organizationMapper.selectById(20L)).thenReturn(
                organization(20L, "SCHOOL", "510000", 1)
        );
        when(organizationScopeService.loadEffectiveGrants(OPERATOR_ID)).thenReturn(List.of(
                new EffectiveGrant("SCHOOL_ADMIN", ScopeType.ORGANIZATION, 10L)
        ));
        when(assignmentMapper.selectCount(any())).thenReturn(0L);
        when(assignmentMapper.insert(any(SysUserRoleDO.class))).thenAnswer(invocation -> {
            SysUserRoleDO assignment = invocation.getArgument(0);
            assignment.setId(101L);
            return 1;
        });

        assertThatCode(() -> service.createAssignment(
                createRequest(TARGET_USER_ID, "EVENT_MANAGER", "ORGANIZATION", 10L),
                "own-school-key"
        )).doesNotThrowAnyException();
        assertBizCode(
                () -> service.createAssignment(
                        createRequest(TARGET_USER_ID, "EVENT_MANAGER", "ORGANIZATION", 20L),
                        "cross-school-key"
                ),
                ErrorCode.DATA_FORBIDDEN
        );
    }

    @Test
    @DisplayName("省级管理员不能跨省授予范围角色")
    void provinceAdminCannotCrossProvince() {
        when(userMapper.selectById(TARGET_USER_ID)).thenReturn(user(TARGET_USER_ID, 1));
        when(roleMapper.selectOne(any())).thenReturn(role(22L, "REVIEWER"));
        when(dictionaryQueryService.containsEnabledValue("CN_PROVINCE", "430000")).thenReturn(true);
        when(organizationScopeService.loadEffectiveGrants(OPERATOR_ID)).thenReturn(List.of(
                new EffectiveGrant("PROVINCE_ADMIN", ScopeType.PROVINCE, 510000L)
        ));

        assertBizCode(
                () -> service.createAssignment(
                        createRequest(TARGET_USER_ID, "REVIEWER", "PROVINCE", 430000L),
                        "cross-province-key"
                ),
                ErrorCode.DATA_FORBIDDEN
        );
    }

    @Test
    @DisplayName("学校管理员可以给本校赛事授予赛事负责人")
    void schoolAdminAssignsEventManagerToOwnEvent() {
        when(userMapper.selectById(TARGET_USER_ID)).thenReturn(user(TARGET_USER_ID, 1));
        when(roleMapper.selectOne(any())).thenReturn(role(21L, "EVENT_MANAGER"));
        when(contestEditionMapper.selectById(40L)).thenReturn(event(40L, 10L, null));
        when(organizationMapper.selectById(10L)).thenReturn(
                organization(10L, "SCHOOL", "510000", 1)
        );
        when(organizationScopeService.loadEffectiveGrants(OPERATOR_ID)).thenReturn(List.of(
                new EffectiveGrant("SCHOOL_ADMIN", ScopeType.ORGANIZATION, 10L)
        ));
        when(assignmentMapper.selectCount(any())).thenReturn(0L);
        when(assignmentMapper.insert(any(SysUserRoleDO.class))).thenAnswer(invocation -> {
            SysUserRoleDO assignment = invocation.getArgument(0);
            assignment.setId(102L);
            return 1;
        });

        IdVersionVO result = service.createAssignment(
                createRequest(TARGET_USER_ID, "EVENT_MANAGER", "EVENT", 40L),
                "event-scope-key"
        );

        assertThat(result.getId()).isEqualTo("102");
        verify(sideEffectService).revokeSessionsOfUsers(
                List.of(TARGET_USER_ID),
                "ROLE_ASSIGNMENT_CREATE"
        );
    }

    @Test
    @DisplayName("管理员角色必须绑定兼容的范围类型")
    void rejectsIncompatibleRoleAndScope() {
        when(userMapper.selectById(TARGET_USER_ID)).thenReturn(user(TARGET_USER_ID, 1));
        when(roleMapper.selectOne(any())).thenReturn(role(20L, "SCHOOL_ADMIN"));
        when(dictionaryQueryService.containsEnabledValue("CN_PROVINCE", "510000")).thenReturn(true);

        assertBizCode(
                () -> service.createAssignment(
                        createRequest(TARGET_USER_ID, "SCHOOL_ADMIN", "PROVINCE", 510000L),
                        "bad-role-scope-key"
                ),
                ErrorCode.INVALID_PARAM
        );
    }

    @Test
    @DisplayName("不能授予全局 ADMIN、同级管理员或越权角色")
    void rejectsAdminAndOverLevelAssignments() {
        when(userMapper.selectById(TARGET_USER_ID)).thenReturn(user(TARGET_USER_ID, 1));
        assertBizCode(
                () -> service.createAssignment(
                        createRequest(TARGET_USER_ID, "ADMIN", "ORGANIZATION", 10L),
                        "admin-key"
                ),
                ErrorCode.DATA_FORBIDDEN
        );

        when(roleMapper.selectOne(any())).thenReturn(role(20L, "SCHOOL_ADMIN"));
        when(organizationMapper.selectById(10L)).thenReturn(
                organization(10L, "SCHOOL", "510000", 1)
        );
        when(organizationScopeService.loadEffectiveGrants(OPERATOR_ID)).thenReturn(List.of(
                new EffectiveGrant("SCHOOL_ADMIN", ScopeType.ORGANIZATION, 10L)
        ));
        assertBizCode(
                () -> service.createAssignment(
                        createRequest(TARGET_USER_ID, "SCHOOL_ADMIN", "ORGANIZATION", 10L),
                        "same-level-key"
                ),
                ErrorCode.DATA_FORBIDDEN
        );
    }

    @Test
    @DisplayName("伪造组织、省份或赛事 scope_id 返回 404")
    void rejectsFakeScopeIds() {
        when(userMapper.selectById(TARGET_USER_ID)).thenReturn(user(TARGET_USER_ID, 1));
        when(roleMapper.selectOne(any())).thenReturn(role(21L, "EVENT_MANAGER"));
        when(organizationMapper.selectById(99L)).thenReturn(null);
        assertBizCode(
                () -> service.createAssignment(
                        createRequest(TARGET_USER_ID, "EVENT_MANAGER", "ORGANIZATION", 99L),
                        "fake-org-key"
                ),
                ErrorCode.RESOURCE_NOT_FOUND
        );

        when(dictionaryQueryService.containsEnabledValue("CN_PROVINCE", "999999")).thenReturn(false);
        assertBizCode(
                () -> service.createAssignment(
                        createRequest(TARGET_USER_ID, "EVENT_MANAGER", "PROVINCE", 999999L),
                        "fake-province-key"
                ),
                ErrorCode.RESOURCE_NOT_FOUND
        );

        when(contestEditionMapper.selectById(99L)).thenReturn(null);
        assertBizCode(
                () -> service.createAssignment(
                        createRequest(TARGET_USER_ID, "EVENT_MANAGER", "EVENT", 99L),
                        "fake-event-key"
                ),
                ErrorCode.RESOURCE_NOT_FOUND
        );
    }

    @Test
    @DisplayName("过期或失效调用范围不能继续授权")
    void expiredCallerGrantCannotAssign() {
        when(userMapper.selectById(TARGET_USER_ID)).thenReturn(user(TARGET_USER_ID, 1));
        when(roleMapper.selectOne(any())).thenReturn(role(21L, "EVENT_MANAGER"));
        when(organizationMapper.selectById(10L)).thenReturn(
                organization(10L, "SCHOOL", "510000", 1)
        );
        when(organizationScopeService.loadEffectiveGrants(OPERATOR_ID)).thenReturn(List.of());

        assertBizCode(
                () -> service.createAssignment(
                        createRequest(TARGET_USER_ID, "EVENT_MANAGER", "ORGANIZATION", 10L),
                        "expired-scope-key"
                ),
                ErrorCode.DATA_FORBIDDEN
        );
    }

    @Test
    @DisplayName("重复范围授权返回 409")
    void rejectsDuplicateAssignment() {
        when(userMapper.selectById(TARGET_USER_ID)).thenReturn(user(TARGET_USER_ID, 1));
        when(roleMapper.selectOne(any())).thenReturn(role(20L, "SCHOOL_ADMIN"));
        when(organizationMapper.selectById(10L)).thenReturn(
                organization(10L, "SCHOOL", "510000", 1)
        );
        when(organizationScopeService.loadEffectiveGrants(OPERATOR_ID)).thenReturn(
                List.of(globalAdminGrant())
        );
        when(assignmentMapper.selectCount(any())).thenReturn(1L);

        assertBizCode(
                () -> service.createAssignment(
                        createRequest(TARGET_USER_ID, "SCHOOL_ADMIN", "ORGANIZATION", 10L),
                        "duplicate-key"
                ),
                ErrorCode.DUPLICATE_RELATION
        );
        verify(assignmentMapper, never()).insert(any(SysUserRoleDO.class));
    }

    @Test
    @DisplayName("EP-090 缺少 Idempotency-Key 返回 400")
    void createRequiresIdempotencyKey() {
        assertBizCode(
                () -> service.createAssignment(
                        createRequest(TARGET_USER_ID, "EVENT_MANAGER", "ORGANIZATION", 10L),
                        " "
                ),
                ErrorCode.INVALID_PARAM
        );
        verify(userMapper, never()).selectById(any());
    }

    @Test
    @DisplayName("EP-090 使用用户作用域幂等执行器")
    void createUsesIdempotencyService() {
        RoleAssignmentServiceImpl idempotentService = new RoleAssignmentServiceImpl(
                assignmentMapper,
                roleMapper,
                userMapper,
                organizationMapper,
                contestEditionMapper,
                dictionaryQueryService,
                organizationScopeService,
                idempotencyService,
                auditLogService,
                sideEffectService,
                currentUserProvider,
                TestInfrastructure.transactionTemplate(),
                new ObjectMapper()
        );
        when(userMapper.selectById(TARGET_USER_ID)).thenReturn(user(TARGET_USER_ID, 1));
        when(roleMapper.selectOne(any())).thenReturn(role(20L, "SCHOOL_ADMIN"));
        when(organizationMapper.selectById(10L)).thenReturn(
                organization(10L, "SCHOOL", "510000", 1)
        );
        when(organizationScopeService.loadEffectiveGrants(OPERATOR_ID)).thenReturn(
                List.of(globalAdminGrant())
        );
        when(assignmentMapper.selectCount(any())).thenReturn(0L);
        when(assignmentMapper.insert(any(SysUserRoleDO.class))).thenAnswer(invocation -> {
            SysUserRoleDO assignment = invocation.getArgument(0);
            assignment.setId(100L);
            return 1;
        });
        when(idempotencyService.execute(
                any(),
                eq("ROLE_ASSIGNMENT_CREATE"),
                eq("idempotency-key"),
                any(),
                any(),
                eq(IdVersionVO.class),
                any()
        )).thenAnswer(invocation -> {
            Supplier<IdVersionVO> action = invocation.getArgument(6);
            return action.get();
        });

        IdVersionVO result = idempotentService.createAssignment(
                createRequest(TARGET_USER_ID, "SCHOOL_ADMIN", "ORGANIZATION", 10L),
                "idempotency-key"
        );

        assertThat(result.getId()).isEqualTo("100");
        verify(idempotencyService).execute(
                any(),
                eq("ROLE_ASSIGNMENT_CREATE"),
                eq("idempotency-key"),
                any(),
                any(),
                eq(IdVersionVO.class),
                any()
        );
    }

    @Test
    @DisplayName("EP-091 使用真实 version 撤销授权并撤销会话")
    void updatesAssignmentUsingVersion() {
        SysUserRoleDO existing = assignment(
                100L,
                TARGET_USER_ID,
                21L,
                "ORGANIZATION",
                10L,
                1,
                4
        );
        when(assignmentMapper.selectById(100L)).thenReturn(existing);
        when(userMapper.selectById(TARGET_USER_ID)).thenReturn(user(TARGET_USER_ID, 1));
        when(roleMapper.selectById(21L)).thenReturn(role(21L, "EVENT_MANAGER"));
        when(organizationMapper.selectById(10L)).thenReturn(
                organization(10L, "SCHOOL", "510000", 1)
        );
        when(organizationScopeService.loadEffectiveGrantsForExistingTargets(OPERATOR_ID)).thenReturn(
                List.of(globalAdminGrant())
        );
        when(assignmentMapper.updateScopedAssignment(
                eq(100L),
                eq("ORGANIZATION"),
                eq(10L),
                eq(0),
                isNull(),
                eq(4),
                eq(OPERATOR_ID)
        )).thenReturn(1);

        IdVersionVO result = service.updateAssignment(
                100L,
                updateRequest(false, 4),
                "update-key"
        );

        assertThat(result.getVersion()).isEqualTo(5);
        verify(auditLogService).record(any(AuditLogCommand.class));
        verify(sideEffectService).revokeSessionsOfUsers(
                List.of(TARGET_USER_ID),
                "ROLE_ASSIGNMENT_UPDATE"
        );
    }

    @Test
    @DisplayName("EP-091 旧版本返回 409，不写库、不写审计、不撤销会话")
    void updateRejectsStaleVersion() {
        when(assignmentMapper.selectById(100L)).thenReturn(
                assignment(100L, TARGET_USER_ID, 21L, "ORGANIZATION", 10L, 1, 4)
        );

        assertBizCode(
                () -> service.updateAssignment(100L, updateRequest(false, 3), null),
                ErrorCode.BUSINESS_CONFLICT
        );
        verify(assignmentMapper, never()).updateScopedAssignment(
                any(), any(), any(), anyInt(), any(), anyInt(), any()
        );
        verify(auditLogService, never()).record(any());
        verify(sideEffectService, never()).revokeSessionsOfUsers(any(), any());
    }

    @Test
    @DisplayName("组织管理员不能修改其他组织的授权")
    void schoolAdminCannotUpdateAnotherOrganizationAssignment() {
        when(assignmentMapper.selectById(100L)).thenReturn(
                assignment(100L, TARGET_USER_ID, 21L, "ORGANIZATION", 20L, 1, 1)
        );
        when(userMapper.selectById(TARGET_USER_ID)).thenReturn(user(TARGET_USER_ID, 1));
        when(roleMapper.selectById(21L)).thenReturn(role(21L, "EVENT_MANAGER"));
        when(organizationMapper.selectById(20L)).thenReturn(
                organization(20L, "SCHOOL", "510000", 1)
        );
        when(organizationScopeService.loadEffectiveGrantsForExistingTargets(OPERATOR_ID)).thenReturn(List.of(
                new EffectiveGrant("SCHOOL_ADMIN", ScopeType.ORGANIZATION, 10L)
        ));

        assertBizCode(
                () -> service.updateAssignment(100L, updateRequest(false, 1), null),
                ErrorCode.DATA_FORBIDDEN
        );
        verify(assignmentMapper, never()).updateScopedAssignment(
                any(), any(), any(), anyInt(), any(), anyInt(), any()
        );
    }

    @Test
    @DisplayName("组织管理员不能把授权迁移到另一个组织")
    void schoolAdminCannotMoveAssignmentOutsideOwnScope() {
        when(assignmentMapper.selectById(100L)).thenReturn(
                assignment(100L, TARGET_USER_ID, 21L, "ORGANIZATION", 10L, 1, 1)
        );
        when(userMapper.selectById(TARGET_USER_ID)).thenReturn(user(TARGET_USER_ID, 1));
        when(roleMapper.selectById(21L)).thenReturn(role(21L, "EVENT_MANAGER"));
        when(organizationMapper.selectById(10L)).thenReturn(
                organization(10L, "SCHOOL", "510000", 1)
        );
        when(organizationMapper.selectById(20L)).thenReturn(
                organization(20L, "SCHOOL", "510000", 1)
        );
        when(organizationScopeService.loadEffectiveGrantsForExistingTargets(OPERATOR_ID)).thenReturn(List.of(
                new EffectiveGrant("SCHOOL_ADMIN", ScopeType.ORGANIZATION, 10L)
        ));
        when(organizationScopeService.loadEffectiveGrants(OPERATOR_ID)).thenReturn(List.of(
                new EffectiveGrant("SCHOOL_ADMIN", ScopeType.ORGANIZATION, 10L)
        ));

        RoleAssignmentUpdateRequest request = updateRequest(true, 1);
        request.setScopeType("ORGANIZATION");
        request.setScopeId("20");
        assertBizCode(
                () -> service.updateAssignment(100L, request, null),
                ErrorCode.DATA_FORBIDDEN
        );
        verify(assignmentMapper, never()).updateScopedAssignment(
                any(), any(), any(), anyInt(), any(), anyInt(), any()
        );
    }

    @Test
    @DisplayName("EP-091 不能修改 GLOBAL/0 基础角色")
    void updateCannotModifyBaseAssignment() {
        when(assignmentMapper.selectById(100L)).thenReturn(
                assignment(100L, TARGET_USER_ID, 20L, "GLOBAL", 0L, 1, 1)
        );

        assertBizCode(
                () -> service.updateAssignment(100L, updateRequest(false, 1), null),
                ErrorCode.INVALID_PARAM
        );
        verify(assignmentMapper, never()).updateScopedAssignment(
                any(), any(), any(), anyInt(), any(), anyInt(), any()
        );
    }

    @Test
    @DisplayName("审计失败回滚授权事务且不撤销会话")
    void auditFailureRollsBackAssignment() {
        TrackingTransactionManager transactionManager = new TrackingTransactionManager();
        RoleAssignmentServiceImpl transactionalService = new RoleAssignmentServiceImpl(
                assignmentMapper,
                roleMapper,
                userMapper,
                organizationMapper,
                contestEditionMapper,
                dictionaryQueryService,
                organizationScopeService,
                TestInfrastructure.passthroughIdempotencyService(),
                auditLogService,
                sideEffectService,
                currentUserProvider,
                new TransactionTemplate(transactionManager),
                new ObjectMapper()
        );
        when(userMapper.selectById(TARGET_USER_ID)).thenReturn(user(TARGET_USER_ID, 1));
        when(roleMapper.selectOne(any())).thenReturn(role(20L, "SCHOOL_ADMIN"));
        when(organizationMapper.selectById(10L)).thenReturn(
                organization(10L, "SCHOOL", "510000", 1)
        );
        when(organizationScopeService.loadEffectiveGrants(OPERATOR_ID)).thenReturn(
                List.of(globalAdminGrant())
        );
        when(assignmentMapper.selectCount(any())).thenReturn(0L);
        when(assignmentMapper.insert(any(SysUserRoleDO.class))).thenAnswer(invocation -> {
            SysUserRoleDO assignment = invocation.getArgument(0);
            assignment.setId(100L);
            return 1;
        });
        doThrow(new IllegalStateException("audit unavailable"))
                .when(auditLogService).record(any());

        assertThatThrownBy(() -> transactionalService.createAssignment(
                createRequest(TARGET_USER_ID, "SCHOOL_ADMIN", "ORGANIZATION", 10L),
                "rollback-key"
        )).isInstanceOf(IllegalStateException.class);
        assertThat(transactionManager.rolledBack).isTrue();
        assertThat(transactionManager.committed).isFalse();
        verify(sideEffectService, never()).revokeSessionsOfUsers(any(), any());
    }

    @Test
    @DisplayName("会话撤销失败不能返回完全成功")
    void sessionRevocationFailureIsPropagated() {
        TrackingTransactionManager transactionManager = new TrackingTransactionManager();
        RoleAssignmentServiceImpl transactionalService = new RoleAssignmentServiceImpl(
                assignmentMapper,
                roleMapper,
                userMapper,
                organizationMapper,
                contestEditionMapper,
                dictionaryQueryService,
                organizationScopeService,
                TestInfrastructure.passthroughIdempotencyService(),
                auditLogService,
                sideEffectService,
                currentUserProvider,
                new TransactionTemplate(transactionManager),
                new ObjectMapper()
        );
        when(userMapper.selectById(TARGET_USER_ID)).thenReturn(user(TARGET_USER_ID, 1));
        when(roleMapper.selectOne(any())).thenReturn(role(20L, "SCHOOL_ADMIN"));
        when(organizationMapper.selectById(10L)).thenReturn(
                organization(10L, "SCHOOL", "510000", 1)
        );
        when(organizationScopeService.loadEffectiveGrants(OPERATOR_ID)).thenReturn(
                List.of(globalAdminGrant())
        );
        when(assignmentMapper.selectCount(any())).thenReturn(0L);
        when(assignmentMapper.insert(any(SysUserRoleDO.class))).thenAnswer(invocation -> {
            SysUserRoleDO assignment = invocation.getArgument(0);
            assignment.setId(100L);
            return 1;
        });
        doThrow(new BizException(
                ErrorCode.AUTH_IDEMPOTENCY_UNAVAILABLE.getCode(),
                "session revoke unavailable"
        )).when(sideEffectService).revokeSessionsOfUsers(any(), any());

        assertBizCode(
                () -> transactionalService.createAssignment(
                        createRequest(TARGET_USER_ID, "SCHOOL_ADMIN", "ORGANIZATION", 10L),
                        "revoke-key"
                ),
                ErrorCode.AUTH_IDEMPOTENCY_UNAVAILABLE
        );
        assertThat(transactionManager.committed).isTrue();
        assertThat(transactionManager.rolledBack).isFalse();
    }

    @Test
    @DisplayName("组织停用后平台管理员仍可停用原范围授权")
    void platformAdminCanDeactivateAssignmentOnDisabledOrganization() {
        when(assignmentMapper.selectById(100L)).thenReturn(
                assignment(100L, TARGET_USER_ID, 21L, "ORGANIZATION", 10L, 1, 2)
        );
        when(userMapper.selectById(TARGET_USER_ID)).thenReturn(user(TARGET_USER_ID, 1));
        when(roleMapper.selectById(21L)).thenReturn(role(21L, "EVENT_MANAGER"));
        when(organizationMapper.selectById(10L)).thenReturn(
                organization(10L, "SCHOOL", "510000", 0)
        );
        when(organizationScopeService.loadEffectiveGrantsForExistingTargets(OPERATOR_ID)).thenReturn(
                List.of(globalAdminGrant())
        );
        when(assignmentMapper.updateScopedAssignment(
                eq(100L),
                eq("ORGANIZATION"),
                eq(10L),
                eq(0),
                isNull(),
                eq(2),
                eq(OPERATOR_ID)
        )).thenReturn(1);

        IdVersionVO result = service.updateAssignment(
                100L,
                updateRequest(false, 2),
                null
        );

        assertThat(result.getVersion()).isEqualTo(3);
        verify(auditLogService).record(any(AuditLogCommand.class));
        verify(sideEffectService).revokeSessionsOfUsers(
                List.of(TARGET_USER_ID),
                "ROLE_ASSIGNMENT_UPDATE"
        );
    }

    @Test
    @DisplayName("学校管理员在组织停用后仍可撤销本组织旧授权")
    void schoolAdminCanDeactivateOwnAssignmentAfterOrganizationDisabled() {
        when(assignmentMapper.selectById(100L)).thenReturn(
                assignment(100L, TARGET_USER_ID, 21L, "ORGANIZATION", 10L, 1, 2)
        );
        when(userMapper.selectById(TARGET_USER_ID)).thenReturn(user(TARGET_USER_ID, 1));
        when(roleMapper.selectById(21L)).thenReturn(role(21L, "EVENT_MANAGER"));
        when(organizationMapper.selectById(10L)).thenReturn(
                organization(10L, "SCHOOL", "510000", 0)
        );
        when(organizationScopeService.loadEffectiveGrantsForExistingTargets(OPERATOR_ID))
                .thenReturn(List.of(
                        new EffectiveGrant("SCHOOL_ADMIN", ScopeType.ORGANIZATION, 10L)
                ));
        when(assignmentMapper.updateScopedAssignment(
                eq(100L),
                eq("ORGANIZATION"),
                eq(10L),
                eq(0),
                isNull(),
                eq(2),
                eq(OPERATOR_ID)
        )).thenReturn(1);

        IdVersionVO result = service.updateAssignment(
                100L,
                updateRequest(false, 2),
                null
        );

        assertThat(result.getVersion()).isEqualTo(3);
        verify(auditLogService).record(any(AuditLogCommand.class));
        verify(sideEffectService).revokeSessionsOfUsers(
                List.of(TARGET_USER_ID),
                "ROLE_ASSIGNMENT_UPDATE"
        );
    }

    @Test
    @DisplayName("归档赛事上的旧授权仍可停用")
    void archivedEventAssignmentCanBeDeactivated() {
        when(assignmentMapper.selectById(100L)).thenReturn(
                assignment(100L, TARGET_USER_ID, 21L, "EVENT", 40L, 1, 2)
        );
        when(userMapper.selectById(TARGET_USER_ID)).thenReturn(user(TARGET_USER_ID, 1));
        when(roleMapper.selectById(21L)).thenReturn(role(21L, "EVENT_MANAGER"));
        when(contestEditionMapper.selectById(40L)).thenReturn(event(40L, 10L, null, 5));
        when(organizationMapper.selectById(10L)).thenReturn(
                organization(10L, "SCHOOL", "510000", 1)
        );
        when(organizationScopeService.loadEffectiveGrantsForExistingTargets(OPERATOR_ID)).thenReturn(
                List.of(globalAdminGrant())
        );
        when(assignmentMapper.updateScopedAssignment(
                eq(100L),
                eq("EVENT"),
                eq(40L),
                eq(0),
                isNull(),
                eq(2),
                eq(OPERATOR_ID)
        )).thenReturn(1);

        IdVersionVO result = service.updateAssignment(
                100L,
                updateRequest(false, 2),
                null
        );

        assertThat(result.getVersion()).isEqualTo(3);
        verify(auditLogService).record(any(AuditLogCommand.class));
        verify(sideEffectService).revokeSessionsOfUsers(
                List.of(TARGET_USER_ID),
                "ROLE_ASSIGNMENT_UPDATE"
        );
    }

    @Test
    @DisplayName("不能给归档赛事新增授权")
    void cannotCreateAssignmentForArchivedEvent() {
        when(userMapper.selectById(TARGET_USER_ID)).thenReturn(user(TARGET_USER_ID, 1));
        when(roleMapper.selectOne(any())).thenReturn(role(21L, "EVENT_MANAGER"));
        when(contestEditionMapper.selectById(40L)).thenReturn(event(40L, 10L, null, 5));

        assertBizCode(
                () -> service.createAssignment(
                        createRequest(TARGET_USER_ID, "EVENT_MANAGER", "EVENT", 40L),
                        "archived-create-key"
                ),
                ErrorCode.BUSINESS_CONFLICT
        );
        verify(assignmentMapper, never()).insert(any(SysUserRoleDO.class));
    }

    @Test
    @DisplayName("不能把授权迁移到归档赛事")
    void cannotMoveAssignmentToArchivedEvent() {
        when(assignmentMapper.selectById(100L)).thenReturn(
                assignment(100L, TARGET_USER_ID, 21L, "ORGANIZATION", 10L, 1, 1)
        );
        when(userMapper.selectById(TARGET_USER_ID)).thenReturn(user(TARGET_USER_ID, 1));
        when(roleMapper.selectById(21L)).thenReturn(role(21L, "EVENT_MANAGER"));
        when(organizationMapper.selectById(10L)).thenReturn(
                organization(10L, "SCHOOL", "510000", 1)
        );
        when(organizationScopeService.loadEffectiveGrantsForExistingTargets(OPERATOR_ID)).thenReturn(
                List.of(globalAdminGrant())
        );
        when(contestEditionMapper.selectById(40L)).thenReturn(event(40L, 10L, null, 5));
        RoleAssignmentUpdateRequest request = updateRequest(true, 1);
        request.setScopeType("EVENT");
        request.setScopeId("40");

        assertBizCode(
                () -> service.updateAssignment(100L, request, null),
                ErrorCode.BUSINESS_CONFLICT
        );
        verify(assignmentMapper, never()).updateScopedAssignment(
                any(), any(), any(), anyInt(), any(), anyInt(), any()
        );
    }

    @Test
    @DisplayName("不能给主办组织停用的赛事新增授权")
    void cannotCreateAssignmentWhenEventOwnerIsDisabled() {
        when(userMapper.selectById(TARGET_USER_ID)).thenReturn(user(TARGET_USER_ID, 1));
        when(roleMapper.selectOne(any())).thenReturn(role(21L, "EVENT_MANAGER"));
        when(contestEditionMapper.selectById(40L)).thenReturn(event(40L, 10L, null, 0));
        when(organizationMapper.selectById(10L)).thenReturn(
                organization(10L, "SCHOOL", "510000", 0)
        );

        assertBizCode(
                () -> service.createAssignment(
                        createRequest(TARGET_USER_ID, "EVENT_MANAGER", "EVENT", 40L),
                        "disabled-owner-key"
                ),
                ErrorCode.BUSINESS_CONFLICT
        );
    }

    @Test
    @DisplayName("不能重新启用归档赛事上的旧授权")
    void cannotReactivateAssignmentOnArchivedEvent() {
        when(assignmentMapper.selectById(100L)).thenReturn(
                assignment(100L, TARGET_USER_ID, 21L, "EVENT", 40L, 0, 1)
        );
        when(userMapper.selectById(TARGET_USER_ID)).thenReturn(user(TARGET_USER_ID, 1));
        when(roleMapper.selectById(21L)).thenReturn(role(21L, "EVENT_MANAGER"));
        when(contestEditionMapper.selectById(40L)).thenReturn(event(40L, 10L, null, 5));
        when(organizationMapper.selectById(10L)).thenReturn(
                organization(10L, "SCHOOL", "510000", 1)
        );
        when(organizationScopeService.loadEffectiveGrantsForExistingTargets(OPERATOR_ID)).thenReturn(
                List.of(globalAdminGrant())
        );

        assertBizCode(
                () -> service.updateAssignment(100L, updateRequest(true, 1), null),
                ErrorCode.BUSINESS_CONFLICT
        );
        verify(assignmentMapper, never()).updateScopedAssignment(
                any(), any(), any(), anyInt(), any(), anyInt(), any()
        );
    }

    private void assertBizCode(Runnable action, ErrorCode expected) {
        assertThatThrownBy(action::run)
                .isInstanceOfSatisfying(BizException.class, error ->
                        assertThat(error.getCode()).isEqualTo(expected.getCode()));
    }

    private EffectiveGrant globalAdminGrant() {
        return new EffectiveGrant("ADMIN", ScopeType.GLOBAL, 0L);
    }

    private RoleAssignmentCreateRequest createRequest(
            Long userId,
            String roleCode,
            String scopeType,
            Long scopeId
    ) {
        RoleAssignmentCreateRequest request = new RoleAssignmentCreateRequest();
        request.setUserId(String.valueOf(userId));
        request.setRoleCode(roleCode);
        request.setScopeType(scopeType);
        request.setScopeId(String.valueOf(scopeId));
        return request;
    }

    private RoleAssignmentUpdateRequest updateRequest(boolean active, int version) {
        RoleAssignmentUpdateRequest request = new RoleAssignmentUpdateRequest();
        request.setActive(active);
        request.setVersion(version);
        return request;
    }

    private SysUserDO user(Long id, int status) {
        SysUserDO user = new SysUserDO();
        user.setId(id);
        user.setStatus(status);
        return user;
    }

    private SysRoleDO role(Long id, String code) {
        SysRoleDO role = new SysRoleDO();
        role.setId(id);
        role.setRoleCode(code);
        role.setDeleted(0);
        return role;
    }

    private OrganizationDO organization(
            Long id,
            String type,
            String provinceCode,
            int status
    ) {
        OrganizationDO organization = new OrganizationDO();
        organization.setId(id);
        organization.setOrgType(type);
        organization.setProvinceCode(provinceCode);
        organization.setStatus(status);
        organization.setDeleted(0);
        return organization;
    }

    private ContestEditionScopeDO event(Long id, Long ownerOrgId, String provinceCode) {
        return event(id, ownerOrgId, provinceCode, 0);
    }

    private ContestEditionScopeDO event(
            Long id,
            Long ownerOrgId,
            String provinceCode,
            int status
    ) {
        ContestEditionScopeDO event = new ContestEditionScopeDO();
        event.setId(id);
        event.setOwnerOrgId(ownerOrgId);
        event.setProvinceCode(provinceCode);
        event.setStatus(status);
        event.setDeleted(0);
        return event;
    }

    private SysUserRoleDO assignment(
            Long id,
            Long userId,
            Long roleId,
            String scopeType,
            Long scopeId,
            int status,
            int version
    ) {
        SysUserRoleDO assignment = new SysUserRoleDO();
        assignment.setId(id);
        assignment.setUserId(userId);
        assignment.setRoleId(roleId);
        assignment.setScopeType(scopeType);
        assignment.setScopeId(scopeId);
        assignment.setStatus(status);
        assignment.setValidTo(null);
        assignment.setDeleted(0);
        assignment.setVersion(version);
        return assignment;
    }

    private static final class TrackingTransactionManager
            extends AbstractPlatformTransactionManager {

        private boolean committed;
        private boolean rolledBack;

        @Override
        protected Object doGetTransaction() {
            return new Object();
        }

        @Override
        protected void doBegin(Object transaction, TransactionDefinition definition) {
        }

        @Override
        protected void doCommit(DefaultTransactionStatus status) {
            committed = true;
        }

        @Override
        protected void doRollback(DefaultTransactionStatus status) {
            rolledBack = true;
        }
    }
}
