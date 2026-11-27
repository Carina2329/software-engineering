package com.campus.contest.module.team;

import com.baomidou.mybatisplus.core.MybatisConfiguration;
import com.baomidou.mybatisplus.core.conditions.Wrapper;
import com.baomidou.mybatisplus.core.metadata.TableInfoHelper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.campus.contest.TestInfrastructure;
import com.campus.contest.common.BizException;
import com.campus.contest.common.ErrorCode;
import com.campus.contest.common.idempotency.IdempotencyScope;
import com.campus.contest.common.idempotency.IdempotencyService;
import com.campus.contest.module.contest.entity.ContestEditionDO;
import com.campus.contest.module.contest.enums.EditionStatus;
import com.campus.contest.module.organization.entity.OrganizationDO;
import com.campus.contest.module.organization.mapper.OrganizationMapper;
import com.campus.contest.module.system.dto.AuthorizationSnapshot;
import com.campus.contest.module.system.entity.SysUserDO;
import com.campus.contest.module.system.mapper.SysUserMapper;
import com.campus.contest.module.system.service.AuthorizationSnapshotService;
import com.campus.contest.module.team.dto.TeamApprovalRequest;
import com.campus.contest.module.team.dto.TeamCreateRequest;
import com.campus.contest.module.team.dto.AvailableTeacherQuery;
import com.campus.contest.module.team.dto.TeamMemberInput;
import com.campus.contest.module.team.dto.TeamPageQuery;
import com.campus.contest.module.team.dto.TeamRegistrationSubmitRequest;
import com.campus.contest.module.team.dto.TeamUpdateRequest;
import com.campus.contest.module.team.entity.TeamDO;
import com.campus.contest.module.team.entity.TeamMemberDO;
import com.campus.contest.module.team.entity.TeamRegistrationDO;
import com.campus.contest.module.team.entity.TeamTeacherApplicationDO;
import com.campus.contest.module.team.enums.TeamStatus;
import com.campus.contest.module.team.mapper.TeamMapper;
import com.campus.contest.module.team.mapper.TeamMemberMapper;
import com.campus.contest.module.team.mapper.TeamRegistrationMapper;
import com.campus.contest.module.team.mapper.TeamTeacherApplicationMapper;
import com.campus.contest.module.team.mapper.AvailableTeacherMapper;
import com.campus.contest.module.team.mapper.AvailableTeacherMapper.AvailableTeacherRow;
import com.campus.contest.module.team.service.TeamAuditService;
import com.campus.contest.module.team.service.TeamAuthorizationService;
import com.campus.contest.module.team.service.TeamReadScope;
import com.campus.contest.module.team.service.impl.TeamServiceImpl;
import com.campus.contest.module.team.support.model.RegistrationEligibilityResult;
import com.campus.contest.module.team.support.model.SchoolQuotaReservation;
import com.campus.contest.module.team.support.service.RegistrationEligibilityService;
import com.campus.contest.module.team.support.service.SchoolQuotaService;
import com.campus.contest.module.team.vo.TeamApprovalVO;
import com.campus.contest.module.team.vo.TeamCreateVO;
import com.campus.contest.module.team.vo.TeamRegistrationSubmitVO;
import com.campus.contest.module.team.vo.TeamRegistrationWithdrawVO;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.apache.ibatis.builder.MapperBuilderAssistant;
import org.junit.jupiter.api.BeforeAll;
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

import java.time.Clock;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.Duration;
import java.time.ZoneId;
import java.util.List;
import java.util.Set;
import java.util.HashMap;
import java.util.Map;
import java.util.function.Supplier;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.mockito.Mockito.doAnswer;

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
    private TeamRegistrationMapper registrationMapper;
    @Mock
    private TeamTeacherApplicationMapper teacherApplicationMapper;
    @Mock
    private AvailableTeacherMapper availableTeacherMapper;
    @Mock
    private SysUserMapper userMapper;
    @Mock
    private OrganizationMapper organizationMapper;
    @Mock
    private TeamAuthorizationService authorizationService;
    @Mock
    private RegistrationEligibilityService eligibilityService;
    @Mock
    private SchoolQuotaService quotaService;
    @Mock
    private AuthorizationSnapshotService authorizationSnapshotService;
    @Mock
    private TeamAuditService auditService;

    private RecordingTransactionManager transactionManager;
    private TeamServiceImpl service;

    @BeforeAll
    static void initializeTableInfo() {
        TableInfoHelper.initTableInfo(
                new MapperBuilderAssistant(new MybatisConfiguration(), ""),
                TeamDO.class
        );
    }
    @BeforeEach
    void setUp() {
        transactionManager = new RecordingTransactionManager();
        Clock clock = Clock.fixed(
                Instant.parse("2026-11-24T10:00:00Z"),
                ZoneId.of("Asia/Shanghai")
        );
        service = new TeamServiceImpl(
                teamMapper,
                memberMapper,
                registrationMapper,
                teacherApplicationMapper,
                availableTeacherMapper,
                userMapper,
                organizationMapper,
                authorizationService,
                eligibilityService,
                quotaService,
                authorizationSnapshotService,
                auditService,
                TestInfrastructure.passthroughIdempotencyService(),
                new ObjectMapper(),
                clock,
                new TransactionTemplate(transactionManager)
        );
        lenient().when(authorizationService.currentUserId()).thenReturn(USER_ID);
        lenient().doAnswer(invocation -> edition(1L)).when(authorizationService).requireVisibleEdition(org.mockito.ArgumentMatchers.anyLong());
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
    @DisplayName("学生队长可按关键字分页查询可用指导教师")
    void studentCanListAvailableTeachers() {
        when(userMapper.selectById(USER_ID)).thenReturn(user("S001"));
        when(organizationMapper.selectById(SCHOOL_ID)).thenReturn(school());
        when(authorizationSnapshotService.loadRequired(USER_ID)).thenReturn(
                new AuthorizationSnapshot(USER_ID, "leader", 1, Set.of("STUDENT"), Set.of(), Set.of())
        );
        when(availableTeacherMapper.count("张")).thenReturn(21L);
        when(availableTeacherMapper.selectPage("张", 10, 10)).thenReturn(List.of(
                new AvailableTeacherRow(
                        8L,
                        "张老师",
                        SCHOOL_ID,
                        "测试学校",
                        "计算机学院",
                        "副教授"
                )
        ));
        AvailableTeacherQuery query = new AvailableTeacherQuery();
        query.setKeyword(" 张 ");
        query.setPage(2);
        query.setSize(10);

        var result = service.availableTeachers(1L, query);

        assertThat(result.total()).isEqualTo(21);
        assertThat(result.items()).containsExactly(new com.campus.contest.module.team.vo.AvailableTeacherVO(
                "8", "张老师", String.valueOf(SCHOOL_ID), "测试学校", "计算机学院", "副教授"
        ));
        assertThat(result.hasMore()).isTrue();
        verify(availableTeacherMapper).selectPage("张", 10, 10);
    }

    @Test
    @DisplayName("管理员报名终审可按教师姓名查询教师选项")
    void adminCanListApprovalTeachers() {
        when(authorizationService.requireEditionForApprovalFilter(1L)).thenReturn(edition(1L));
        when(availableTeacherMapper.count("张")).thenReturn(1L);
        when(availableTeacherMapper.selectPage("张", 0, 20)).thenReturn(List.of(
                new AvailableTeacherRow(
                        8L, "张老师", SCHOOL_ID, "测试学校", "计算机学院", "副教授"
                )
        ));
        AvailableTeacherQuery query = new AvailableTeacherQuery();
        query.setKeyword(" 张 ");

        var result = service.approvalTeachers(1L, query);

        assertThat(result.items()).singleElement().satisfies(item -> {
            assertThat(item.id()).isEqualTo("8");
            assertThat(item.realName()).isEqualTo("张老师");
        });
        verify(authorizationService).requireEditionForApprovalFilter(1L);
    }

    @Test
    @DisplayName("非学生账号不能查询指导教师")
    void nonStudentCannotListAvailableTeachers() {
        when(userMapper.selectById(USER_ID)).thenReturn(user("S001"));
        when(organizationMapper.selectById(SCHOOL_ID)).thenReturn(school());
        when(authorizationSnapshotService.loadRequired(USER_ID)).thenReturn(
                new AuthorizationSnapshot(USER_ID, "teacher", 1, Set.of("TEACHER"), Set.of(), Set.of())
        );

        assertThatThrownBy(() -> service.availableTeachers(1L, new AvailableTeacherQuery()))
                .isInstanceOfSatisfying(BizException.class, error ->
                        assertThat(error.getCode()).isEqualTo(ErrorCode.DATA_FORBIDDEN.getCode()));
        verify(availableTeacherMapper, never()).selectPage(anyString(), anyInt(), anyInt());
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

    @Test
    @DisplayName("资格通过时创建报名轮次、占用名额并创建教师申请")
    void submitsRegistrationAndCreatesRound() {
        TeamDO team = team();
        when(teamMapper.selectActiveByIdForUpdate(TEAM_ID)).thenReturn(team);
        when(authorizationService.requireVisibleEdition(1L)).thenReturn(edition(1L));
        when(eligibilityService.checkCanRegister(any())).thenReturn(eligible());
        when(userMapper.selectById(8L)).thenReturn(teacher());
        when(organizationMapper.selectById(SCHOOL_ID)).thenReturn(school());
        when(authorizationSnapshotService.loadRequired(8L)).thenReturn(teacherSnapshot());
        when(memberMapper.selectList(any())).thenReturn(List.of(leaderMember()));
        when(registrationMapper.selectMaxRoundNo(TEAM_ID)).thenReturn(0);
        when(registrationMapper.insert(any(TeamRegistrationDO.class))).thenAnswer(invocation -> {
            TeamRegistrationDO registration = invocation.getArgument(0);
            registration.setId(700L);
            return 1;
        });
        when(quotaService.occupy(700L, 1L, SCHOOL_ID))
                .thenReturn(new SchoolQuotaReservation(1L, SCHOOL_ID, 10, 1, true, true));
        when(teamMapper.updateById(any(TeamDO.class))).thenReturn(1);

        TeamRegistrationSubmitVO result = service.submitRegistration(
                TEAM_ID, registrationRequest(), "submit-key"
        );

        assertThat(result.registrationId()).isEqualTo("700");
        assertThat(result.roundNo()).isEqualTo(1);
        assertThat(result.status()).isEqualTo("PENDING_TEACHER");
        verify(registrationMapper).insert(any(TeamRegistrationDO.class));
        verify(quotaService).occupy(700L, 1L, SCHOOL_ID);
        verify(teacherApplicationMapper).insert(any(TeamTeacherApplicationDO.class));
        verify(teamMapper).updateById(any(TeamDO.class));
    }

    @Test
    @DisplayName("资格不通过时返回 409 数据且不创建报名轮次")
    void rejectsSubmissionWhenEligibilityFails() {
        when(teamMapper.selectActiveByIdForUpdate(TEAM_ID)).thenReturn(team());
        when(authorizationService.requireVisibleEdition(1L)).thenReturn(edition(1L));
        when(eligibilityService.checkCanRegister(any())).thenReturn(new RegistrationEligibilityResult(
                false, "NOT_QUALIFIED", "当前队伍不在晋级名单中",
                null, SCHOOL_ID, null, null, null
        ));

        assertThatThrownBy(() -> service.submitRegistration(
                TEAM_ID, registrationRequest(), "submit-key"
        )).isInstanceOfSatisfying(BizException.class, error -> {
            assertThat(error.getCode()).isEqualTo(ErrorCode.BUSINESS_CONFLICT.getCode());
            assertThat(error.getData()).isInstanceOf(java.util.Map.class);
        });
        verify(registrationMapper, never()).insert(any(TeamRegistrationDO.class));
        verify(quotaService, never()).occupy(any(), any(), any());
    }

    @Test
    @DisplayName("队长撤回当前报名时释放名额并创建新的报名版本")
    void withdrawsRegistrationAndReleasesQuota() {
        TeamDO team = team();
        team.setStatus(TeamStatus.PENDING_TEACHER.databaseValue());
        team.setCurrentRegistrationId(700L);
        TeamRegistrationDO registration = registration();
        when(teamMapper.selectActiveByIdForUpdate(TEAM_ID)).thenReturn(team);
        when(registrationMapper.selectActiveByIdForUpdate(700L)).thenReturn(registration);
        when(registrationMapper.selectById(700L)).thenReturn(registration);
        org.mockito.Mockito.doAnswer(invocation -> {
            registration.setVersion(2);
            return null;
        }).when(quotaService).release(700L);
        when(teacherApplicationMapper.withdrawCurrent(
                700L, USER_ID, LocalDateTime.of(2026, 11, 24, 18, 0), "队长撤回报名"
        )).thenReturn(1);
        when(registrationMapper.updateById(any(TeamRegistrationDO.class))).thenReturn(1);
        when(teamMapper.withdrawAndClearTeacher(eq(TEAM_ID), eq(0), eq(USER_ID), any(), eq(5)))
                .thenReturn(1);

        TeamRegistrationWithdrawVO result = service.withdrawRegistration(
                TEAM_ID, 1, "withdraw-key"
        );

        assertThat(result.status()).isEqualTo("WITHDRAWN");
        assertThat(result.version()).isEqualTo(3);
        ArgumentCaptor<TeamRegistrationDO> registrationCaptor =
                ArgumentCaptor.forClass(TeamRegistrationDO.class);
        verify(quotaService).release(700L);
        verify(registrationMapper).updateById(registrationCaptor.capture());
        assertThat(registrationCaptor.getValue().getVersion()).isEqualTo(2);
        assertThat(registrationCaptor.getValue().getStatus()).isEqualTo("WITHDRAWN");
        verify(teamMapper).withdrawAndClearTeacher(eq(TEAM_ID), eq(0), eq(USER_ID), any(), eq(5));
    }
    @Test
    @DisplayName("移出成员时在一个事务中完成成员删除、人数更新和审计")
    void removeMemberCommitsWholeTransaction() {
        TeamDO team = team();
        TeamMemberDO member = normalMember();
        when(teamMapper.selectActiveByIdForUpdate(TEAM_ID)).thenReturn(team);
        when(authorizationService.requireVisibleEdition(1L)).thenReturn(edition(1L));
        when(memberMapper.selectById(600L)).thenReturn(member);
        when(memberMapper.markInactive(eq(600L), eq(0), eq(USER_ID), any()))
                .thenReturn(1);
        when(teamMapper.updateById(any(TeamDO.class))).thenReturn(1);

        service.removeMember(TEAM_ID, 600L, 0, "remove-key");

        assertThat(transactionManager.committed).isTrue();
        assertThat(transactionManager.rolledBack).isFalse();
        verify(memberMapper).markInactive(eq(600L), eq(0), eq(USER_ID), any());
        verify(teamMapper).updateById(any(TeamDO.class));
        verify(auditService).record(eq(USER_ID), eq("TEAM_MEMBER_REMOVE"), any());
    }

    @Test
    @DisplayName("移出成员审计失败时整个事务回滚")
    void removeMemberRollsBackWhenAuditFails() {
        TeamDO team = team();
        TeamMemberDO member = normalMember();
        when(teamMapper.selectActiveByIdForUpdate(TEAM_ID)).thenReturn(team);
        when(authorizationService.requireVisibleEdition(1L)).thenReturn(edition(1L));
        when(memberMapper.selectById(600L)).thenReturn(member);
        when(memberMapper.markInactive(eq(600L), eq(0), eq(USER_ID), any()))
                .thenReturn(1);
        when(teamMapper.updateById(any(TeamDO.class))).thenReturn(1);
        doAnswer(invocation -> {
            throw new IllegalStateException("audit failed");
        }).when(auditService).record(any(), any(), any());

        assertThatThrownBy(() -> service.removeMember(TEAM_ID, 600L, 0, "remove-key"))
                .isInstanceOf(IllegalStateException.class);

        assertThat(transactionManager.rolledBack).isTrue();
        assertThat(transactionManager.committed).isFalse();
    }

    @Test
    @DisplayName("报名截止后不能创建、修改或移出成员")
    void rejectsEditableOperationsAfterDeadline() {
        ContestEditionDO closed = edition(1L);
        closed.setRegisterDeadline(LocalDateTime.of(2026, 11, 24, 17, 59));
        when(authorizationService.requireVisibleEdition(1L)).thenReturn(closed);

        assertThatThrownBy(() -> service.create(1L, createRequest("截止队伍"), "create-key"))
                .isInstanceOfSatisfying(BizException.class, error ->
                        assertThat(error.getCode()).isEqualTo(ErrorCode.BUSINESS_DEADLINE_PASSED.getCode()));

        when(teamMapper.selectActiveByIdForUpdate(TEAM_ID)).thenReturn(team());
        TeamUpdateRequest update = updateRequest(0);
        update.setTeamName("截止后修改");
        assertThatThrownBy(() -> service.update(TEAM_ID, update, null))
                .isInstanceOfSatisfying(BizException.class, error ->
                        assertThat(error.getCode()).isEqualTo(ErrorCode.BUSINESS_DEADLINE_PASSED.getCode()));

        assertThatThrownBy(() -> service.removeMember(TEAM_ID, 600L, 0, null))
                .isInstanceOfSatisfying(BizException.class, error ->
                        assertThat(error.getCode()).isEqualTo(ErrorCode.BUSINESS_DEADLINE_PASSED.getCode()));
        verify(teamMapper, never()).updateById(any(TeamDO.class));
        verify(memberMapper, never()).markInactive(any(), any(), any(), any());
    }

    @Test
    @DisplayName("提交报名缺少 teacher_id 返回 400")
    void rejectsMissingTeacherId() {
        TeamRegistrationSubmitRequest request = registrationRequest();
        request.setTeacherId(null);

        assertThatThrownBy(() -> service.submitRegistration(TEAM_ID, request, "submit-key"))
                .isInstanceOfSatisfying(BizException.class, error ->
                        assertThat(error.getCode()).isEqualTo(ErrorCode.INVALID_PARAM.getCode()));
        verify(teamMapper, never()).selectActiveByIdForUpdate(any());
    }

    @Test
    @DisplayName("详情返回当前报名轮次版本用于撤回")
    void detailReturnsCurrentRegistrationVersion() {
        TeamDO team = team();
        team.setStatus(TeamStatus.PENDING_TEACHER.databaseValue());
        team.setCurrentRegistrationId(700L);
        team.setVersion(1);
        TeamRegistrationDO registration = registration();
        registration.setVersion(2);
        when(teamMapper.selectById(TEAM_ID)).thenReturn(team);
        when(authorizationService.requireEditionForTeamRead(team)).thenReturn(edition(1L));
        when(memberMapper.selectList(any())).thenReturn(List.of(leaderMember()));
        when(registrationMapper.selectById(700L)).thenReturn(registration);

        var result = service.detail(TEAM_ID);

        assertThat(result.currentRegistrationId()).isEqualTo("700");
        assertThat(result.currentRegistrationVersion()).isEqualTo(2);
    }

    @Test
    @DisplayName("详情返回当前轮次教师、申请状态和驳回原因")
    void detailReturnsCurrentRegistrationContext() {
        TeamDO team = team();
        team.setStatus(TeamStatus.REJECTED.databaseValue());
        team.setCurrentRegistrationId(700L);
        TeamRegistrationDO registration = registration();
        registration.setStatus("REJECTED");
        registration.setRejectStage("ADMIN");
        registration.setRejectReason("成员资料不完整");
        TeamTeacherApplicationDO application = new TeamTeacherApplicationDO();
        application.setId(800L);
        application.setRegistrationId(700L);
        application.setTeacherId(8L);
        application.setStatus("ACCEPTED");
        SysUserDO teacher = teacher();
        when(teamMapper.selectById(TEAM_ID)).thenReturn(team);
        when(authorizationService.requireEditionForTeamRead(team)).thenReturn(edition(1L));
        when(memberMapper.selectList(any())).thenReturn(List.of(leaderMember()));
        when(registrationMapper.selectById(700L)).thenReturn(registration);
        when(teacherApplicationMapper.selectCurrentByRegistrationId(700L)).thenReturn(application);
        when(userMapper.selectById(8L)).thenReturn(teacher);
        when(eligibilityService.checkCanRegister(any())).thenReturn(
                new RegistrationEligibilityResult(false, "REGISTRATION_STATE_INVALID",
                        "当前队伍状态不允许提交报名", null, SCHOOL_ID, null, null, null)
        );

        var result = service.detail(TEAM_ID);

        assertThat(result.currentRegistrationStatus()).isEqualTo("REJECTED");
        assertThat(result.currentRejectStage()).isEqualTo("ADMIN");
        assertThat(result.currentRejectReason()).isEqualTo("成员资料不完整");
        assertThat(result.currentTeacherId()).isEqualTo("8");
        assertThat(result.currentTeacherName()).isEqualTo("教师甲");
        assertThat(result.currentTeacherApplicationStatus()).isEqualTo("ACCEPTED");
    }
    @Test
    @DisplayName("学校管理员列表范围包含其组织下的队伍")
    void schoolAdminListUsesOrganizationScope() {
        when(authorizationService.requireEditionForList(1L)).thenReturn(edition(1L));
        when(authorizationService.currentReadScope()).thenReturn(new TeamReadScope(
                false, 20L, Set.of(SCHOOL_ID), Set.of(), Set.of()
        ));
        Page<TeamDO> page = new Page<>(1, 20);
        page.setRecords(List.of());
        page.setTotal(0);
        when(teamMapper.selectPage(any(), any())).thenReturn(page);

        service.list(1L, new TeamPageQuery());

        @SuppressWarnings("unchecked")
        ArgumentCaptor<Wrapper<TeamDO>> wrapperCaptor =
                ArgumentCaptor.forClass((Class) Wrapper.class);
        verify(teamMapper).selectPage(any(), wrapperCaptor.capture());
        String sql = wrapperCaptor.getValue().getSqlSegment();
        assertThat(sql).contains("contest_edition");
        assertThat(sql).contains("owner_org_id");
    }
    @Test
    @DisplayName("待确认教师列表范围只匹配当前报名轮次")
    void pendingTeacherListUsesCurrentRegistrationOnly() {
        when(authorizationService.requireEditionForList(1L)).thenReturn(edition(1L));
        when(authorizationService.currentReadScope()).thenReturn(new TeamReadScope(
                false, 20L, Set.of(), Set.of(), Set.of()
        ));
        Page<TeamDO> page = new Page<>(1, 20);
        page.setRecords(List.of());
        page.setTotal(0);
        when(teamMapper.selectPage(any(), any())).thenReturn(page);

        service.list(1L, new TeamPageQuery());

        @SuppressWarnings("unchecked")
        ArgumentCaptor<Wrapper<TeamDO>> wrapperCaptor =
                ArgumentCaptor.forClass((Class) Wrapper.class);
        verify(teamMapper).selectPage(any(), wrapperCaptor.capture());
        String sql = wrapperCaptor.getValue().getSqlSegment();
        assertThat(sql).contains("team_teacher_application");
        assertThat(sql).contains("current_registration_id = tr.id");
    }
    @Test
    @DisplayName("撤回时名额释放失败会回滚整个事务")
    void withdrawRollsBackWhenQuotaReleaseFails() {
        TeamDO team = team();
        team.setStatus(TeamStatus.PENDING_TEACHER.databaseValue());
        team.setCurrentRegistrationId(700L);
        TeamRegistrationDO registration = registration();
        when(teamMapper.selectActiveByIdForUpdate(TEAM_ID)).thenReturn(team);
        when(registrationMapper.selectActiveByIdForUpdate(700L)).thenReturn(registration);
        org.mockito.Mockito.doThrow(new BizException(
                ErrorCode.BUSINESS_CONFLICT.getCode(), "释放失败"
        )).when(quotaService).release(700L);

        assertThatThrownBy(() -> service.withdrawRegistration(TEAM_ID, 1, "withdraw-key"))
                .isInstanceOf(BizException.class);
        assertThat(transactionManager.rolledBack).isTrue();
        assertThat(transactionManager.committed).isFalse();
        verify(registrationMapper, never()).updateById(any(TeamRegistrationDO.class));
        verify(teamMapper, never()).withdrawAndClearTeacher(any(), any(), any(), any(), any());
    }
    @Test
    @DisplayName("同一幂等键在截止后重试仍返回截止前的创建结果")
    void createRetryAfterDeadlineReturnsCachedResult() {
        MutableClock clock = new MutableClock(
                Instant.parse("2026-11-24T09:00:00Z"),
                ZoneId.of("Asia/Shanghai")
        );
        ContestEditionDO openEdition = edition(1L);
        openEdition.setRegisterDeadline(LocalDateTime.of(2026, 11, 24, 18, 0));
        TeamServiceImpl idempotentService = new TeamServiceImpl(
                teamMapper,
                memberMapper,
                registrationMapper,
                teacherApplicationMapper,
                availableTeacherMapper,
                userMapper,
                organizationMapper,
                authorizationService,
                eligibilityService,
                quotaService,
                authorizationSnapshotService,
                auditService,
                new CachingIdempotencyService(),
                new ObjectMapper(),
                clock,
                new TransactionTemplate(transactionManager)
        );
        when(authorizationService.requireVisibleEdition(1L)).thenReturn(openEdition);
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

        TeamCreateVO first = idempotentService.create(
                1L, createRequest("截止前幂等队伍"), "same-create-key"
        );

        clock.setInstant(Instant.parse("2026-11-24T10:01:00Z"));

        TeamCreateVO second = idempotentService.create(
                1L, createRequest("截止前幂等队伍"), "same-create-key"
        );

        assertThat(second).isEqualTo(first);
        verify(teamMapper, org.mockito.Mockito.times(1)).insert(any(TeamDO.class));
        verify(memberMapper, org.mockito.Mockito.times(1)).insert(any(TeamMemberDO.class));
    }
    @Test
    @DisplayName("教师确认通过后进入管理员终审并写入 teacher_id")
    void teacherApprovalMovesTeamToPendingAdmin() {
        TeamDO team = team();
        team.setStatus(TeamStatus.PENDING_TEACHER.databaseValue());
        team.setCurrentRegistrationId(700L);
        TeamRegistrationDO registration = registration();
        registration.setStatus("PENDING_TEACHER");
        TeamTeacherApplicationDO application = pendingTeacherApplication();
        when(teamMapper.selectActiveByIdForUpdate(TEAM_ID)).thenReturn(team);
        when(registrationMapper.selectActiveByIdForUpdate(700L)).thenReturn(registration);
        when(teacherApplicationMapper.selectOne(any())).thenReturn(application);
        when(authorizationService.requireEditionForTeamRead(team)).thenReturn(edition(1L));
        when(teamMapper.lockActiveUserForUpdate(USER_ID)).thenReturn(USER_ID);
        when(registrationMapper.countActiveTeacherLoad(1L, USER_ID, 700L)).thenReturn(0L);
        when(teacherApplicationMapper.updateById(any(TeamTeacherApplicationDO.class))).thenReturn(1);
        when(registrationMapper.updateById(any(TeamRegistrationDO.class))).thenReturn(1);
        when(teamMapper.updateTeacherAndStatus(eq(TEAM_ID), eq(0), eq(USER_ID), eq(USER_ID), any(), eq(2)))
                .thenReturn(1);

        TeamApprovalVO result = service.approveRegistration(
                TEAM_ID, approvalRequest("TEACHER", "APPROVE", null, 1), "approve-key"
        );

        assertThat(result.status()).isEqualTo("PENDING_ADMIN");
        assertThat(result.version()).isEqualTo(2);
        verify(teacherApplicationMapper).updateById(any(TeamTeacherApplicationDO.class));
        verify(registrationMapper).updateById(any(TeamRegistrationDO.class));
        verify(teamMapper).updateTeacherAndStatus(eq(TEAM_ID), eq(0), eq(USER_ID), eq(USER_ID), any(), eq(2));
    }

    @Test
    @DisplayName("教师驳回时释放名额、清空 teacher_id 并回滚状态")
    void teacherRejectionReleasesQuotaAndClearsTeacher() {
        TeamDO team = team();
        team.setStatus(TeamStatus.PENDING_TEACHER.databaseValue());
        team.setCurrentRegistrationId(700L);
        TeamRegistrationDO registration = registration();
        registration.setStatus("PENDING_TEACHER");
        TeamTeacherApplicationDO application = pendingTeacherApplication();
        when(teamMapper.selectActiveByIdForUpdate(TEAM_ID)).thenReturn(team);
        when(registrationMapper.selectActiveByIdForUpdate(700L)).thenReturn(registration);
        when(teacherApplicationMapper.selectOne(any())).thenReturn(application);
        when(teacherApplicationMapper.updateById(any(TeamTeacherApplicationDO.class))).thenReturn(1);
        when(registrationMapper.updateById(any(TeamRegistrationDO.class))).thenReturn(1);
        TeamRegistrationDO released = registration();
        released.setStatus("REJECTED");
        released.setVersion(3);
        when(registrationMapper.selectById(700L)).thenReturn(released);
        when(teamMapper.withdrawAndClearTeacher(eq(TEAM_ID), eq(0), eq(USER_ID), any(), eq(4)))
                .thenReturn(1);

        TeamApprovalVO result = service.approveRegistration(
                TEAM_ID, approvalRequest("TEACHER", "REJECT", "教师驳回", 1), "reject-key"
        );

        assertThat(result.status()).isEqualTo("REJECTED");
        assertThat(result.version()).isEqualTo(3);
        verify(quotaService).release(700L);
        verify(registrationMapper).selectById(700L);
        verify(teamMapper).withdrawAndClearTeacher(eq(TEAM_ID), eq(0), eq(USER_ID), any(), eq(4));
    }

    @Test
    @DisplayName("管理员终审通过后队伍和报名轮次均变为 APPROVED")
    void adminApprovalMovesTeamToApproved() {
        TeamDO team = team();
        team.setStatus(TeamStatus.PENDING_ADMIN.databaseValue());
        team.setCurrentRegistrationId(700L);
        team.setTeacherId(USER_ID);
        TeamRegistrationDO registration = registration();
        registration.setStatus("PENDING_ADMIN");
        when(teamMapper.selectActiveByIdForUpdate(TEAM_ID)).thenReturn(team);
        when(registrationMapper.selectActiveByIdForUpdate(700L)).thenReturn(registration);
        when(authorizationService.requireEditionForAdminReview(team)).thenReturn(edition(1L));
        when(eligibilityService.checkCanRegister(any())).thenReturn(eligible());
        when(registrationMapper.updateById(any(TeamRegistrationDO.class))).thenReturn(1);
        when(teamMapper.updateStatus(eq(TEAM_ID), eq(0), eq(USER_ID), any(), eq(3))).thenReturn(1);

        TeamApprovalVO result = service.approveRegistration(
                TEAM_ID, approvalRequest("ADMIN", "APPROVE", null, 1), "admin-approve-key"
        );

        assertThat(result.status()).isEqualTo("APPROVED");
        verify(registrationMapper).updateById(any(TeamRegistrationDO.class));
        verify(teamMapper).updateStatus(eq(TEAM_ID), eq(0), eq(USER_ID), any(), eq(3));
    }

    @Test
    @DisplayName("管理员终审资格失效时按 ADMIN 驳回并释放名额")
    void adminApprovalRejectsWhenEligibilityFails() {
        TeamDO team = team();
        team.setStatus(TeamStatus.PENDING_ADMIN.databaseValue());
        team.setCurrentRegistrationId(700L);
        team.setTeacherId(USER_ID);
        TeamRegistrationDO registration = registration();
        registration.setStatus("PENDING_ADMIN");
        when(teamMapper.selectActiveByIdForUpdate(TEAM_ID)).thenReturn(team);
        when(registrationMapper.selectActiveByIdForUpdate(700L)).thenReturn(registration);
        when(authorizationService.requireEditionForAdminReview(team)).thenReturn(edition(1L));
        when(eligibilityService.checkCanRegister(any())).thenReturn(new RegistrationEligibilityResult(
                false, "MEMBERS_INVALID", "成员资格失效", null, SCHOOL_ID, null, null, null
        ));
        when(registrationMapper.updateById(any(TeamRegistrationDO.class))).thenReturn(1);
        when(teamMapper.withdrawAndClearTeacher(eq(TEAM_ID), eq(0), eq(USER_ID), any(), eq(4)))
                .thenReturn(1);

        TeamApprovalVO result = service.approveRegistration(
                TEAM_ID, approvalRequest("ADMIN", "APPROVE", null, 1), "admin-approve-key"
        );

        assertThat(result.status()).isEqualTo("REJECTED");
        verify(quotaService).release(700L);
        verify(teamMapper).withdrawAndClearTeacher(eq(TEAM_ID), eq(0), eq(USER_ID), any(), eq(4));
    }
    @Test
    @DisplayName("教师确认队伍数达到上限时拒绝通过")
    void teacherApprovalRejectsWhenCapacityFull() {
        TeamDO team = team();
        team.setStatus(TeamStatus.PENDING_TEACHER.databaseValue());
        team.setCurrentRegistrationId(700L);
        TeamRegistrationDO registration = registration();
        registration.setStatus("PENDING_TEACHER");
        TeamTeacherApplicationDO application = pendingTeacherApplication();
        ContestEditionDO edition = edition(1L);
        edition.setMaxTeamsPerTeacher(3);
        when(teamMapper.selectActiveByIdForUpdate(TEAM_ID)).thenReturn(team);
        when(registrationMapper.selectActiveByIdForUpdate(700L)).thenReturn(registration);
        when(teacherApplicationMapper.selectOne(any())).thenReturn(application);
        when(authorizationService.requireEditionForTeamRead(team)).thenReturn(edition);
        when(teamMapper.lockActiveUserForUpdate(USER_ID)).thenReturn(USER_ID);
        when(registrationMapper.countActiveTeacherLoad(1L, USER_ID, 700L)).thenReturn(3L);

        assertThatThrownBy(() -> service.approveRegistration(
                TEAM_ID, approvalRequest("TEACHER", "APPROVE", null, 1), "capacity-key"
        )).isInstanceOfSatisfying(BizException.class, error ->
                assertThat(error.getCode()).isEqualTo(ErrorCode.BUSINESS_CONFLICT.getCode()));
        verify(teacherApplicationMapper, never()).updateById(any(TeamTeacherApplicationDO.class));
        verify(teamMapper, never()).updateTeacherAndStatus(any(), any(), any(), any(), any(), any());
        var ordered = org.mockito.Mockito.inOrder(teamMapper, registrationMapper);
        ordered.verify(teamMapper).lockActiveUserForUpdate(USER_ID);
        ordered.verify(registrationMapper).countActiveTeacherLoad(1L, USER_ID, 700L);
    }
    private TeamApprovalRequest approvalRequest(
            String stage,
            String decision,
            String reason,
            int version
    ) {
        TeamApprovalRequest request = new TeamApprovalRequest();
        request.setStage(stage);
        request.setDecision(decision);
        request.setReason(reason);
        request.setVersion(version);
        return request;
    }

    private TeamTeacherApplicationDO pendingTeacherApplication() {
        TeamTeacherApplicationDO application = new TeamTeacherApplicationDO();
        application.setId(800L);
        application.setRegistrationId(700L);
        application.setTeacherId(USER_ID);
        application.setStatus("PENDING");
        application.setVersion(0);
        return application;
    }
    private TeamRegistrationSubmitRequest registrationRequest() {
        TeamRegistrationSubmitRequest request = new TeamRegistrationSubmitRequest();
        request.setVersion(0);
        request.setTeacherId("8");
        request.setRemark("第一次报名");
        return request;
    }

    private RegistrationEligibilityResult eligible() {
        return new RegistrationEligibilityResult(
                true, "OPEN", null, null, SCHOOL_ID, 1L, 10, 1
        );
    }

    private SysUserDO teacher() {
        SysUserDO teacher = new SysUserDO();
        teacher.setId(8L);
        teacher.setRealName("教师甲");
        teacher.setSchoolId(SCHOOL_ID);
        teacher.setStatus(1);
        return teacher;
    }

    private AuthorizationSnapshot teacherSnapshot() {
        return new AuthorizationSnapshot(
                8L, "teacher_a", 1, Set.of("TEACHER"), Set.of(), Set.of("GUIDED")
        );
    }

    private TeamRegistrationDO registration() {
        TeamRegistrationDO registration = new TeamRegistrationDO();
        registration.setId(700L);
        registration.setTeamId(TEAM_ID);
        registration.setEditionId(1L);
        registration.setRoundNo(1);
        registration.setStatus("PENDING_TEACHER");
        registration.setMembersSnapshot("[]");
        registration.setQuotaHeld(1);
        registration.setQuotaSchoolId(SCHOOL_ID);
        registration.setVersion(1);
        return registration;
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

    private TeamMemberDO normalMember() {
        TeamMemberDO member = new TeamMemberDO();
        member.setId(600L);
        member.setTeamId(TEAM_ID);
        member.setEditionId(1L);
        member.setMemberName("队员乙");
        member.setSchoolId(SCHOOL_ID);
        member.setStudentNo("S002");
        member.setIsLeader(0);
        member.setStatus(1);
        member.setJoinTime(LocalDateTime.now());
        member.setVersion(0);
        return member;
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
    private static final class RecordingTransactionManager extends AbstractPlatformTransactionManager {
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
    private static final class MutableClock extends Clock {
        private volatile Instant instant;
        private final ZoneId zone;

        private MutableClock(Instant instant, ZoneId zone) {
            this.instant = instant;
            this.zone = zone;
        }

        void setInstant(Instant instant) {
            this.instant = instant;
        }

        @Override
        public ZoneId getZone() {
            return zone;
        }

        @Override
        public Clock withZone(ZoneId zone) {
            return new MutableClock(instant, zone);
        }

        @Override
        public Instant instant() {
            return instant;
        }
    }
    private static final class CachingIdempotencyService implements IdempotencyService {
        private final Map<String, Object> results = new HashMap<>();

        @Override
        public <T> T execute(
                IdempotencyScope scope,
                String operation,
                String idempotencyKey,
                Object request,
                Duration ttl,
                Class<T> resultType,
                Supplier<T> action
        ) {
            String key = scope.principalId() + ":" + operation + ":" + idempotencyKey;
            if (results.containsKey(key)) {
                return resultType.cast(results.get(key));
            }
            T result = action.get();
            results.put(key, result);
            return result;
        }
    }
}
