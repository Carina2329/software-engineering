package com.campus.contest.module.organization;

import com.campus.contest.common.BizException;
import com.campus.contest.common.ErrorCode;
import com.campus.contest.module.organization.entity.ContestEditionScopeDO;
import com.campus.contest.module.organization.entity.OrganizationDO;
import com.campus.contest.module.organization.mapper.ContestEditionScopeMapper;
import com.campus.contest.module.organization.mapper.OrganizationMapper;
import com.campus.contest.module.organization.mapper.OrganizationScopeMapper;
import com.campus.contest.module.organization.model.EffectiveScope;
import com.campus.contest.module.organization.model.ScopeGrantDO;
import com.campus.contest.module.organization.service.impl.OrganizationScopeServiceImpl;
import com.campus.contest.module.system.service.DictionaryQueryService;
import com.campus.contest.security.CurrentUserProvider;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.LocalDateTime;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyCollection;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class OrganizationScopeServiceImplTest {

    private static final Long USER_ID = 7L;

    @Mock
    private OrganizationScopeMapper scopeMapper;
    @Mock
    private OrganizationMapper organizationMapper;
    @Mock
    private ContestEditionScopeMapper contestEditionMapper;
    @Mock
    private DictionaryQueryService dictionaryQueryService;
    @Mock
    private CurrentUserProvider currentUserProvider;

    private OrganizationScopeServiceImpl service;

    @BeforeEach
    void setUp() {
        service = new OrganizationScopeServiceImpl(
                scopeMapper,
                organizationMapper,
                contestEditionMapper,
                dictionaryQueryService,
                currentUserProvider
        );
        lenient().when(currentUserProvider.requireUserId()).thenReturn(USER_ID);
    }

    @Test
    @DisplayName("只加载有效且目标存在的范围授权")
    void loadsOnlyEffectiveAndExistingGrants() {
        LocalDateTime now = LocalDateTime.now();
        ScopeGrantDO schoolGrant = grant("SCHOOL_ADMIN", "ORGANIZATION", 10L);
        ScopeGrantDO provinceGrant = grant("PROVINCE_ADMIN", "PROVINCE", 510000L);
        ScopeGrantDO eventGrant = grant("EVENT_MANAGER", "EVENT", 40L);
        ScopeGrantDO expired = grant("SCHOOL_ADMIN", "ORGANIZATION", 20L);
        expired.setValidTo(now.minusSeconds(1));
        ScopeGrantDO disabled = grant("SCHOOL_ADMIN", "ORGANIZATION", 20L);
        disabled.setStatus(0);
        ScopeGrantDO missingTarget = grant("SCHOOL_ADMIN", "ORGANIZATION", 99L);
        ScopeGrantDO inactiveTarget = grant("SCHOOL_ADMIN", "ORGANIZATION", 30L);
        ScopeGrantDO invalidGlobal = grant("SCHOOL_ADMIN", "GLOBAL", 0L);
        ScopeGrantDO invalidProvince = grant("PROVINCE_ADMIN", "PROVINCE", 430000L);

        when(scopeMapper.selectActiveGrants(eq(USER_ID), any())).thenReturn(
                List.of(
                        schoolGrant,
                        provinceGrant,
                        eventGrant,
                        expired,
                        disabled,
                        missingTarget,
                        inactiveTarget,
                        invalidGlobal,
                        invalidProvince
                )
        );
        when(organizationMapper.selectBatchIds(anyCollection())).thenReturn(List.of(
                organization(10L, "SCHOOL", "510000", 1),
                organization(20L, "SCHOOL", "510000", 1),
                organization(30L, "SCHOOL", "510000", 0)
        ));
        when(contestEditionMapper.selectBatchIds(anyCollection())).thenReturn(List.of(
                event(40L, 10L, null)
        ));
        when(dictionaryQueryService.containsEnabledValue("CN_PROVINCE", "510000")).thenReturn(true);
        when(dictionaryQueryService.containsEnabledValue("CN_PROVINCE", "430000")).thenReturn(false);

        EffectiveScope scope = service.loadRequired(USER_ID);

        assertThat(scope.global()).isFalse();
        assertThat(scope.organizationIds()).containsExactly(10L);
        assertThat(scope.provinceCodes()).containsExactly(510000L);
        assertThat(scope.eventIds()).containsExactly(40L);
    }

    @Test
    @DisplayName("平台 ADMIN 的 GLOBAL/ALL 授权拥有全局范围")
    void globalAdminCanManageAnyExistingOrganization() {
        ScopeGrantDO global = grant("ADMIN", "GLOBAL", 0L);
        global.setDataScope("ALL");
        when(scopeMapper.selectActiveGrants(eq(USER_ID), any())).thenReturn(List.of(global));
        when(organizationMapper.selectById(20L)).thenReturn(organization(20L, "SCHOOL", "430000", 1));

        assertThat(service.loadCurrentRequired().global()).isTrue();
        assertThatCode(() -> service.requireOrganizationManageable(20L)).doesNotThrowAnyException();
    }

    @Test
    @DisplayName("学校管理员跨校查询返回 404，跨校写入返回 403")
    void schoolAdminCannotAccessAnotherSchool() {
        when(scopeMapper.selectActiveGrants(eq(USER_ID), any())).thenReturn(List.of(
                grant("SCHOOL_ADMIN", "ORGANIZATION", 10L)
        ));
        when(organizationMapper.selectBatchIds(anyCollection())).thenReturn(List.of(
                organization(10L, "SCHOOL", "510000", 1)
        ));
        when(organizationMapper.selectById(10L)).thenReturn(organization(10L, "SCHOOL", "510000", 1));
        when(organizationMapper.selectById(20L)).thenReturn(organization(20L, "SCHOOL", "510000", 1));

        assertThatCode(() -> service.requireOrganizationVisible(10L)).doesNotThrowAnyException();
        assertBizCode(() -> service.requireOrganizationVisible(20L), ErrorCode.RESOURCE_NOT_FOUND);
        assertBizCode(() -> service.requireOrganizationManageable(20L), ErrorCode.DATA_FORBIDDEN);
    }

    @Test
    @DisplayName("省级管理员只能访问本省组织和本省组织赛事")
    void provinceAdminIsLimitedToOwnProvince() {
        when(scopeMapper.selectActiveGrants(eq(USER_ID), any())).thenReturn(List.of(
                grant("PROVINCE_ADMIN", "PROVINCE", 510000L)
        ));
        when(dictionaryQueryService.containsEnabledValue("CN_PROVINCE", "510000")).thenReturn(true);
        when(organizationMapper.selectById(10L)).thenReturn(organization(10L, "SCHOOL", "510000", 1));
        when(organizationMapper.selectById(20L)).thenReturn(organization(20L, "SCHOOL", "430000", 1));
        when(contestEditionMapper.selectById(40L)).thenReturn(event(40L, 10L, null));
        when(contestEditionMapper.selectById(41L)).thenReturn(event(41L, 20L, "430000"));

        assertThatCode(() -> service.requireOrganizationManageable(10L)).doesNotThrowAnyException();
        assertThatCode(() -> service.requireEventManageable(40L)).doesNotThrowAnyException();
        assertBizCode(() -> service.requireOrganizationVisible(20L), ErrorCode.RESOURCE_NOT_FOUND);
        assertBizCode(() -> service.requireEventVisible(41L), ErrorCode.RESOURCE_NOT_FOUND);
    }

    @Test
    @DisplayName("赛事负责人只访问被直接授权的赛事")
    void eventManagerIsLimitedToAssignedEvent() {
        when(scopeMapper.selectActiveGrants(eq(USER_ID), any())).thenReturn(List.of(
                grant("EVENT_MANAGER", "EVENT", 40L)
        ));
        when(contestEditionMapper.selectBatchIds(anyCollection())).thenReturn(List.of(
                event(40L, 10L, null)
        ));
        when(contestEditionMapper.selectById(40L)).thenReturn(event(40L, 10L, null));
        when(contestEditionMapper.selectById(41L)).thenReturn(event(41L, 10L, null));

        assertThatCode(() -> service.requireEventManageable(40L)).doesNotThrowAnyException();
        assertBizCode(() -> service.requireEventVisible(41L), ErrorCode.RESOURCE_NOT_FOUND);
        assertBizCode(() -> service.requireEventManageable(41L), ErrorCode.DATA_FORBIDDEN);
    }

    @Test
    @DisplayName("未知省份返回 404，越权省份写入返回 403")
    void provinceChecksDistinguishMissingAndForbidden() {
        when(scopeMapper.selectActiveGrants(eq(USER_ID), any())).thenReturn(List.of(
                grant("PROVINCE_ADMIN", "PROVINCE", 510000L)
        ));
        when(dictionaryQueryService.containsEnabledValue("CN_PROVINCE", "510000")).thenReturn(true);
        when(dictionaryQueryService.containsEnabledValue("CN_PROVINCE", "430000")).thenReturn(true);
        when(dictionaryQueryService.containsEnabledValue("CN_PROVINCE", "999999")).thenReturn(false);

        assertThatCode(() -> service.requireProvinceManageable("510000")).doesNotThrowAnyException();
        assertBizCode(() -> service.requireProvinceManageable("430000"), ErrorCode.DATA_FORBIDDEN);
        assertBizCode(() -> service.requireProvinceManageable("999999"), ErrorCode.RESOURCE_NOT_FOUND);
    }

    private void assertBizCode(Runnable action, ErrorCode expected) {
        assertThatThrownBy(action::run)
                .isInstanceOfSatisfying(BizException.class, error ->
                        assertThat(error.getCode()).isEqualTo(expected.getCode()));
    }

    private ScopeGrantDO grant(String roleCode, String scopeType, Long scopeId) {
        ScopeGrantDO grant = new ScopeGrantDO();
        grant.setAssignmentId(1L);
        grant.setRoleId(1L);
        grant.setRoleCode(roleCode);
        grant.setScopeType(scopeType);
        grant.setScopeId(scopeId);
        grant.setStatus(1);
        grant.setDeleted(0);
        return grant;
    }

    private OrganizationDO organization(Long id, String type, String provinceCode, Integer status) {
        OrganizationDO organization = new OrganizationDO();
        organization.setId(id);
        organization.setOrgType(type);
        organization.setProvinceCode(provinceCode);
        organization.setStatus(status);
        organization.setDeleted(0);
        return organization;
    }

    private ContestEditionScopeDO event(Long id, Long ownerOrgId, String provinceCode) {
        ContestEditionScopeDO event = new ContestEditionScopeDO();
        event.setId(id);
        event.setOwnerOrgId(ownerOrgId);
        event.setProvinceCode(provinceCode);
        event.setDeleted(0);
        return event;
    }
}
