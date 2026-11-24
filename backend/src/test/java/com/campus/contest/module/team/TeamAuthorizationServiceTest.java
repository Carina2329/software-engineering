package com.campus.contest.module.team;

import com.campus.contest.common.BizException;
import com.campus.contest.common.ErrorCode;
import com.campus.contest.module.contest.entity.ContestEditionDO;
import com.campus.contest.module.contest.mapper.ContestAccessCriteria;
import com.campus.contest.module.contest.mapper.ContestAccessMapper;
import com.campus.contest.module.contest.mapper.ContestEditionMapper;
import com.campus.contest.module.contest.service.ContestAuthorizationService;
import com.campus.contest.module.organization.entity.OrganizationDO;
import com.campus.contest.module.organization.mapper.OrganizationMapper;
import com.campus.contest.module.organization.model.EffectiveScope;
import com.campus.contest.module.system.entity.SysUserDO;
import com.campus.contest.module.system.mapper.SysUserMapper;
import com.campus.contest.module.team.entity.TeamDO;
import com.campus.contest.module.team.mapper.TeamTeacherApplicationMapper;
import com.campus.contest.module.team.service.TeamAuthorizationService;
import com.campus.contest.security.CurrentUserProvider;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class TeamAuthorizationServiceTest {

    @Mock
    private CurrentUserProvider currentUserProvider;
    @Mock
    private ContestAuthorizationService contestAuthorizationService;
    @Mock
    private ContestAccessMapper contestAccessMapper;
    @Mock
    private ContestEditionMapper contestEditionMapper;
    @Mock
    private SysUserMapper userMapper;
    @Mock
    private OrganizationMapper organizationMapper;
    @Mock
    private TeamTeacherApplicationMapper teacherApplicationMapper;

    private TeamAuthorizationService service;

    @BeforeEach
    void setUp() {
        service = new TeamAuthorizationService(
                currentUserProvider,
                contestAuthorizationService,
                contestAccessMapper,
                contestEditionMapper,
                userMapper,
                organizationMapper,
                teacherApplicationMapper
        );
        when(currentUserProvider.requireUserId()).thenReturn(20L);
    }

    @Test
    @DisplayName("学校管理员可以查看组织范围内的队伍")
    void schoolAdminCanReadScopedTeam() {
        when(contestAuthorizationService.currentScope()).thenReturn(
                new EffectiveScope(false, Set.of(10L), Set.of(), Set.of())
        );
        TeamDO team = team();
        ContestEditionDO edition = edition();
        edition.setSchoolId(10L);

        assertThatCode(() -> service.requireReadable(team, edition))
                .doesNotThrowAnyException();
    }

    @Test
    @DisplayName("省份管理员可以查看队长学校属于本省的队伍")
    void provinceAdminCanReadTeamByLeaderSchoolProvince() {
        when(contestAuthorizationService.currentScope()).thenReturn(
                new EffectiveScope(false, Set.of(), Set.of(510000L), Set.of())
        );
        TeamDO team = team();
        SysUserDO leader = new SysUserDO();
        leader.setId(9L);
        leader.setSchoolId(10L);
        when(userMapper.selectById(9L)).thenReturn(leader);
        OrganizationDO school = new OrganizationDO();
        school.setId(10L);
        school.setOrgType("SCHOOL");
        school.setProvinceCode("510000");
        when(organizationMapper.selectById(10L)).thenReturn(school);

        assertThatCode(() -> service.requireReadable(team, edition()))
                .doesNotThrowAnyException();
    }

    @Test
    @DisplayName("赛事授权管理员可以查看对应届次队伍")
    void eventAdminCanReadScopedEventTeam() {
        when(contestAuthorizationService.currentScope()).thenReturn(
                new EffectiveScope(false, Set.of(), Set.of(), Set.of(1L))
        );

        assertThatCode(() -> service.requireReadable(team(), edition()))
                .doesNotThrowAnyException();
    }

    @Test
    @DisplayName("外校待确认教师可以读取不可见届次的待审核队伍")
    void pendingTeacherCanReadUnlistedEdition() {
        TeamDO team = team();
        team.setCurrentRegistrationId(700L);
        ContestEditionDO edition = edition();
        when(teacherApplicationMapper.selectCount(any())).thenReturn(1L);
        when(contestEditionMapper.selectById(1L)).thenReturn(edition);

        org.assertj.core.api.Assertions.assertThat(
                service.requireEditionForTeamRead(team)
        ).isSameAs(edition);
    }
    @Test
    @DisplayName("外校待确认教师可以通过有效申请列出不可见届次队伍")
    void pendingTeacherCanListUnlistedEditionTeams() {
        ContestEditionDO edition = edition();
        when(contestAuthorizationService.currentAccessCriteria())
                .thenReturn(new ContestAccessCriteria());
        when(contestAccessMapper.selectEditionById(any())).thenReturn(null);
        when(teacherApplicationMapper.countEffectiveForEdition(20L, 1L)).thenReturn(1L);
        when(contestEditionMapper.selectById(1L)).thenReturn(edition);

        org.assertj.core.api.Assertions.assertThat(
                service.requireEditionForList(1L)
        ).isSameAs(edition);
    }
    @Test
    @DisplayName("教师确认前可以通过待处理申请查看队伍")
    void pendingTeacherCanReadTeamBeforeConfirmation() {
        when(contestAuthorizationService.currentScope()).thenReturn(
                new EffectiveScope(false, Set.of(), Set.of(), Set.of())
        );
        TeamDO team = team();
        team.setCurrentRegistrationId(700L);
        when(teacherApplicationMapper.selectCount(any())).thenReturn(1L);

        assertThatCode(() -> service.requireReadable(team, edition()))
                .doesNotThrowAnyException();
    }

    @Test
    @DisplayName("无队伍或范围关系时拒绝查看")
    void unrelatedUserCannotReadTeam() {
        when(contestAuthorizationService.currentScope()).thenReturn(
                new EffectiveScope(false, Set.of(), Set.of(), Set.of())
        );
        when(userMapper.selectById(9L)).thenReturn(null);

        assertThatThrownBy(() -> service.requireReadable(team(), edition()))
                .isInstanceOfSatisfying(BizException.class, error ->
                        error.getCode().equals(ErrorCode.DATA_FORBIDDEN.getCode()));
    }

    private TeamDO team() {
        TeamDO team = new TeamDO();
        team.setId(100L);
        team.setEditionId(1L);
        team.setLeaderId(9L);
        return team;
    }

    private ContestEditionDO edition() {
        ContestEditionDO edition = new ContestEditionDO();
        edition.setId(1L);
        edition.setOwnerOrgId(99L);
        return edition;
    }
}
