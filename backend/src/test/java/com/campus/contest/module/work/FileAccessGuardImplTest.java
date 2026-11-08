package com.campus.contest.module.work;

import com.campus.contest.common.BizException;
import com.campus.contest.common.ErrorCode;
import com.campus.contest.module.work.entity.FileDO;
import com.campus.contest.module.work.service.impl.FileAccessGuardImpl;
import com.campus.contest.security.AuthenticatedUser;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;

import java.util.Set;

import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.assertj.core.api.Assertions.assertThat;

/**
 * 文件归属检查单元测试（水平越权的服务层防线）。
 */
class FileAccessGuardImplTest {

    private static final Long STUDENT_A = 2L;
    private static final Long STUDENT_B = 3L;

    private FileAccessGuardImpl guard;

    @BeforeEach
    void setUp() {
        guard = new FileAccessGuardImpl();
    }

    @AfterEach
    void tearDown() {
        SecurityContextHolder.clearContext();
    }

    @Test
    @DisplayName("上传人可以访问自己的文件")
    void ownerCanAccessOwnFile() {
        login(STUDENT_A, Set.of("STUDENT"));

        assertThatCode(() -> guard.requireDownloadable(file(STUDENT_A)))
                .doesNotThrowAnyException();
    }

    @Test
    @DisplayName("学生 A 访问学生 B 的文件返回 404，不泄露文件是否存在")
    void otherStudentGetsNotFound() {
        login(STUDENT_A, Set.of("STUDENT"));

        assertThatThrownBy(() -> guard.requireDownloadable(file(STUDENT_B)))
                .isInstanceOfSatisfying(BizException.class, error -> {
                    assertThat(error.getCode()).isEqualTo(ErrorCode.RESOURCE_NOT_FOUND.getCode());
                    assertThat(error.getMessage()).contains("不可见");
                });
    }

    @Test
    @DisplayName("管理员可以访问任意用户的文件")
    void adminCanAccessAnyFile() {
        login(1L, Set.of("ADMIN"));

        assertThatCode(() -> guard.requireDownloadable(file(STUDENT_B)))
                .doesNotThrowAnyException();
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
        login(STUDENT_A, Set.of("STUDENT"));

        assertThatThrownBy(() -> guard.requireDownloadable(null))
                .isInstanceOfSatisfying(BizException.class, error ->
                        assertThat(error.getCode()).isEqualTo(ErrorCode.RESOURCE_NOT_FOUND.getCode()));
    }

    private void login(Long userId, Set<String> roles) {
        AuthenticatedUser user = new AuthenticatedUser(
                userId,
                "session-" + userId,
                roles,
                Set.of("file:download", "file:upload"),
                Set.of("SELF")
        );
        SecurityContextHolder.getContext().setAuthentication(
                new UsernamePasswordAuthenticationToken(user, null, user.authorities())
        );
    }

    private FileDO file(Long uploaderId) {
        FileDO file = new FileDO();
        file.setId(9L);
        file.setUploaderId(uploaderId);
        file.setDeleted(0);
        return file;
    }
}
