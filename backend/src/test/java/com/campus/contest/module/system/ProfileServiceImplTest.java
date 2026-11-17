package com.campus.contest.module.system;

import com.campus.contest.TestInfrastructure;
import com.campus.contest.common.BizException;
import com.campus.contest.common.ErrorCode;
import com.campus.contest.module.system.dto.AuditLogCommand;
import com.campus.contest.module.system.dto.AuthorizationSnapshot;
import com.campus.contest.module.system.dto.ProfileUpdateRequest;
import com.campus.contest.module.system.entity.SysUserDO;
import com.campus.contest.module.system.mapper.SysUserMapper;
import com.campus.contest.module.system.service.AuditLogService;
import com.campus.contest.module.system.service.AuthorizationSnapshotService;
import com.campus.contest.module.system.service.impl.ProfileServiceImpl;
import com.campus.contest.module.system.vo.CurrentUserVO;
import com.campus.contest.module.system.vo.IdVersionVO;
import com.campus.contest.security.CurrentUserProvider;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.dao.DataIntegrityViolationException;

import java.util.List;
import java.util.Set;

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
 * 个人档案（BIZ-PROFILE-001）业务规则单元测试。
 *
 * 覆盖冻结字段、角色边界、version 乐观锁、学号唯一、审计字段与敏感数据脱敏。
 */
@ExtendWith(MockitoExtension.class)
class ProfileServiceImplTest {

    private static final Long STUDENT_ID = 21L;
    private static final Long TEACHER_ID = 31L;

    @Mock
    private SysUserMapper userMapper;
    @Mock
    private AuthorizationSnapshotService authorizationSnapshotService;
    @Mock
    private CurrentUserProvider currentUserProvider;
    @Mock
    private AuditLogService auditLogService;

    private final ObjectMapper objectMapper = new ObjectMapper();

    private ProfileServiceImpl profileService;

    @BeforeEach
    void setUp() {
        profileService = new ProfileServiceImpl(
                userMapper,
                authorizationSnapshotService,
                currentUserProvider,
                TestInfrastructure.transactionTemplate(),
                auditLogService,
                TestInfrastructure.passthroughIdempotencyService(),
                objectMapper
        );
    }

    // ------------------------------------------------------------------
    // EP-003 档案字段适配
    // ------------------------------------------------------------------

    @Test
    @DisplayName("学生档案返回学号、学生字段和最新 version，不返回职称")
    void applyProfileFieldsForStudent() {
        when(currentUserProvider.requireUserId()).thenReturn(STUDENT_ID);
        when(userMapper.selectById(STUDENT_ID)).thenReturn(studentUser());

        CurrentUserVO vo = new CurrentUserVO();
        vo.setUserType("STUDENT");
        profileService.applyProfileFields(vo);

        assertThat(vo.getStudentNo()).isEqualTo("202678090110");
        assertThat(vo.getTitle()).isNull();
        assertThat(vo.getMajor()).isEqualTo("计算机科学与技术");
        assertThat(vo.getGrade()).isEqualTo("2024");
        assertThat(vo.getVersion()).isEqualTo(3);
    }

    @Test
    @DisplayName("教师档案只返回姓名、学院、职称，不泄露学号/专业/年级")
    void applyProfileFieldsForTeacher() {
        when(currentUserProvider.requireUserId()).thenReturn(TEACHER_ID);
        when(userMapper.selectById(TEACHER_ID)).thenReturn(teacherUser());

        CurrentUserVO vo = new CurrentUserVO();
        vo.setUserType("TEACHER");
        profileService.applyProfileFields(vo);

        assertThat(vo.getTitle()).isEqualTo("副教授");
        assertThat(vo.getStudentNo()).isNull();
        assertThat(vo.getMajor()).isNull();
        assertThat(vo.getGrade()).isNull();
        assertThat(vo.getVersion()).isEqualTo(2);
    }

    // ------------------------------------------------------------------
    // EP-004 学生档案
    // ------------------------------------------------------------------

    @Test
    @DisplayName("学生修改姓名与学号成功：版本加一、审计记录变更字段且不含敏感数据")
    void studentUpdatesOwnProfile() throws Exception {
        when(currentUserProvider.requireUserId()).thenReturn(STUDENT_ID);
        when(userMapper.selectById(STUDENT_ID)).thenReturn(studentUser());
        when(authorizationSnapshotService.loadRequired(STUDENT_ID)).thenReturn(snapshot(STUDENT_ID, "STUDENT"));
        when(userMapper.countByStudentNoExcluding("202678099999", STUDENT_ID)).thenReturn(0L);
        when(userMapper.updateById(any(SysUserDO.class))).thenReturn(1);

        ProfileUpdateRequest request = request(3, null);
        request.setRealName("刘蕊");
        request.setStudentNo("202678099999");

        IdVersionVO result = profileService.updateMyProfile(request, null);

        assertThat(result.getId()).isEqualTo("21");
        assertThat(result.getVersion()).isEqualTo(4);

        ArgumentCaptor<SysUserDO> captor = ArgumentCaptor.forClass(SysUserDO.class);
        verify(userMapper).updateById(captor.capture());
        assertThat(captor.getValue().getVersion()).isEqualTo(3);
        assertThat(captor.getValue().getRealName()).isEqualTo("刘蕊");
        assertThat(captor.getValue().getStudentNo()).isEqualTo("202678099999");
        // 未提交字段不能被改写
        assertThat(captor.getValue().getTitle()).isNull();

        JsonNode audit = capturedAuditParams(0);
        assertThat(audit.path("changed_fields").toString()).contains("real_name", "student_no");
        assertThat(audit.path("version_after").asInt()).isEqualTo(4);
        assertThat(audit.path("result").asText()).isEqualTo("SUCCESS");
        assertThat(audit.toString()).doesNotContain("password", "token", "email");
    }

    @Test
    @DisplayName("学生提交职称返回 400，且不会写库")
    void studentCannotSubmitTitle() {
        when(currentUserProvider.requireUserId()).thenReturn(STUDENT_ID);
        when(userMapper.selectById(STUDENT_ID)).thenReturn(studentUser());
        when(authorizationSnapshotService.loadRequired(STUDENT_ID)).thenReturn(snapshot(STUDENT_ID, "STUDENT"));

        ProfileUpdateRequest request = request(3, null);
        request.setTitle("教授");

        assertThatThrownBy(() -> profileService.updateMyProfile(request, null))
                .isInstanceOfSatisfying(BizException.class, error ->
                        assertThat(error.getCode()).isEqualTo(ErrorCode.INVALID_PARAM.getCode()));
        verify(userMapper, never()).updateById(any(SysUserDO.class));
    }

    @Test
    @DisplayName("教师提交学号、专业、年级返回 400")
    void teacherCannotSubmitStudentFields() {
        when(currentUserProvider.requireUserId()).thenReturn(TEACHER_ID);
        when(userMapper.selectById(TEACHER_ID)).thenReturn(teacherUser());
        when(authorizationSnapshotService.loadRequired(TEACHER_ID)).thenReturn(snapshot(TEACHER_ID, "TEACHER"));

        ProfileUpdateRequest studentNo = request(2, null);
        studentNo.setStudentNo("202678090110");
        assertThatThrownBy(() -> profileService.updateMyProfile(studentNo, null))
                .isInstanceOfSatisfying(BizException.class, error ->
                        assertThat(error.getCode()).isEqualTo(ErrorCode.INVALID_PARAM.getCode()));

        ProfileUpdateRequest major = request(2, null);
        major.setMajor("软件工程");
        assertThatThrownBy(() -> profileService.updateMyProfile(major, null))
                .isInstanceOfSatisfying(BizException.class, error ->
                        assertThat(error.getCode()).isEqualTo(ErrorCode.INVALID_PARAM.getCode()));

        ProfileUpdateRequest grade = request(2, null);
        grade.setGrade("2025");
        assertThatThrownBy(() -> profileService.updateMyProfile(grade, null))
                .isInstanceOfSatisfying(BizException.class, error ->
                        assertThat(error.getCode()).isEqualTo(ErrorCode.INVALID_PARAM.getCode()));
        verify(userMapper, never()).updateById(any(SysUserDO.class));
    }

    @Test
    @DisplayName("学生提交空字符串职称同样返回 400：字段出现即越界，不能被当成未提交")
    void studentCannotSubmitBlankTitle() {
        when(currentUserProvider.requireUserId()).thenReturn(STUDENT_ID);
        when(userMapper.selectById(STUDENT_ID)).thenReturn(studentUser());
        when(authorizationSnapshotService.loadRequired(STUDENT_ID)).thenReturn(snapshot(STUDENT_ID, "STUDENT"));

        ProfileUpdateRequest blankTitle = request(3, "刘蕊");
        blankTitle.setTitle("");

        assertThatThrownBy(() -> profileService.updateMyProfile(blankTitle, null))
                .isInstanceOfSatisfying(BizException.class, error -> {
                    assertThat(error.getCode()).isEqualTo(ErrorCode.INVALID_PARAM.getCode());
                    assertThat(error.getMessage()).isEqualTo("学生档案不允许修改职称");
                });
        verify(userMapper, never()).updateById(any(SysUserDO.class));
    }

    @Test
    @DisplayName("教师提交空字符串或 null 学号同样返回 400")
    void teacherCannotSubmitBlankStudentNo() {
        when(currentUserProvider.requireUserId()).thenReturn(TEACHER_ID);
        when(userMapper.selectById(TEACHER_ID)).thenReturn(teacherUser());
        when(authorizationSnapshotService.loadRequired(TEACHER_ID)).thenReturn(snapshot(TEACHER_ID, "TEACHER"));

        ProfileUpdateRequest blankStudentNo = request(2, null);
        blankStudentNo.setCollege("计算机科学学院");
        blankStudentNo.setStudentNo("   ");

        assertThatThrownBy(() -> profileService.updateMyProfile(blankStudentNo, null))
                .isInstanceOfSatisfying(BizException.class, error -> {
                    assertThat(error.getCode()).isEqualTo(ErrorCode.INVALID_PARAM.getCode());
                    assertThat(error.getMessage()).isEqualTo("教师档案不允许修改学号");
                });

        ProfileUpdateRequest nullStudentNo = request(2, null);
        nullStudentNo.setStudentNo(null);

        assertThatThrownBy(() -> profileService.updateMyProfile(nullStudentNo, null))
                .isInstanceOfSatisfying(BizException.class, error ->
                        assertThat(error.getCode()).isEqualTo(ErrorCode.INVALID_PARAM.getCode()));
        verify(userMapper, never()).updateById(any(SysUserDO.class));
    }

    @Test
    @DisplayName("本人角色字段提交空值返回 400，不静默忽略")
    void ownRoleFieldWithBlankValueIsRejected() {
        when(currentUserProvider.requireUserId()).thenReturn(STUDENT_ID);
        when(userMapper.selectById(STUDENT_ID)).thenReturn(studentUser());
        when(authorizationSnapshotService.loadRequired(STUDENT_ID)).thenReturn(snapshot(STUDENT_ID, "STUDENT"));

        ProfileUpdateRequest request = request(3, null);
        request.setMajor("   ");

        assertThatThrownBy(() -> profileService.updateMyProfile(request, null))
                .isInstanceOfSatisfying(BizException.class, error -> {
                    assertThat(error.getCode()).isEqualTo(ErrorCode.INVALID_PARAM.getCode());
                    assertThat(error.getMessage()).isEqualTo("专业不能为空");
                });
        verify(userMapper, never()).updateById(any(SysUserDO.class));
    }

    @Test
    @DisplayName("成功变更的审计写入失败时整体失败：不会出现“改成功但没有审计”")
    void auditFailureAbortsUpdate() {
        when(currentUserProvider.requireUserId()).thenReturn(STUDENT_ID);
        when(userMapper.selectById(STUDENT_ID)).thenReturn(studentUser());
        when(authorizationSnapshotService.loadRequired(STUDENT_ID)).thenReturn(snapshot(STUDENT_ID, "STUDENT"));
        when(userMapper.updateById(any(SysUserDO.class))).thenReturn(1);
        org.mockito.Mockito.doThrow(new org.springframework.dao.DataAccessResourceFailureException("审计库不可用"))
                .when(auditLogService).record(any(AuditLogCommand.class));

        ProfileUpdateRequest request = request(3, "刘蕊");

        assertThatThrownBy(() -> profileService.updateMyProfile(request, null))
                .isInstanceOf(RuntimeException.class);
    }

    @Test
    @DisplayName("重复学号是 409 + 20002，版本冲突是 409 + 20001，两者业务码必须不同")
    void duplicateStudentNoAndVersionConflictUseDifferentCodes() {
        when(currentUserProvider.requireUserId()).thenReturn(STUDENT_ID);
        when(userMapper.selectById(STUDENT_ID)).thenReturn(studentUser());
        when(authorizationSnapshotService.loadRequired(STUDENT_ID)).thenReturn(snapshot(STUDENT_ID, "STUDENT"));

        ProfileUpdateRequest duplicate = request(3, null);
        duplicate.setStudentNo("202678090111");
        when(userMapper.countByStudentNoExcluding(anyString(), eq(STUDENT_ID))).thenReturn(1L);
        assertThatThrownBy(() -> profileService.updateMyProfile(duplicate, null))
                .isInstanceOfSatisfying(BizException.class, error ->
                        assertThat(error.getCode()).isEqualTo(ErrorCode.DUPLICATE_RELATION.getCode()));

        when(userMapper.updateById(any(SysUserDO.class))).thenReturn(0);
        ProfileUpdateRequest conflict = request(2, "刘蕊");
        assertThatThrownBy(() -> profileService.updateMyProfile(conflict, null))
                .isInstanceOfSatisfying(BizException.class, error ->
                        assertThat(error.getCode()).isEqualTo(ErrorCode.BUSINESS_CONFLICT.getCode()));
    }

    @Test
    @DisplayName("教师修改学院与职称成功")
    void teacherUpdatesCollegeAndTitle() {
        when(currentUserProvider.requireUserId()).thenReturn(TEACHER_ID);
        when(userMapper.selectById(TEACHER_ID)).thenReturn(teacherUser());
        when(authorizationSnapshotService.loadRequired(TEACHER_ID)).thenReturn(snapshot(TEACHER_ID, "TEACHER"));
        when(userMapper.updateById(any(SysUserDO.class))).thenReturn(1);

        ProfileUpdateRequest request = request(2, null);
        request.setCollege("数学科学学院");
        request.setTitle("教授");

        IdVersionVO result = profileService.updateMyProfile(request, null);

        assertThat(result.getVersion()).isEqualTo(3);
        verify(auditLogService).record(any(AuditLogCommand.class));
    }

    // ------------------------------------------------------------------
    // version 乐观锁、学号唯一、字段白名单
    // ------------------------------------------------------------------

    @Test
    @DisplayName("旧版本提交返回 409 业务冲突，且不改库")
    void staleVersionReturnsConflict() {
        when(currentUserProvider.requireUserId()).thenReturn(STUDENT_ID);
        when(userMapper.selectById(STUDENT_ID)).thenReturn(studentUser());
        when(authorizationSnapshotService.loadRequired(STUDENT_ID)).thenReturn(snapshot(STUDENT_ID, "STUDENT"));
        when(userMapper.updateById(any(SysUserDO.class))).thenReturn(0);

        ProfileUpdateRequest request = request(2, null);
        request.setRealName("刘蕊");

        assertThatThrownBy(() -> profileService.updateMyProfile(request, null))
                .isInstanceOfSatisfying(BizException.class, error -> {
                    assertThat(error.getCode()).isEqualTo(ErrorCode.BUSINESS_CONFLICT.getCode());
                    assertThat(ErrorCode.resolveHttpStatus(error.getCode()).value()).isEqualTo(409);
                });
    }

    @Test
    @DisplayName("重复学号返回 20002，且原资料不发生变化")
    void duplicateStudentNoIsRejected() {
        when(currentUserProvider.requireUserId()).thenReturn(STUDENT_ID);
        when(userMapper.selectById(STUDENT_ID)).thenReturn(studentUser());
        when(authorizationSnapshotService.loadRequired(STUDENT_ID)).thenReturn(snapshot(STUDENT_ID, "STUDENT"));
        when(userMapper.countByStudentNoExcluding("202678090111", STUDENT_ID)).thenReturn(1L);

        ProfileUpdateRequest request = request(3, null);
        request.setStudentNo("202678090111");

        assertThatThrownBy(() -> profileService.updateMyProfile(request, null))
                .isInstanceOfSatisfying(BizException.class, error -> {
                    assertThat(error.getCode()).isEqualTo(ErrorCode.DUPLICATE_RELATION.getCode());
                    assertThat(ErrorCode.resolveHttpStatus(error.getCode()).value()).isEqualTo(409);
                });
        verify(userMapper, never()).updateById(any(SysUserDO.class));
    }

    @Test
    @DisplayName("并发写入撞到学号唯一索引时返回 20002 而不是 500")
    void uniqueIndexViolationIsMappedToBusinessError() {
        when(currentUserProvider.requireUserId()).thenReturn(STUDENT_ID);
        when(userMapper.selectById(STUDENT_ID)).thenReturn(studentUser());
        when(authorizationSnapshotService.loadRequired(STUDENT_ID)).thenReturn(snapshot(STUDENT_ID, "STUDENT"));
        when(userMapper.countByStudentNoExcluding(anyString(), eq(STUDENT_ID))).thenReturn(0L);
        when(userMapper.updateById(any(SysUserDO.class)))
                .thenThrow(new DataIntegrityViolationException("uk_sys_user_student_no"));

        ProfileUpdateRequest request = request(3, null);
        request.setStudentNo("202678090112");

        assertThatThrownBy(() -> profileService.updateMyProfile(request, null))
                .isInstanceOfSatisfying(BizException.class, error ->
                        assertThat(error.getCode()).isEqualTo(ErrorCode.DUPLICATE_RELATION.getCode()));
    }

    @Test
    @DisplayName("提交 username/status 等账号字段返回 400，账号与角色不可通过档案接口修改")
    void unexpectedAccountFieldsAreRejected() {
        when(currentUserProvider.requireUserId()).thenReturn(STUDENT_ID);

        ProfileUpdateRequest request = request(3, null);
        request.setRealName("刘蕊");
        request.collectUnexpectedField("status", 0);
        request.collectUnexpectedField("username", "hacker");

        assertThatThrownBy(() -> profileService.updateMyProfile(request, null))
                .isInstanceOfSatisfying(BizException.class, error -> {
                    assertThat(error.getCode()).isEqualTo(ErrorCode.INVALID_PARAM.getCode());
                    assertThat(error.getMessage()).contains("status", "username");
                });
        verify(userMapper, never()).updateById(any(SysUserDO.class));
    }

    @Test
    @DisplayName("缺少 version 返回 400")
    void versionIsRequired() {
        when(currentUserProvider.requireUserId()).thenReturn(STUDENT_ID);

        ProfileUpdateRequest request = request(null, null);
        request.setRealName("刘蕊");

        assertThatThrownBy(() -> profileService.updateMyProfile(request, null))
                .isInstanceOfSatisfying(BizException.class, error ->
                        assertThat(error.getCode()).isEqualTo(ErrorCode.INVALID_PARAM.getCode()));
    }

    @Test
    @DisplayName("只提交 version 没有档案字段返回 400")
    void emptyPatchIsRejected() {
        when(currentUserProvider.requireUserId()).thenReturn(STUDENT_ID);
        when(userMapper.selectById(STUDENT_ID)).thenReturn(studentUser());
        when(authorizationSnapshotService.loadRequired(STUDENT_ID)).thenReturn(snapshot(STUDENT_ID, "STUDENT"));

        assertThatThrownBy(() -> profileService.updateMyProfile(request(3, null), null))
                .isInstanceOfSatisfying(BizException.class, error ->
                        assertThat(error.getCode()).isEqualTo(ErrorCode.INVALID_PARAM.getCode()));
    }

    @Test
    @DisplayName("姓名超长或为空返回 400，前端规则与服务端一致")
    void invalidNameIsRejected() {
        when(currentUserProvider.requireUserId()).thenReturn(STUDENT_ID);
        when(userMapper.selectById(STUDENT_ID)).thenReturn(studentUser());
        when(authorizationSnapshotService.loadRequired(STUDENT_ID)).thenReturn(snapshot(STUDENT_ID, "STUDENT"));

        ProfileUpdateRequest tooLong = request(3, null);
        tooLong.setRealName("名".repeat(33));
        assertThatThrownBy(() -> profileService.updateMyProfile(tooLong, null))
                .isInstanceOfSatisfying(BizException.class, error ->
                        assertThat(error.getCode()).isEqualTo(ErrorCode.INVALID_PARAM.getCode()));

        ProfileUpdateRequest blank = request(3, null);
        blank.setRealName("   ");
        assertThatThrownBy(() -> profileService.updateMyProfile(blank, null))
                .isInstanceOfSatisfying(BizException.class, error ->
                        assertThat(error.getCode()).isEqualTo(ErrorCode.INVALID_PARAM.getCode()));
    }

    @Test
    @DisplayName("管理员/评审专家只能改姓名和学院，不能改学生或教师专属字段")
    void commonRoleCannotTouchRoleSpecificFields() {
        when(currentUserProvider.requireUserId()).thenReturn(9L);
        when(userMapper.selectById(9L)).thenReturn(teacherUser());
        when(authorizationSnapshotService.loadRequired(9L)).thenReturn(snapshot(9L, "ADMIN"));
        when(userMapper.updateById(any(SysUserDO.class))).thenReturn(1);

        ProfileUpdateRequest request = request(2, null);
        request.setRealName("管理员");
        request.setCollege("教务处");
        IdVersionVO result = profileService.updateMyProfile(request, null);
        assertThat(result.getVersion()).isEqualTo(3);

        ProfileUpdateRequest title = request(2, null);
        title.setTitle("教授");
        assertThatThrownBy(() -> profileService.updateMyProfile(title, null))
                .isInstanceOfSatisfying(BizException.class, error ->
                        assertThat(error.getCode()).isEqualTo(ErrorCode.INVALID_PARAM.getCode()));
    }

    // ------------------------------------------------------------------
    // 测试数据构造
    // ------------------------------------------------------------------

    private ProfileUpdateRequest request(Integer version, String realName) {
        ProfileUpdateRequest request = new ProfileUpdateRequest();
        request.setVersion(version);
        if (realName != null) {
            // 不设置即代表“请求体里没有该字段”，与“提交了空值”要区分开
            request.setRealName(realName);
        }
        return request;
    }

    private SysUserDO studentUser() {
        SysUserDO user = new SysUserDO();
        user.setId(STUDENT_ID);
        user.setUsername("student_a");
        user.setRealName("学生 A");
        user.setStudentNo("202678090110");
        user.setCollege("计算机科学学院");
        user.setMajor("计算机科学与技术");
        user.setGrade("2024");
        user.setStatus(1);
        user.setVersion(3);
        return user;
    }

    private SysUserDO teacherUser() {
        SysUserDO user = new SysUserDO();
        user.setId(TEACHER_ID);
        user.setUsername("teacher_a");
        user.setRealName("教师 A");
        user.setCollege("计算机科学学院");
        user.setTitle("副教授");
        user.setStatus(1);
        user.setVersion(2);
        return user;
    }

    private AuthorizationSnapshot snapshot(Long userId, String roleCode) {
        return new AuthorizationSnapshot(
                userId,
                "user_" + userId,
                1,
                Set.of(roleCode),
                Set.of(),
                Set.of("SELF")
        );
    }

    private JsonNode capturedAuditParams(int index) throws Exception {
        ArgumentCaptor<AuditLogCommand> captor = ArgumentCaptor.forClass(AuditLogCommand.class);
        verify(auditLogService, org.mockito.Mockito.atLeast(index + 1)).record(captor.capture());
        AuditLogCommand command = captor.getAllValues().get(index);
        assertThat(command.module()).isEqualTo("PROFILE");
        assertThat(command.operation()).isEqualTo("USER_PROFILE_UPDATE");
        assertThat(command.userId()).isEqualTo(STUDENT_ID);
        return objectMapper.readTree(command.params());
    }
}
