package com.campus.contest.module.work;

import com.campus.contest.common.BizException;
import com.campus.contest.common.ErrorCode;
import com.campus.contest.module.work.entity.FileDO;
import com.campus.contest.module.work.mapper.FileAccessScopeMapper;
import com.campus.contest.module.work.service.impl.FileAccessGuardImpl;
import com.campus.contest.security.AuthenticatedUser;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;

import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * 文件归属检查单元测试：SELF / TEAM / GUIDED / ASSIGNED / ALL 五档数据范围。
 */
@ExtendWith(MockitoExtension.class)
class FileAccessGuardImplTest {

    private static final Long ADMIN_ID = 1L;
    private static final Long STUDENT_A = 2L;
    private static final Long STUDENT_B = 3L;
    private static final Long FILE_ID = 9L;

    @Mock
    private FileAccessScopeMapper fileAccessScopeMapper;

    private FileAccessGuardImpl guard;

    @BeforeEach
    void setUp() {
        guard = new FileAccessGuardImpl(fileAccessScopeMapper);
        lenient().when(fileAccessScopeMapper.countTeamAccess(anyLong(), anyLong())).thenReturn(0L);
        lenient().when(fileAccessScopeMapper.countGuidedAccess(anyLong(), anyLong())).thenReturn(0L);
        lenient().when(fileAccessScopeMapper.countAssignedAccess(anyLong(), anyLong())).thenReturn(0L);
    }

    @AfterEach
    void tearDown() {
        SecurityContextHolder.clearContext();
    }

    @Test
    @DisplayName("上传人可以访问自己的文件（SELF 不依赖任何范围查询）")
    void ownerCanAccessOwnFile() {
        login(STUDENT_A, Set.of("STUDENT"), Set.of("SELF"));

        assertThatCode(() -> guard.requireDownloadable(file(STUDENT_A)))
                .doesNotThrowAnyException();
        verify(fileAccessScopeMapper, never()).countTeamAccess(anyLong(), anyLong());
    }

    @Test
    @DisplayName("学生 A 访问学生 B 的文件返回 404，不泄露文件是否存在")
    void otherStudentGetsNotFound() {
        login(STUDENT_A, Set.of("STUDENT"), Set.of("SELF"));

        assertThatThrownBy(() -> guard.requireDownloadable(file(STUDENT_B)))
                .isInstanceOfSatisfying(BizException.class, error -> {
                    assertThat(error.getCode()).isEqualTo(ErrorCode.RESOURCE_NOT_FOUND.getCode());
                    assertThat(error.getMessage()).contains("不可见");
                });
    }

    @Test
    @DisplayName("管理员可以访问任意用户的文件")
    void adminCanAccessAnyFile() {
        login(ADMIN_ID, Set.of("ADMIN"), Set.of("ALL"));

        assertThatCode(() -> guard.requireDownloadable(file(STUDENT_B)))
                .doesNotThrowAnyException();
    }

    @Test
    @DisplayName("只有 ALL 数据范围、没有 ADMIN 角色的用户同样可以访问")
    void allScopeCanAccessAnyFile() {
        login(4L, Set.of("TEACHER"), Set.of("ALL"));

        assertThatCode(() -> guard.requireDownloadable(file(STUDENT_B)))
                .doesNotThrowAnyException();
    }

    @Test
    @DisplayName("TEAM 范围：同队队员可以访问队友上传的文件")
    void teamMemberCanAccessTeammateFile() {
        login(STUDENT_A, Set.of("STUDENT"), Set.of("SELF", "TEAM"));
        when(fileAccessScopeMapper.countTeamAccess(FILE_ID, STUDENT_A)).thenReturn(1L);

        assertThatCode(() -> guard.requireDownloadable(file(STUDENT_B)))
                .doesNotThrowAnyException();
    }

    @Test
    @DisplayName("TEAM 范围：不在同一支队伍仍然返回 404")
    void teamScopeWithoutMembershipGetsNotFound() {
        login(STUDENT_A, Set.of("STUDENT"), Set.of("SELF", "TEAM"));
        when(fileAccessScopeMapper.countTeamAccess(FILE_ID, STUDENT_A)).thenReturn(0L);

        assertThatThrownBy(() -> guard.requireDownloadable(file(STUDENT_B)))
                .isInstanceOfSatisfying(BizException.class, error ->
                        assertThat(error.getCode()).isEqualTo(ErrorCode.RESOURCE_NOT_FOUND.getCode()));
    }

    @Test
    @DisplayName("GUIDED 范围：指导教师可以访问所指导队伍的文件")
    void guidingTeacherCanAccessTeamFile() {
        login(5L, Set.of("TEACHER"), Set.of("GUIDED"));
        when(fileAccessScopeMapper.countGuidedAccess(FILE_ID, 5L)).thenReturn(1L);

        assertThatCode(() -> guard.requireDownloadable(file(STUDENT_B)))
                .doesNotThrowAnyException();
    }

    @Test
    @DisplayName("ASSIGNED 范围：该作品版本的指派评委可以访问文件")
    void assignedReviewerCanAccessReviewedFile() {
        login(6L, Set.of("REVIEWER"), Set.of("ASSIGNED"));
        when(fileAccessScopeMapper.countAssignedAccess(FILE_ID, 6L)).thenReturn(1L);

        assertThatCode(() -> guard.requireDownloadable(file(STUDENT_B)))
                .doesNotThrowAnyException();
    }

    @Test
    @DisplayName("没有对应数据范围时不去查库，直接 404")
    void scopeNotGrantedDoesNotQueryDatabase() {
        login(6L, Set.of("REVIEWER"), Set.of("SELF"));

        assertThatThrownBy(() -> guard.requireDownloadable(file(STUDENT_B)))
                .isInstanceOfSatisfying(BizException.class, error ->
                        assertThat(error.getCode()).isEqualTo(ErrorCode.RESOURCE_NOT_FOUND.getCode()));
        verify(fileAccessScopeMapper, never()).countAssignedAccess(anyLong(), anyLong());
    }

    @Test
    @DisplayName("未认证上下文按 401 处理")
    void anonymousGetsUnauthenticated() {
        assertThatThrownBy(() -> guard.requireDownloadable(file(STUDENT_A)))
                .isInstanceOfSatisfying(BizException.class, error ->
                        assertThat(error.getCode()).isEqualTo(ErrorCode.UNAUTHENTICATED.getCode()));
    }

    @Test
    @DisplayName("文件不存在或已删除按 404 处理")
    void missingFileGetsNotFound() {
        login(STUDENT_A, Set.of("STUDENT"), Set.of("SELF"));

        assertThatThrownBy(() -> guard.requireDownloadable(null))
                .isInstanceOfSatisfying(BizException.class, error ->
                        assertThat(error.getCode()).isEqualTo(ErrorCode.RESOURCE_NOT_FOUND.getCode()));
    }

    private void login(Long userId, Set<String> roles, Set<String> scopes) {
        AuthenticatedUser user = new AuthenticatedUser(
                userId,
                "session-" + userId,
                roles,
                Set.of("file:download", "file:upload"),
                scopes
        );
        SecurityContextHolder.getContext().setAuthentication(
                new UsernamePasswordAuthenticationToken(user, null, user.authorities())
        );
    }

    private FileDO file(Long uploaderId) {
        FileDO file = new FileDO();
        file.setId(FILE_ID);
        file.setUploaderId(uploaderId);
        file.setDeleted(0);
        return file;
    }
}
