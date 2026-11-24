package com.campus.contest.module.team;

import com.campus.contest.TestInfrastructure;
import com.campus.contest.common.BizException;
import com.campus.contest.common.ErrorCode;
import com.campus.contest.module.contest.entity.ContestEditionDO;
import com.campus.contest.module.contest.enums.EditionStatus;
import com.campus.contest.module.organization.entity.OrganizationDO;
import com.campus.contest.module.organization.mapper.OrganizationMapper;
import com.campus.contest.module.system.entity.SysUserDO;
import com.campus.contest.module.system.mapper.SysUserMapper;
import com.campus.contest.module.team.dto.TeamCreateRequest;
import com.campus.contest.module.team.dto.TeamMemberInput;
import com.campus.contest.module.team.dto.TeamUpdateRequest;
import com.campus.contest.module.team.entity.TeamDO;
import com.campus.contest.module.team.entity.TeamMemberDO;
import com.campus.contest.module.team.enums.TeamStatus;
import com.campus.contest.module.team.mapper.TeamMapper;
import com.campus.contest.module.team.mapper.TeamMemberMapper;
import com.campus.contest.module.team.service.TeamAuditService;
import com.campus.contest.module.team.service.TeamAuthorizationService;
import com.campus.contest.module.team.service.impl.TeamServiceImpl;
import com.campus.contest.module.team.vo.TeamCreateVO;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.LocalDateTime;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class TeamServiceImplTest {

    private static final Long USER_ID = 9L;
    private static final Long SCHOOL_ID = 10L;
    private static final Long TEAM_ID = 100L;

    @Mock
    private TeamMapper teamMapper;
    @Mock
    private TeamMemberMapper memberMapper;
    @Mock
    private SysUserMapper userMapper;
    @Mock
    private OrganizationMapper organizationMapper;
    @Mock
    private TeamAuthorizationService authorizationService;
    @Mock
    private TeamAuditService auditService;

    private TeamServiceImpl service;

    @BeforeEach
    void setUp() {
        service = new TeamServiceImpl(
                teamMapper,
                memberMapper,
                userMapper,
                organizationMapper,
                authorizationService,
                auditService,
                TestInfrastructure.passthroughIdempotencyService(),
                TestInfrastructure.transactionTemplate()
        );
        lenient().when(authorizationService.currentUserId()).thenReturn(USER_ID);
    }

    @Test
    @DisplayName("已验证学校的学生创建 DRAFT 队伍并自动写入队长快照")
    void createsDraftTeamWithLeaderMember() {
        when(authorizationService.requireVisibleEdition(1L)).thenReturn(edition(1L));
        when(userMapper.selectById(USER_ID)).thenReturn(user("S001"));
        when(organizationMapper.selectById(SCHOOL_ID)).thenReturn(school());
        when(teamMapper.selectCount(any())).thenReturn(0L);
        when(memberMapper.selectCount(any())).thenReturn(0L);
        when(teamMapper.insert(any(TeamDO.class))).thenAnswer(invocation -> {
            TeamDO team = invocation.getArgument(0);
            team.setId(TEAM_ID);
            return 1;
        });
        when(memberMapper.insert(any(TeamMemberDO.class))).thenAnswer(invocation -> {
            TeamMemberDO member = invocation.getArgument(0);
            member.setId(200L);
            return 1;
        });

        TeamCreateVO result = service.create(1L, createRequest("统计建模一队"), "create-key");

        assertThat(result.id()).isEqualTo("100");
        assertThat(result.status()).isEqualTo("DRAFT");
        assertThat(result.teamCode()).startsWith("T").hasSize(15);
        assertThat(result.leaderMemberId()).isEqualTo("200");
        verify(teamMapper).insert(any(TeamDO.class));
        verify(memberMapper).insert(any(TeamMemberDO.class));
        verify(auditService).record(any(), any(), any());
    }

    @Test
    @DisplayName("学校归属未审核通过时不能创建队伍")
    void rejectsUnverifiedLeaderSchool() {
        when(authorizationService.requireVisibleEdition(1L)).thenReturn(edition(1L));
        SysUserDO user = user("S001");
        user.setSchoolVerificationStatus("PENDING");
        when(userMapper.selectById(USER_ID)).thenReturn(user);

        assertThatThrownBy(() -> service.create(1L, createRequest("未审核队伍"), "create-key"))
                .isInstanceOfSatisfying(BizException.class, error ->
                        assertThat(error.getCode()).isEqualTo(ErrorCode.DATA_FORBIDDEN.getCode()));
        verify(teamMapper, never()).insert(any(TeamDO.class));
    }

    @Test
    @DisplayName("同一届次同校同学号已有的队长不能重复建队")
    void rejectsDuplicateLeaderStudentNo() {
        when(authorizationService.requireVisibleEdition(1L)).thenReturn(edition(1L));
        when(userMapper.selectById(USER_ID)).thenReturn(user("s001"));
        when(organizationMapper.selectById(SCHOOL_ID)).thenReturn(school());
        when(teamMapper.selectCount(any())).thenReturn(0L);
        when(memberMapper.selectCount(any())).thenReturn(1L);

        assertThatThrownBy(() -> service.create(1L, createRequest("重复队伍"), "create-key"))
                .isInstanceOfSatisfying(BizException.class, error ->
                        assertThat(error.getCode()).isEqualTo(ErrorCode.DUPLICATE_RELATION.getCode()));
        verify(teamMapper, never()).insert(any(TeamDO.class));
    }

    @Test
    @DisplayName("普通队员超过 4 人时修改失败")
    void rejectsTooManyNormalMembers() {
        when(teamMapper.selectActiveByIdForUpdate(TEAM_ID)).thenReturn(team());
        when(memberMapper.selectList(any())).thenReturn(List.of(leaderMember()));
        TeamUpdateRequest request = updateRequest(0);
        request.setMembers(List.of(
                member(null, "甲", "S01"),
                member(null, "乙", "S02"),
                member(null, "丙", "S03"),
                member(null, "丁", "S04"),
                member(null, "戊", "S05")
        ));

        assertThatThrownBy(() -> service.update(TEAM_ID, request, null))
                .isInstanceOfSatisfying(BizException.class, error ->
                        assertThat(error.getCode()).isEqualTo(ErrorCode.INVALID_PARAM.getCode()));
        verify(teamMapper, never()).updateById(any(TeamDO.class));
    }

    @Test
    @DisplayName("同一学校同一学号在成员列表中重复时修改失败")
    void rejectsDuplicateMemberStudentNo() {
        when(teamMapper.selectActiveByIdForUpdate(TEAM_ID)).thenReturn(team());
        when(memberMapper.selectList(any())).thenReturn(List.of(leaderMember()));
        when(organizationMapper.selectById(SCHOOL_ID)).thenReturn(school());
        TeamUpdateRequest request = updateRequest(0);
        request.setMembers(List.of(
                member(null, "甲", "s01"),
                member(null, "乙", " S01 ")
        ));

        assertThatThrownBy(() -> service.update(TEAM_ID, request, null))
                .isInstanceOfSatisfying(BizException.class, error ->
                        assertThat(error.getCode()).isEqualTo(ErrorCode.INVALID_PARAM.getCode()));
        verify(teamMapper, never()).updateById(any(TeamDO.class));
    }

    @Test
    @DisplayName("队长成员不能被移出")
    void rejectsRemovingLeaderMember() {
        when(teamMapper.selectActiveByIdForUpdate(TEAM_ID)).thenReturn(team());
        when(memberMapper.selectById(500L)).thenReturn(leaderMember());

        assertThatThrownBy(() -> service.removeMember(TEAM_ID, 500L, 0, null))
                .isInstanceOfSatisfying(BizException.class, error ->
                        assertThat(error.getCode()).isEqualTo(ErrorCode.INVALID_PARAM.getCode()));
        verify(memberMapper, never()).updateById(any(TeamMemberDO.class));
    }

    private TeamCreateRequest createRequest(String teamName) {
        TeamCreateRequest request = new TeamCreateRequest();
        request.setTeamName(teamName);
        return request;
    }

    private TeamUpdateRequest updateRequest(int version) {
        TeamUpdateRequest request = new TeamUpdateRequest();
        request.setVersion(version);
        return request;
    }

    private TeamMemberInput member(String memberId, String name, String studentNo) {
        TeamMemberInput input = new TeamMemberInput();
        input.setMemberId(memberId);
        input.setMemberName(name);
        input.setSchoolId(String.valueOf(SCHOOL_ID));
        input.setStudentNo(studentNo);
        return input;
    }

    private SysUserDO user(String studentNo) {
        SysUserDO user = new SysUserDO();
        user.setId(USER_ID);
        user.setRealName("队长甲");
        user.setStudentNo(studentNo);
        user.setSchoolId(SCHOOL_ID);
        user.setSchoolVerificationStatus("VERIFIED");
        user.setStatus(1);
        return user;
    }

    private OrganizationDO school() {
        OrganizationDO school = new OrganizationDO();
        school.setId(SCHOOL_ID);
        school.setOrgType("SCHOOL");
        school.setStatus(1);
        return school;
    }

    private ContestEditionDO edition(Long id) {
        ContestEditionDO edition = new ContestEditionDO();
        edition.setId(id);
        edition.setStatus(EditionStatus.REGISTERING.getDatabaseValue());
        edition.setMinTeamSize(1);
        edition.setMaxTeamSize(5);
        edition.setCrossSchoolTeamAllowed(0);
        edition.setEntryMode("OPEN");
        edition.setRegisterDeadline(LocalDateTime.now().plusDays(7));
        return edition;
    }

    private TeamDO team() {
        TeamDO team = new TeamDO();
        team.setId(TEAM_ID);
        team.setEditionId(1L);
        team.setTeamName("统计建模一队");
        team.setTeamCode("T12345678901234");
        team.setLeaderId(USER_ID);
        team.setStatus(TeamStatus.DRAFT.databaseValue());
        team.setMemberCount(1);
        team.setVersion(0);
        team.setDeleted(0);
        return team;
    }

    private TeamMemberDO leaderMember() {
        TeamMemberDO member = new TeamMemberDO();
        member.setId(500L);
        member.setTeamId(TEAM_ID);
        member.setEditionId(1L);
        member.setUserId(USER_ID);
        member.setMemberName("队长甲");
        member.setSchoolId(SCHOOL_ID);
        member.setStudentNo("S001");
        member.setIsLeader(1);
        member.setStatus(1);
        member.setJoinTime(LocalDateTime.now());
        member.setVersion(0);
        return member;
    }
}
