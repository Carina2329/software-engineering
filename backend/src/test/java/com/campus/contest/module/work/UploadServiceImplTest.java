package com.campus.contest.module.work;

import com.campus.contest.TestInfrastructure;
import com.campus.contest.common.BizException;
import com.campus.contest.common.ErrorCode;
import com.campus.contest.module.work.dto.UploadDtos;
import com.campus.contest.module.work.entity.UploadTaskDO;
import com.campus.contest.module.work.mapper.FileChunkMapper;
import com.campus.contest.module.work.mapper.FileMapper;
import com.campus.contest.module.work.mapper.UploadTaskMapper;
import com.campus.contest.module.work.service.impl.UploadServiceImpl;
import com.campus.contest.security.AuthenticatedUser;
import com.campus.contest.security.CurrentUserProvider;
import com.campus.contest.storage.StorageService;
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
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.when;

/**
 * 分片上传的越权与幂等单元测试（EP-046 ~ EP-049）。
 *
 * 覆盖两条合并前必须修的规则：
 * 1. 上传任务归属：学生 A 操作学生 B 的 uploadId 必须 404 + 40404，管理员放行；
 * 2. 幂等必填：EP-046 初始化与 EP-049 合并缺 Idempotency-Key 必须 400 + 10001。
 */
@ExtendWith(MockitoExtension.class)
class UploadServiceImplTest {

    private static final Long ADMIN_ID = 1L;
    private static final Long STUDENT_A = 2L;
    private static final Long STUDENT_B = 3L;
    private static final Long TASK_OF_B = 77L;

    @Mock
    private UploadTaskMapper uploadTaskMapper;
    @Mock
    private FileChunkMapper fileChunkMapper;
    @Mock
    private FileMapper fileMapper;
    @Mock
    private StorageService storageService;
    @Mock
    private CurrentUserProvider currentUserProvider;

    private UploadServiceImpl uploadService;

    @BeforeEach
    void setUp() {
        uploadService = new UploadServiceImpl(
                uploadTaskMapper,
                fileChunkMapper,
                fileMapper,
                storageService,
                currentUserProvider,
                TestInfrastructure.passthroughIdempotencyService(),
                TestInfrastructure.transactionTemplate()
        );
        lenient().when(currentUserProvider.requireUserId()).thenReturn(STUDENT_A);
    }

    @AfterEach
    void tearDown() {
        SecurityContextHolder.clearContext();
    }

    @Test
    @DisplayName("EP-046 缺少 Idempotency-Key 返回 400 + 10001")
    void initRequiresIdempotencyKey() {
        UploadDtos.InitRequest request = new UploadDtos.InitRequest();
        request.setFileName("thesis.pdf");
        request.setTotalSize(1024L);

        assertThatThrownBy(() -> uploadService.init(request, null))
                .isInstanceOfSatisfying(BizException.class, error ->
                        assertThat(error.getCode()).isEqualTo(ErrorCode.INVALID_PARAM.getCode()));
    }

    @Test
    @DisplayName("EP-049 缺少 Idempotency-Key 返回 400 + 10001，且不查任务")
    void mergeRequiresIdempotencyKey() {
        assertThatThrownBy(() -> uploadService.merge(TASK_OF_B, new UploadDtos.MergeRequest(), "  "))
                .isInstanceOfSatisfying(BizException.class, error ->
                        assertThat(error.getCode()).isEqualTo(ErrorCode.INVALID_PARAM.getCode()));
    }

    @Test
    @DisplayName("水平越权：学生 A 查询学生 B 的上传任务返回 404 + 40404")
    void studentCannotReadOthersUploadTask() {
        login(STUDENT_A, Set.of("STUDENT"), Set.of("SELF"));
        when(uploadTaskMapper.selectById(TASK_OF_B)).thenReturn(taskOfB());

        assertThatThrownBy(() -> uploadService.progress(TASK_OF_B))
                .isInstanceOfSatisfying(BizException.class, error ->
                        assertThat(error.getCode()).isEqualTo(ErrorCode.RESOURCE_NOT_FOUND.getCode()));
    }

    @Test
    @DisplayName("水平越权：学生 A 对学生 B 的任务上传分片同样 404")
    void studentCannotUploadChunkToOthersTask() {
        login(STUDENT_A, Set.of("STUDENT"), Set.of("SELF"));
        when(uploadTaskMapper.selectById(TASK_OF_B)).thenReturn(taskOfB());

        assertThatThrownBy(() -> uploadService.uploadChunk(TASK_OF_B, 0, null, new byte[]{1, 2, 3}))
                .isInstanceOfSatisfying(BizException.class, error ->
                        assertThat(error.getCode()).isEqualTo(ErrorCode.RESOURCE_NOT_FOUND.getCode()));
    }

    @Test
    @DisplayName("本人可以查询自己的上传进度")
    void ownerCanReadOwnUploadTask() {
        login(STUDENT_A, Set.of("STUDENT"), Set.of("SELF"));
        when(uploadTaskMapper.selectById(TASK_OF_B)).thenReturn(taskOf(STUDENT_A));

        UploadDtos.ProgressResponse response = uploadService.progress(TASK_OF_B);

        assertThat(response.getUploadId()).isEqualTo(String.valueOf(TASK_OF_B));
        assertThat(response.getTotalChunks()).isEqualTo(2);
        assertThat(response.getUploadedChunks()).isZero();
    }

    @Test
    @DisplayName("管理员可以查看任意用户的上传任务")
    void adminCanReadAnyUploadTask() {
        login(ADMIN_ID, Set.of("ADMIN"), Set.of("ALL"));
        lenient().when(currentUserProvider.requireUserId()).thenReturn(ADMIN_ID);
        when(uploadTaskMapper.selectById(TASK_OF_B)).thenReturn(taskOfB());

        UploadDtos.ProgressResponse response = uploadService.progress(TASK_OF_B);

        assertThat(response.getUploadId()).isEqualTo(String.valueOf(TASK_OF_B));
    }

    @Test
    @DisplayName("已删除的上传任务按 404 处理")
    void deletedUploadTaskGetsNotFound() {
        login(STUDENT_A, Set.of("STUDENT"), Set.of("SELF"));
        UploadTaskDO deleted = taskOf(STUDENT_A);
        deleted.setDeleted(1);
        when(uploadTaskMapper.selectById(TASK_OF_B)).thenReturn(deleted);

        assertThatThrownBy(() -> uploadService.progress(TASK_OF_B))
                .isInstanceOfSatisfying(BizException.class, error ->
                        assertThat(error.getCode()).isEqualTo(ErrorCode.RESOURCE_NOT_FOUND.getCode()));
    }

    private void login(Long userId, Set<String> roles, Set<String> scopes) {
        AuthenticatedUser user = new AuthenticatedUser(
                userId,
                "session-" + userId,
                roles,
                Set.of("file:upload", "file:download"),
                scopes
        );
        SecurityContextHolder.getContext().setAuthentication(
                new UsernamePasswordAuthenticationToken(user, null, user.authorities())
        );
    }

    private UploadTaskDO taskOf(Long uploaderId) {
        UploadTaskDO task = new UploadTaskDO();
        task.setId(TASK_OF_B);
        task.setUploaderId(uploaderId);
        task.setStatus(1);
        task.setTotalChunks(2);
        task.setUploadedChunks(0);
        task.setChunkSize(5 * 1024 * 1024);
        task.setDeleted(0);
        task.setVersion(0);
        return task;
    }

    private UploadTaskDO taskOfB() {
        return taskOf(STUDENT_B);
    }
}
