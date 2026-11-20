package com.campus.contest.module.contest;

import com.campus.contest.common.BizException;
import com.campus.contest.common.ErrorCode;
import com.campus.contest.module.contest.entity.ContestEditionDO;
import com.campus.contest.module.contest.enums.ContestLevel;
import com.campus.contest.module.contest.service.ContestAuthorizationService;
import com.campus.contest.module.organization.mapper.OrganizationMapper;
import com.campus.contest.module.organization.model.EffectiveScope;
import com.campus.contest.module.organization.service.OrganizationScopeService;
import com.campus.contest.module.system.mapper.SysUserMapper;
import com.campus.contest.security.CurrentUserProvider;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class ContestAuthorizationServiceTest {

    private final OrganizationScopeService scopeService = mock(OrganizationScopeService.class);
    private final CurrentUserProvider currentUserProvider = mock(CurrentUserProvider.class);
    private final SysUserMapper userMapper = mock(SysUserMapper.class);
    private final OrganizationMapper organizationMapper = mock(OrganizationMapper.class);

    private ContestAuthorizationService service;

    @BeforeEach
    void setUp() {
        service = new ContestAuthorizationService(
                scopeService,
                currentUserProvider,
                userMapper,
                organizationMapper
        );
    }

    @Test
    @DisplayName("省级管理员不能创建同省非直属学校比赛")
    void provinceAdminCannotCreateSchoolEvent() {
        when(scopeService.loadCurrentRequired()).thenReturn(
                new EffectiveScope(false, Set.of(), Set.of(510000L), Set.of())
        );

        assertThatThrownBy(() ->
                service.requireCreateScope(ContestLevel.SCHOOL, 2L, null))
                .isInstanceOfSatisfying(BizException.class, error ->
                        assertThat(error.getCode()).isEqualTo(ErrorCode.DATA_FORBIDDEN.getCode()));
    }

    @Test
    @DisplayName("省级管理员不能通过赛事 ID 修改同省学校比赛")
    void provinceAdminCannotManageSchoolEventById() {
        when(scopeService.loadCurrentRequired()).thenReturn(
                new EffectiveScope(false, Set.of(), Set.of(510000L), Set.of())
        );
        ContestEditionDO schoolEvent = event(31L, "SCHOOL", 2L, null);

        assertThatThrownBy(() -> service.requireContestEventManageable(schoolEvent))
                .isInstanceOfSatisfying(BizException.class, error ->
                        assertThat(error.getCode()).isEqualTo(ErrorCode.DATA_FORBIDDEN.getCode()));
    }

    @Test
    @DisplayName("EVENT 负责人只能操作被授权的具体赛事")
    void eventGrantOnlyCoversAssignedEvent() {
        when(scopeService.loadCurrentRequired()).thenReturn(
                new EffectiveScope(false, Set.of(), Set.of(), Set.of(31L))
        );

        assertThatCode(() ->
                service.requireContestEventManageable(
                        event(31L, "SCHOOL", 2L, null)
                )).doesNotThrowAnyException();

        assertThatThrownBy(() ->
                service.requireContestEventManageable(
                        event(32L, "SCHOOL", 3L, null)
                ))
                .isInstanceOfSatisfying(BizException.class, error ->
                        assertThat(error.getCode()).isEqualTo(ErrorCode.DATA_FORBIDDEN.getCode()));
    }

    @Test
    @DisplayName("直属学校授权仍可管理本校比赛")
    void directSchoolGrantCanManageSchoolEvent() {
        when(scopeService.loadCurrentRequired()).thenReturn(
                new EffectiveScope(false, Set.of(2L), Set.of(), Set.of())
        );

        assertThatCode(() ->
                service.requireContestEventManageable(
                        event(31L, "SCHOOL", 2L, null)
                )).doesNotThrowAnyException();
    }

    private ContestEditionDO event(
            Long id,
            String level,
            Long ownerOrgId,
            String provinceCode
    ) {
        ContestEditionDO edition = new ContestEditionDO();
        edition.setId(id);
        edition.setLevel(level);
        edition.setOwnerOrgId(ownerOrgId);
        edition.setProvinceCode(provinceCode);
        return edition;
    }
}
