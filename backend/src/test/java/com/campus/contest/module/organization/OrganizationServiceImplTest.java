package com.campus.contest.module.organization;

import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.campus.contest.TestInfrastructure;
import com.campus.contest.common.BizException;
import com.campus.contest.common.ErrorCode;
import com.campus.contest.common.idempotency.IdempotencyService;
import com.campus.contest.module.organization.dto.OrganizationCreateRequest;
import com.campus.contest.module.organization.dto.OrganizationPageQuery;
import com.campus.contest.module.organization.dto.OrganizationUpdateRequest;
import com.campus.contest.module.organization.entity.OrganizationDO;
import com.campus.contest.module.organization.mapper.OrganizationMapper;
import com.campus.contest.module.organization.model.EffectiveScope;
import com.campus.contest.module.organization.service.OrganizationScopeService;
import com.campus.contest.module.organization.service.impl.OrganizationServiceImpl;
import com.campus.contest.module.organization.vo.OrganizationCreatedVO;
import com.campus.contest.module.organization.vo.OrganizationPageVO;
import com.campus.contest.module.system.dto.AuditLogCommand;
import com.campus.contest.module.system.service.AuditLogService;
import com.campus.contest.module.system.service.DictionaryQueryService;
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

import java.util.List;
import java.util.Set;
import java.util.concurrent.atomic.AtomicLong;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class OrganizationServiceImplTest {

    private static final Long OPERATOR_ID = 1L;

    @Mock
    private OrganizationMapper organizationMapper;
    @Mock
    private OrganizationScopeService organizationScopeService;
    @Mock
    private DictionaryQueryService dictionaryQueryService;
    @Mock
    private IdempotencyService idempotencyService;
    @Mock
    private AuditLogService auditLogService;
    @Mock
    private CurrentUserProvider currentUserProvider;

    private OrganizationServiceImpl service;

    @BeforeEach
    void setUp() {
        service = new OrganizationServiceImpl(
                organizationMapper,
                organizationScopeService,
                dictionaryQueryService,
                TestInfrastructure.passthroughIdempotencyService(),
                auditLogService,
                currentUserProvider,
                TestInfrastructure.transactionTemplate(),
                new ObjectMapper()
        );
    }

    @Test
    @DisplayName("平台管理员可以创建四种组织类型")
    void createsAllOrganizationTypes() {
        when(currentUserProvider.requireUserId()).thenReturn(OPERATOR_ID);
        when(organizationScopeService.loadCurrentRequired()).thenReturn(globalScope());
        when(dictionaryQueryService.containsEnabledValue("CN_PROVINCE", "510000")).thenReturn(true);
        when(organizationMapper.selectCount(any())).thenReturn(0L);
        AtomicLong ids = new AtomicLong(10L);
        when(organizationMapper.insert(any(OrganizationDO.class))).thenAnswer(invocation -> {
            OrganizationDO organization = invocation.getArgument(0);
            organization.setId(ids.getAndIncrement());
            return 1;
        });

        OrganizationCreatedVO school = service.createOrganization(
                createRequest("SC-A", "四川A大学", "SCHOOL", "510000"),
                "create-school"
        );
        service.createOrganization(
                createRequest("SC-PROVINCE", "四川省主办方", "PROVINCE_ORGANIZER", "510000"),
                "create-province"
        );
        service.createOrganization(
                createRequest("NATIONAL", "全国组委会", "NATIONAL_ORGANIZER", null),
                "create-national"
        );
        service.createOrganization(
                createRequest("PLATFORM", "赛事平台", "PLATFORM", null),
                "create-platform"
        );

        assertThat(school.getId()).isEqualTo("10");
        assertThat(school.getOrgCode()).isEqualTo("SC-A");
        ArgumentCaptor<OrganizationDO> captor = ArgumentCaptor.forClass(OrganizationDO.class);
        verify(organizationMapper, times(4)).insert(captor.capture());
        assertThat(captor.getAllValues())
                .extracting(OrganizationDO::getOrgType)
                .containsExactly("SCHOOL", "PROVINCE_ORGANIZER", "NATIONAL_ORGANIZER", "PLATFORM");
        verify(auditLogService, times(4)).record(any(AuditLogCommand.class));
    }

    @Test
    @DisplayName("创建组织缺少幂等键、类型非法或省份非法时拒绝")
    void rejectsInvalidCreateRequests() {
        OrganizationCreateRequest missingKey = createRequest("SC-A", "A大学", "SCHOOL", "510000");
        when(dictionaryQueryService.containsEnabledValue("CN_PROVINCE", "510000")).thenReturn(true);
        assertBizCode(
                () -> service.createOrganization(missingKey, " "),
                ErrorCode.INVALID_PARAM
        );

        OrganizationCreateRequest invalidType = createRequest("X", "组织", "UNKNOWN", null);
        assertBizCode(
                () -> service.createOrganization(invalidType, "create-key"),
                ErrorCode.INVALID_PARAM
        );

        OrganizationCreateRequest invalidProvince = createRequest("SC-B", "B大学", "SCHOOL", "999999");
        when(dictionaryQueryService.containsEnabledValue("CN_PROVINCE", "999999")).thenReturn(false);
        assertBizCode(
                () -> service.createOrganization(invalidProvince, "create-key"),
                ErrorCode.INVALID_PARAM
        );

        verify(organizationMapper, never()).insert(any(OrganizationDO.class));
    }

    @Test
    @DisplayName("组织编码重复返回 409 且不写审计")
    void rejectsDuplicateOrganizationCode() {
        when(currentUserProvider.requireUserId()).thenReturn(OPERATOR_ID);
        when(organizationScopeService.loadCurrentRequired()).thenReturn(globalScope());
        when(dictionaryQueryService.containsEnabledValue("CN_PROVINCE", "510000")).thenReturn(true);
        when(organizationMapper.selectCount(any())).thenReturn(1L);

        assertBizCode(
                () -> service.createOrganization(
                        createRequest("SC-A", "A大学", "SCHOOL", "510000"),
                        "create-key"
                ),
                ErrorCode.DUPLICATE_RELATION
        );

        verify(organizationMapper, never()).insert(any(OrganizationDO.class));
        verify(auditLogService, never()).record(any());
    }

    @Test
    @DisplayName("组织范围只能查询授权组织")
    void pageUsesOrganizationScopeFilter() {
        when(organizationScopeService.loadCurrentRequired()).thenReturn(
                new EffectiveScope(false, Set.of(10L), Set.of(), Set.of())
        );
        Page<OrganizationDO> resultPage = new Page<>(1, 20);
        resultPage.setRecords(List.of(organization(10L, "SCHOOL", "510000", 1, 2)));
        resultPage.setTotal(1);
        when(organizationMapper.selectPage(any(Page.class), any())).thenReturn(resultPage);

        OrganizationPageQuery query = new OrganizationPageQuery();
        query.setPage(1);
        query.setSize(20);
        OrganizationPageVO page = service.pageOrganizations(query);

        assertThat(page.getItems()).singleElement()
                .satisfies(item -> assertThat(item.getId()).isEqualTo("10"));
        verify(organizationMapper).selectPage(any(Page.class), any());
    }

    @Test
    @DisplayName("跨组织详情返回 404，跨组织修改返回 403")
    void hidesAndRejectsCrossOrganizationAccess() {
        when(organizationScopeService.loadCurrentRequired()).thenReturn(
                new EffectiveScope(false, Set.of(10L), Set.of(), Set.of())
        );
        when(organizationMapper.selectById(20L)).thenReturn(
                organization(20L, "SCHOOL", "510000", 1, 3)
        );

        assertBizCode(() -> service.getOrganization(20L), ErrorCode.RESOURCE_NOT_FOUND);

        OrganizationUpdateRequest request = updateStatusRequest(0, 3);
        assertBizCode(
                () -> service.updateOrganization(20L, request, null),
                ErrorCode.DATA_FORBIDDEN
        );
        verify(organizationMapper, never()).updateById(any(OrganizationDO.class));
        verify(auditLogService, never()).record(any());
    }

    @Test
    @DisplayName("组织范围用户即使拥有功能权限也不能修改本组织")
    void organizationScopeCannotUpdateOwnOrganization() {
        when(currentUserProvider.requireUserId()).thenReturn(OPERATOR_ID);
        when(organizationScopeService.loadCurrentRequired()).thenReturn(
                new EffectiveScope(false, Set.of(10L), Set.of(), Set.of())
        );
        when(organizationMapper.selectById(10L)).thenReturn(
                organization(10L, "SCHOOL", "510000", 1, 3)
        );

        assertBizCode(
                () -> service.updateOrganization(10L, updateStatusRequest(0, 3), null),
                ErrorCode.DATA_FORBIDDEN
        );

        verify(organizationMapper, never()).updateById(any(OrganizationDO.class));
        verify(auditLogService, never()).record(any());
    }

    @Test
    @DisplayName("修改使用真实版本乐观锁，停用只更新状态")
    void updatesVersionAndDisablesWithoutDeleting() {
        OrganizationDO existing = organization(10L, "SCHOOL", "510000", 1, 3);
        when(currentUserProvider.requireUserId()).thenReturn(OPERATOR_ID);
        when(organizationScopeService.loadCurrentRequired()).thenReturn(globalScope());
        when(organizationMapper.selectById(10L)).thenReturn(existing);
        when(organizationMapper.updateById(any(OrganizationDO.class))).thenReturn(1);

        IdVersionVO result = service.updateOrganization(
                10L,
                updateStatusRequest(0, 3),
                null
        );

        assertThat(result.getVersion()).isEqualTo(4);
        ArgumentCaptor<OrganizationDO> captor = ArgumentCaptor.forClass(OrganizationDO.class);
        verify(organizationMapper).updateById(captor.capture());
        assertThat(captor.getValue().getStatus()).isZero();
        assertThat(captor.getValue().getVersion()).isEqualTo(3);
        verify(organizationMapper, never()).deleteById(any(java.io.Serializable.class));
        verify(auditLogService).record(any(AuditLogCommand.class));
    }

    @Test
    @DisplayName("旧版本修改返回 409 且不写库、不写审计")
    void rejectsStaleVersion() {
        when(currentUserProvider.requireUserId()).thenReturn(OPERATOR_ID);
        when(organizationScopeService.loadCurrentRequired()).thenReturn(globalScope());
        when(organizationMapper.selectById(10L)).thenReturn(
                organization(10L, "SCHOOL", "510000", 1, 5)
        );

        assertBizCode(
                () -> service.updateOrganization(10L, updateStatusRequest(0, 4), null),
                ErrorCode.BUSINESS_CONFLICT
        );

        verify(organizationMapper, never()).updateById(any(OrganizationDO.class));
        verify(auditLogService, never()).record(any());
    }

    @Test
    @DisplayName("修改组织时非法省份或状态返回 400")
    void rejectsInvalidUpdateFields() {
        when(currentUserProvider.requireUserId()).thenReturn(OPERATOR_ID);
        when(organizationScopeService.loadCurrentRequired()).thenReturn(globalScope());
        when(organizationMapper.selectById(10L)).thenReturn(
                organization(10L, "SCHOOL", "510000", 1, 1)
        );
        when(dictionaryQueryService.containsEnabledValue("CN_PROVINCE", "999999")).thenReturn(false);

        OrganizationUpdateRequest invalidProvince = new OrganizationUpdateRequest();
        invalidProvince.setProvinceCode("999999");
        invalidProvince.setVersion(1);
        assertBizCode(
                () -> service.updateOrganization(10L, invalidProvince, null),
                ErrorCode.INVALID_PARAM
        );

        OrganizationUpdateRequest invalidStatus = updateStatusRequest(2, 1);
        assertBizCode(
                () -> service.updateOrganization(10L, invalidStatus, null),
                ErrorCode.INVALID_PARAM
        );

        verify(organizationMapper, never()).updateById(any(OrganizationDO.class));
        verify(auditLogService, never()).record(any());
    }

    @Test
    @DisplayName("组织不存在返回 404")
    void rejectsMissingOrganization() {
        when(organizationScopeService.loadCurrentRequired()).thenReturn(globalScope());
        when(organizationMapper.selectById(99L)).thenReturn(null);

        assertBizCode(() -> service.getOrganization(99L), ErrorCode.RESOURCE_NOT_FOUND);
        assertBizCode(
                () -> service.updateOrganization(99L, updateStatusRequest(0, 0), null),
                ErrorCode.RESOURCE_NOT_FOUND
        );
    }

    @Test
    @DisplayName("审计失败使创建调用失败并触发事务回滚")
    void auditFailureTriggersRollback() {
        TrackingTransactionManager transactionManager = new TrackingTransactionManager();
        OrganizationServiceImpl transactionalService = new OrganizationServiceImpl(
                organizationMapper,
                organizationScopeService,
                dictionaryQueryService,
                TestInfrastructure.passthroughIdempotencyService(),
                auditLogService,
                currentUserProvider,
                new TransactionTemplate(transactionManager),
                new ObjectMapper()
        );
        when(currentUserProvider.requireUserId()).thenReturn(OPERATOR_ID);
        when(organizationScopeService.loadCurrentRequired()).thenReturn(globalScope());
        when(dictionaryQueryService.containsEnabledValue("CN_PROVINCE", "510000")).thenReturn(true);
        when(organizationMapper.selectCount(any())).thenReturn(0L);
        when(organizationMapper.insert(any(OrganizationDO.class))).thenAnswer(invocation -> {
            OrganizationDO organization = invocation.getArgument(0);
            organization.setId(10L);
            return 1;
        });
        doThrow(new IllegalStateException("audit unavailable"))
                .when(auditLogService).record(any());

        assertThatThrownBy(() -> transactionalService.createOrganization(
                createRequest("SC-A", "A大学", "SCHOOL", "510000"),
                "create-key"
        )).isInstanceOf(IllegalStateException.class);
        assertThat(transactionManager.rolledBack).isTrue();
        assertThat(transactionManager.committed).isFalse();
    }

    private void assertBizCode(Runnable action, ErrorCode expected) {
        assertThatThrownBy(action::run)
                .isInstanceOfSatisfying(BizException.class, error ->
                        assertThat(error.getCode()).isEqualTo(expected.getCode()));
    }

    private EffectiveScope globalScope() {
        return new EffectiveScope(true, Set.of(), Set.of(), Set.of());
    }

    private OrganizationCreateRequest createRequest(
            String code,
            String name,
            String type,
            String provinceCode
    ) {
        OrganizationCreateRequest request = new OrganizationCreateRequest();
        request.setOrgCode(code);
        request.setOrgName(name);
        request.setOrgType(type);
        request.setProvinceCode(provinceCode);
        return request;
    }

    private OrganizationUpdateRequest updateStatusRequest(Integer status, Integer version) {
        OrganizationUpdateRequest request = new OrganizationUpdateRequest();
        request.setStatus(status);
        request.setVersion(version);
        return request;
    }

    private OrganizationDO organization(
            Long id,
            String type,
            String provinceCode,
            Integer status,
            Integer version
    ) {
        OrganizationDO organization = new OrganizationDO();
        organization.setId(id);
        organization.setOrgCode("ORG-" + id);
        organization.setOrgName("组织" + id);
        organization.setOrgType(type);
        organization.setProvinceCode(provinceCode);
        organization.setStatus(status);
        organization.setVersion(version);
        organization.setDeleted(0);
        return organization;
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
