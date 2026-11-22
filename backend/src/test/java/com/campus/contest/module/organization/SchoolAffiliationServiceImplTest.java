package com.campus.contest.module.organization;

import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.campus.contest.TestInfrastructure;
import com.campus.contest.common.BizException;
import com.campus.contest.common.ErrorCode;
import com.campus.contest.common.idempotency.IdempotencyService;
import com.campus.contest.module.organization.dto.SchoolAffiliationPageQuery;
import com.campus.contest.module.organization.dto.SchoolAffiliationReviewRequest;
import com.campus.contest.module.organization.entity.OrganizationDO;
import com.campus.contest.module.organization.mapper.OrganizationMapper;
import com.campus.contest.module.organization.mapper.SchoolAffiliationMapper;
import com.campus.contest.module.organization.model.EffectiveGrant;
import com.campus.contest.module.organization.model.ScopeType;
import com.campus.contest.module.organization.model.SchoolSelectionValidation;
import com.campus.contest.module.organization.service.OrganizationScopeService;
import com.campus.contest.module.organization.service.impl.SchoolAffiliationServiceImpl;
import com.campus.contest.module.organization.vo.SchoolAffiliationPageVO;
import com.campus.contest.module.organization.vo.SchoolAffiliationReviewVO;
import com.campus.contest.module.system.dto.AuditLogCommand;
import com.campus.contest.module.system.dto.AuthorizationSnapshot;
import com.campus.contest.module.system.entity.SysUserDO;
import com.campus.contest.module.system.mapper.SysUserMapper;
import com.campus.contest.module.system.service.AuditLogService;
import com.campus.contest.module.system.service.AuthorizationSnapshotService;
import com.campus.contest.module.system.service.DictionaryQueryService;
import com.campus.contest.security.CurrentUserProvider;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.transaction.TransactionDefinition;
import org.springframework.transaction.support.AbstractPlatformTransactionManager;
import org.springframework.transaction.support.DefaultTransactionStatus;
import org.springframework.transaction.support.TransactionTemplate;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class SchoolAffiliationServiceImplTest {

    private static final Long OPERATOR_ID = 1L;
    private static final Long TARGET_USER_ID = 5L;

    @Mock
    private SysUserMapper userMapper;
    @Mock
    private OrganizationMapper organizationMapper;
    @Mock
    private SchoolAffiliationMapper schoolAffiliationMapper;
    @Mock
    private OrganizationScopeService organizationScopeService;
    @Mock
    private AuthorizationSnapshotService authorizationSnapshotService;
    @Mock
    private IdempotencyService idempotencyService;
    @Mock
    private DictionaryQueryService dictionaryQueryService;
    @Mock
    private AuditLogService auditLogService;
    @Mock
    private CurrentUserProvider currentUserProvider;

    private SchoolAffiliationServiceImpl service;

    @BeforeEach
    void setUp() {
        service = new SchoolAffiliationServiceImpl(
                userMapper,
                organizationMapper,
                schoolAffiliationMapper,
                organizationScopeService,
                authorizationSnapshotService,
                dictionaryQueryService,
                TestInfrastructure.passthroughIdempotencyService(),
                auditLogService,
                currentUserProvider,
                TestInfrastructure.transactionTemplate(),
                new ObjectMapper().findAndRegisterModules()
        );
        lenient().when(currentUserProvider.requireUserId()).thenReturn(OPERATOR_ID);
    }

    @Test
    @DisplayName("学校管理员只查询本校 PENDING 申请")
    void schoolAdminListsOwnSchoolAffiliations() {
        when(organizationScopeService.loadEffectiveGrants(OPERATOR_ID)).thenReturn(List.of(
                new EffectiveGrant("SCHOOL_ADMIN", ScopeType.ORGANIZATION, 10L)
        ));
        Page<SysUserDO> page = new Page<>(1, 20);
        page.setRecords(List.of(user(TARGET_USER_ID, 10L, "PENDING", 2)));
        page.setTotal(1);
        when(userMapper.selectPage(any(Page.class), any())).thenReturn(page);
        when(organizationMapper.selectBatchIds(any())).thenReturn(List.of(
                school(10L, "四川A大学", "510000", 1)
        ));

        SchoolAffiliationPageVO result =
                service.pageAffiliations(new SchoolAffiliationPageQuery());

        assertThat(result.getItems()).singleElement().satisfies(item -> {
            assertThat(item.getSchoolId()).isEqualTo("10");
            assertThat(item.getSchoolName()).isEqualTo("四川A大学");
            assertThat(item.getStatus()).isEqualTo("PENDING");
        });
    }

    @Test
    @DisplayName("学校管理员不能指定其他学校筛选")
    void schoolAdminCannotFilterAnotherSchool() {
        when(organizationScopeService.loadEffectiveGrants(OPERATOR_ID)).thenReturn(List.of(
                new EffectiveGrant("SCHOOL_ADMIN", ScopeType.ORGANIZATION, 10L)
        ));
        SchoolAffiliationPageQuery query = new SchoolAffiliationPageQuery();
        query.setSchoolId("20");

        assertBizCode(
                () -> service.pageAffiliations(query),
                ErrorCode.DATA_FORBIDDEN
        );
        verify(userMapper, never()).selectPage(any(), any());
    }

    @Test
    @DisplayName("平台管理员可以按学校筛选")
    void platformAdminFiltersBySchool() {
        when(organizationScopeService.loadEffectiveGrants(OPERATOR_ID)).thenReturn(List.of(
                new EffectiveGrant("ADMIN", ScopeType.GLOBAL, 0L)
        ));
        when(organizationMapper.selectById(20L)).thenReturn(
                school(20L, "四川B大学", "510000", 1)
        );
        Page<SysUserDO> page = new Page<>(1, 20);
        page.setRecords(List.of(user(TARGET_USER_ID, 20L, "PENDING", 2)));
        page.setTotal(1);
        when(userMapper.selectPage(any(Page.class), any())).thenReturn(page);
        when(organizationMapper.selectBatchIds(any())).thenReturn(List.of(
                school(20L, "四川B大学", "510000", 1)
        ));
        SchoolAffiliationPageQuery query = new SchoolAffiliationPageQuery();
        query.setSchoolId("20");

        SchoolAffiliationPageVO result = service.pageAffiliations(query);

        assertThat(result.getItems()).singleElement()
                .satisfies(item -> assertThat(item.getSchoolId()).isEqualTo("20"));
    }

    @Test
    @DisplayName("通过学校归属写入审核人和时间")
    void approveWritesReviewerAndTimestamp() {
        when(organizationScopeService.loadEffectiveGrants(OPERATOR_ID)).thenReturn(List.of(
                new EffectiveGrant("SCHOOL_ADMIN", ScopeType.ORGANIZATION, 10L)
        ));
        when(userMapper.selectById(TARGET_USER_ID)).thenReturn(
                user(TARGET_USER_ID, 10L, "PENDING", 3)
        );
        when(organizationMapper.selectById(10L)).thenReturn(
                school(10L, "四川A大学", "510000", 1)
        );
        when(dictionaryQueryService.containsEnabledValue("CN_PROVINCE", "510000"))
                .thenReturn(true);
        when(schoolAffiliationMapper.updateReview(
                eq(TARGET_USER_ID),
                eq("VERIFIED"),
                eq(OPERATOR_ID),
                any(LocalDateTime.class),
                isNull(),
                eq(3)
        )).thenReturn(1);

        SchoolAffiliationReviewVO result = service.reviewAffiliation(
                TARGET_USER_ID,
                reviewRequest("APPROVE", null, 3),
                null
        );

        assertThat(result.getStatus()).isEqualTo("VERIFIED");
        assertThat(result.getVersion()).isEqualTo(4);
        verify(auditLogService).record(any(AuditLogCommand.class));
    }

    @Test
    @DisplayName("驳回必须填写原因并保存 REJECTED")
    void rejectRequiresReasonAndPersistsIt() {
        assertBizCode(
                () -> service.reviewAffiliation(
                        TARGET_USER_ID,
                        reviewRequest("REJECT", " ", 1),
                        null
                ),
                ErrorCode.INVALID_PARAM
        );

        when(organizationScopeService.loadEffectiveGrants(OPERATOR_ID)).thenReturn(List.of(
                new EffectiveGrant("SCHOOL_ADMIN", ScopeType.ORGANIZATION, 10L)
        ));
        when(userMapper.selectById(TARGET_USER_ID)).thenReturn(
                user(TARGET_USER_ID, 10L, "PENDING", 1)
        );
        when(schoolAffiliationMapper.updateReview(
                eq(TARGET_USER_ID),
                eq("REJECTED"),
                eq(OPERATOR_ID),
                any(LocalDateTime.class),
                eq("材料不完整"),
                eq(1)
        )).thenReturn(1);

        SchoolAffiliationReviewVO result = service.reviewAffiliation(
                TARGET_USER_ID,
                reviewRequest("REJECT", "材料不完整", 1),
                null
        );

        assertThat(result.getStatus()).isEqualTo("REJECTED");
        assertThat(result.getVersion()).isEqualTo(2);
        verify(auditLogService).record(any(AuditLogCommand.class));
    }

    @Test
    @DisplayName("跨校审核返回 403，不写库、不写审计")
    void schoolAdminCannotReviewAnotherSchool() {
        when(organizationScopeService.loadEffectiveGrants(OPERATOR_ID)).thenReturn(List.of(
                new EffectiveGrant("SCHOOL_ADMIN", ScopeType.ORGANIZATION, 10L)
        ));
        when(userMapper.selectById(TARGET_USER_ID)).thenReturn(
                user(TARGET_USER_ID, 20L, "PENDING", 1)
        );

        assertBizCode(
                () -> service.reviewAffiliation(
                        TARGET_USER_ID,
                        reviewRequest("APPROVE", null, 1),
                        null
                ),
                ErrorCode.DATA_FORBIDDEN
        );
        verify(schoolAffiliationMapper, never()).updateReview(
                any(), any(), any(), any(), any(), any()
        );
        verify(auditLogService, never()).record(any());
    }

    @Test
    @DisplayName("非 PENDING 或旧版本审核返回 409")
    void rejectsNonPendingAndStaleReview() {
        when(organizationScopeService.loadEffectiveGrants(OPERATOR_ID)).thenReturn(List.of(
                new EffectiveGrant("SCHOOL_ADMIN", ScopeType.ORGANIZATION, 10L)
        ));
        when(userMapper.selectById(TARGET_USER_ID))
                .thenReturn(user(TARGET_USER_ID, 10L, "VERIFIED", 2))
                .thenReturn(user(TARGET_USER_ID, 10L, "PENDING", 3));

        assertBizCode(
                () -> service.reviewAffiliation(
                        TARGET_USER_ID,
                        reviewRequest("APPROVE", null, 2),
                        null
                ),
                ErrorCode.BUSINESS_CONFLICT
        );
        assertBizCode(
                () -> service.reviewAffiliation(
                        TARGET_USER_ID,
                        reviewRequest("APPROVE", null, 2),
                        null
                ),
                ErrorCode.BUSINESS_CONFLICT
        );
        verify(schoolAffiliationMapper, never()).updateReview(
                any(), any(), any(), any(), any(), any()
        );
    }

    @Test
    @DisplayName("审核审计失败触发事务回滚")
    void auditFailureRollsBackReview() {
        TrackingTransactionManager transactionManager = new TrackingTransactionManager();
        SchoolAffiliationServiceImpl transactionalService = new SchoolAffiliationServiceImpl(
                userMapper,
                organizationMapper,
                schoolAffiliationMapper,
                organizationScopeService,
                authorizationSnapshotService,
                dictionaryQueryService,
                TestInfrastructure.passthroughIdempotencyService(),
                auditLogService,
                currentUserProvider,
                new TransactionTemplate(transactionManager),
                new ObjectMapper().findAndRegisterModules()
        );
        when(organizationScopeService.loadEffectiveGrants(OPERATOR_ID)).thenReturn(List.of(
                new EffectiveGrant("SCHOOL_ADMIN", ScopeType.ORGANIZATION, 10L)
        ));
        when(userMapper.selectById(TARGET_USER_ID)).thenReturn(
                user(TARGET_USER_ID, 10L, "PENDING", 2)
        );
        when(organizationMapper.selectById(10L)).thenReturn(
                school(10L, "四川A大学", "510000", 1)
        );
        when(dictionaryQueryService.containsEnabledValue("CN_PROVINCE", "510000"))
                .thenReturn(true);
        when(schoolAffiliationMapper.updateReview(
                any(), any(), any(), any(), any(), any()
        )).thenReturn(1);
        doThrow(new IllegalStateException("audit unavailable"))
                .when(auditLogService).record(any());

        assertThatThrownBy(() -> transactionalService.reviewAffiliation(
                TARGET_USER_ID,
                reviewRequest("APPROVE", null, 2),
                null
        )).isInstanceOf(IllegalStateException.class);
        assertThat(transactionManager.rolledBack).isTrue();
        assertThat(transactionManager.committed).isFalse();
    }

    @Test
    @DisplayName("目标用户不存在返回 404")
    void missingTargetUserReturns404() {
        when(userMapper.selectById(TARGET_USER_ID)).thenReturn(null);

        assertBizCode(
                () -> service.reviewAffiliation(
                        TARGET_USER_ID,
                        reviewRequest("APPROVE", null, 1),
                        null
                ),
                ErrorCode.RESOURCE_NOT_FOUND
        );
    }

    @Test
    @DisplayName("幂等依赖不可用时返回 503 + 50003")
    void idempotencyFailureReturns503() {
        SchoolAffiliationServiceImpl idempotentService = new SchoolAffiliationServiceImpl(
                userMapper,
                organizationMapper,
                schoolAffiliationMapper,
                organizationScopeService,
                authorizationSnapshotService,
                dictionaryQueryService,
                idempotencyService,
                auditLogService,
                currentUserProvider,
                TestInfrastructure.transactionTemplate(),
                new ObjectMapper().findAndRegisterModules()
        );
        when(idempotencyService.execute(
                any(),
                eq("SCHOOL_AFFILIATION_REVIEW"),
                eq("review-key"),
                any(),
                any(),
                eq(SchoolAffiliationReviewVO.class),
                any()
        )).thenThrow(new BizException(
                ErrorCode.AUTH_IDEMPOTENCY_UNAVAILABLE.getCode(),
                "idempotency unavailable"
        ));

        assertBizCode(
                () -> idempotentService.reviewAffiliation(
                        TARGET_USER_ID,
                        reviewRequest("APPROVE", null, 1),
                        "review-key"
                ),
                ErrorCode.AUTH_IDEMPOTENCY_UNAVAILABLE
        );
    }

    @Test
    @DisplayName("REJECTED 学生可以重新提交启用学校")
    void rejectedUserCanSubmitAnEnabledSchool() {
        when(userMapper.selectById(TARGET_USER_ID)).thenReturn(
                user(TARGET_USER_ID, 10L, "REJECTED", 3)
        );
        when(authorizationSnapshotService.loadRequired(TARGET_USER_ID)).thenReturn(
                snapshot(TARGET_USER_ID, "STUDENT")
        );
        when(organizationMapper.selectById(20L)).thenReturn(
                school(20L, "四川B大学", "510000", 1)
        );
        when(dictionaryQueryService.containsEnabledValue("CN_PROVINCE", "510000"))
                .thenReturn(true);

        SchoolSelectionValidation result =
                service.validateSelection(TARGET_USER_ID, 20L, 3);

        assertThat(result.schoolId()).isEqualTo(20L);
        assertThat(result.targetStatus()).isEqualTo("PENDING");
        assertThat(result.noChange()).isFalse();
        assertThat(result.clearPreviousReview()).isTrue();
    }

    @Test
    @DisplayName("未选校用户的默认 PENDING 状态允许首次选择学校")
    void unassignedPendingUserCanSelectFirstSchool() {
        when(userMapper.selectById(TARGET_USER_ID)).thenReturn(
                user(TARGET_USER_ID, null, "PENDING", 0)
        );
        when(authorizationSnapshotService.loadRequired(TARGET_USER_ID)).thenReturn(
                snapshot(TARGET_USER_ID, "STUDENT")
        );
        when(organizationMapper.selectById(20L)).thenReturn(
                school(20L, "四川B大学", "510000", 1)
        );
        when(dictionaryQueryService.containsEnabledValue("CN_PROVINCE", "510000"))
                .thenReturn(true);

        SchoolSelectionValidation result =
                service.validateSelection(TARGET_USER_ID, 20L, 0);

        assertThat(result.schoolId()).isEqualTo(20L);
        assertThat(result.targetStatus()).isEqualTo("PENDING");
        assertThat(result.noChange()).isFalse();
        assertThat(result.clearPreviousReview()).isTrue();
    }

    @Test
    @DisplayName("PENDING 用户只能保持原学校，不能直接更换")
    void pendingUserCannotSwitchSchool() {
        when(userMapper.selectById(TARGET_USER_ID)).thenReturn(
                user(TARGET_USER_ID, 10L, "PENDING", 3)
        );
        when(authorizationSnapshotService.loadRequired(TARGET_USER_ID)).thenReturn(
                snapshot(TARGET_USER_ID, "STUDENT")
        );
        when(organizationMapper.selectById(10L)).thenReturn(
                school(10L, "四川A大学", "510000", 1)
        );
        when(organizationMapper.selectById(20L)).thenReturn(
                school(20L, "四川B大学", "510000", 1)
        );

        SchoolSelectionValidation unchanged =
                service.validateSelection(TARGET_USER_ID, 10L, 3);
        assertThat(unchanged.noChange()).isTrue();
        assertThat(unchanged.clearPreviousReview()).isFalse();

        assertBizCode(
                () -> service.validateSelection(TARGET_USER_ID, 20L, 3),
                ErrorCode.BUSINESS_CONFLICT
        );
    }

    @Test
    @DisplayName("VERIFIED 用户不能切换到其他学校")
    void verifiedUserCannotSwitchSchool() {
        when(userMapper.selectById(TARGET_USER_ID)).thenReturn(
                user(TARGET_USER_ID, 10L, "VERIFIED", 3)
        );
        when(authorizationSnapshotService.loadRequired(TARGET_USER_ID)).thenReturn(
                snapshot(TARGET_USER_ID, "STUDENT")
        );
        when(organizationMapper.selectById(20L)).thenReturn(
                school(20L, "四川B大学", "510000", 1)
        );

        assertBizCode(
                () -> service.validateSelection(TARGET_USER_ID, 20L, 3),
                ErrorCode.BUSINESS_CONFLICT
        );
    }

    @Test
    @DisplayName("停用学校不能被选择或重新提交")
    void disabledSchoolCannotBeSelected() {
        when(userMapper.selectById(TARGET_USER_ID)).thenReturn(
                user(TARGET_USER_ID, 10L, "REJECTED", 3)
        );
        when(authorizationSnapshotService.loadRequired(TARGET_USER_ID)).thenReturn(
                snapshot(TARGET_USER_ID, "STUDENT")
        );
        when(organizationMapper.selectById(20L)).thenReturn(
                school(20L, "四川B大学", "510000", 0)
        );

        assertBizCode(
                () -> service.validateSelection(TARGET_USER_ID, 20L, 3),
                ErrorCode.BUSINESS_CONFLICT
        );
    }

    @Test
    @DisplayName("停用学校上的申请不能 APPROVE，且不写库不写审计")
    void cannotApproveDisabledSchool() {
        when(organizationScopeService.loadEffectiveGrants(OPERATOR_ID)).thenReturn(List.of(
                new EffectiveGrant("SCHOOL_ADMIN", ScopeType.ORGANIZATION, 10L)
        ));
        when(userMapper.selectById(TARGET_USER_ID)).thenReturn(
                user(TARGET_USER_ID, 10L, "PENDING", 2)
        );
        when(organizationMapper.selectById(10L)).thenReturn(
                school(10L, "四川A大学", "510000", 0)
        );

        assertBizCode(
                () -> service.reviewAffiliation(
                        TARGET_USER_ID,
                        reviewRequest("APPROVE", null, 2),
                        null
                ),
                ErrorCode.BUSINESS_CONFLICT
        );
        verify(schoolAffiliationMapper, never()).updateReview(
                any(), any(), any(), any(), any(), any()
        );
        verify(auditLogService, never()).record(any());
    }

    @Test
    @DisplayName("省份字典不可用的学校不能 APPROVE")
    void cannotApproveSchoolWithUnavailableProvince() {
        when(organizationScopeService.loadEffectiveGrants(OPERATOR_ID)).thenReturn(List.of(
                new EffectiveGrant("SCHOOL_ADMIN", ScopeType.ORGANIZATION, 10L)
        ));
        when(userMapper.selectById(TARGET_USER_ID)).thenReturn(
                user(TARGET_USER_ID, 10L, "PENDING", 2)
        );
        when(organizationMapper.selectById(10L)).thenReturn(
                school(10L, "四川A大学", "510000", 1)
        );
        when(dictionaryQueryService.containsEnabledValue("CN_PROVINCE", "510000"))
                .thenReturn(false);

        assertBizCode(
                () -> service.reviewAffiliation(
                        TARGET_USER_ID,
                        reviewRequest("APPROVE", null, 2),
                        null
                ),
                ErrorCode.BUSINESS_CONFLICT
        );
        verify(schoolAffiliationMapper, never()).updateReview(
                any(), any(), any(), any(), any(), any()
        );
    }

    @Test
    @DisplayName("学校消失或非 SCHOOL 类型时不能 APPROVE")
    void cannotApproveMissingOrNonSchoolOrganization() {
        when(organizationScopeService.loadEffectiveGrants(OPERATOR_ID)).thenReturn(List.of(
                new EffectiveGrant("SCHOOL_ADMIN", ScopeType.ORGANIZATION, 10L)
        ));
        when(userMapper.selectById(TARGET_USER_ID)).thenReturn(
                user(TARGET_USER_ID, 10L, "PENDING", 2)
        );
        when(organizationMapper.selectById(10L))
                .thenReturn(null)
                .thenReturn(organization(10L, "PROVINCE_ORGANIZER", "510000", 1));

        assertBizCode(
                () -> service.reviewAffiliation(
                        TARGET_USER_ID,
                        reviewRequest("APPROVE", null, 2),
                        null
                ),
                ErrorCode.BUSINESS_CONFLICT
        );
        assertBizCode(
                () -> service.reviewAffiliation(
                        TARGET_USER_ID,
                        reviewRequest("APPROVE", null, 2),
                        null
                ),
                ErrorCode.BUSINESS_CONFLICT
        );
        verify(schoolAffiliationMapper, never()).updateReview(
                any(), any(), any(), any(), any(), any()
        );
    }

    @Test
    @DisplayName("学校停用后仍可以 REJECT 清理待审核申请")
    void canRejectWhenSchoolIsDisabled() {
        when(organizationScopeService.loadEffectiveGrants(OPERATOR_ID)).thenReturn(List.of(
                new EffectiveGrant("SCHOOL_ADMIN", ScopeType.ORGANIZATION, 10L)
        ));
        when(userMapper.selectById(TARGET_USER_ID)).thenReturn(
                user(TARGET_USER_ID, 10L, "PENDING", 2)
        );
        when(schoolAffiliationMapper.updateReview(
                eq(TARGET_USER_ID),
                eq("REJECTED"),
                eq(OPERATOR_ID),
                any(LocalDateTime.class),
                eq("学校已停用"),
                eq(2)
        )).thenReturn(1);

        SchoolAffiliationReviewVO result = service.reviewAffiliation(
                TARGET_USER_ID,
                reviewRequest("REJECT", "学校已停用", 2),
                null
        );

        assertThat(result.getStatus()).isEqualTo("REJECTED");
        verify(auditLogService).record(any(AuditLogCommand.class));
    }

    private void assertBizCode(Runnable action, ErrorCode expected) {
        assertThatThrownBy(action::run)
                .isInstanceOfSatisfying(BizException.class, error ->
                        assertThat(error.getCode()).isEqualTo(expected.getCode()));
    }

    private SchoolAffiliationReviewRequest reviewRequest(
            String decision,
            String reason,
            int version
    ) {
        SchoolAffiliationReviewRequest request = new SchoolAffiliationReviewRequest();
        request.setDecision(decision);
        request.setReason(reason);
        request.setVersion(version);
        return request;
    }

    private SysUserDO user(Long id, Long schoolId, String status, int version) {
        SysUserDO user = new SysUserDO();
        user.setId(id);
        user.setUsername("student" + id);
        user.setRealName("学生" + id);
        user.setSchoolId(schoolId);
        user.setSchoolVerificationStatus(status);
        user.setSchoolReviewReason("OLD");
        user.setStatus(1);
        user.setCreatedAt(LocalDateTime.now().minusDays(1));
        user.setUpdatedAt(LocalDateTime.now());
        user.setVersion(version);
        user.setDeleted(0);
        return user;
    }

    private OrganizationDO school(
            Long id,
            String name,
            String provinceCode,
            int status
    ) {
        OrganizationDO school = organization(id, "SCHOOL", provinceCode, status);
        school.setOrgName(name);
        return school;
    }

    private OrganizationDO organization(
            Long id,
            String type,
            String provinceCode,
            int status
    ) {
        OrganizationDO school = new OrganizationDO();
        school.setId(id);
        school.setOrgCode("ORG-" + id);
        school.setOrgType(type);
        school.setProvinceCode(provinceCode);
        school.setStatus(status);
        school.setDeleted(0);
        return school;
    }

    private AuthorizationSnapshot snapshot(Long userId, String roleCode) {
        return new AuthorizationSnapshot(
                userId,
                "student5",
                1,
                Set.of(roleCode),
                Set.of(),
                Set.of("SELF")
        );
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
