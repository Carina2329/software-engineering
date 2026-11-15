package com.campus.contest.module.work.service.impl;

import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.campus.contest.common.BizException;
import com.campus.contest.common.ErrorCode;
import com.campus.contest.common.idempotency.IdempotencyScope;
import com.campus.contest.common.idempotency.IdempotencyService;
import com.campus.contest.module.work.dto.UploadDtos;
import com.campus.contest.module.work.entity.FileChunkDO;
import com.campus.contest.module.work.entity.FileDO;
import com.campus.contest.module.work.entity.UploadTaskDO;
import com.campus.contest.module.work.mapper.FileChunkMapper;
import com.campus.contest.module.work.mapper.FileMapper;
import com.campus.contest.module.work.mapper.UploadTaskMapper;
import com.campus.contest.module.work.service.UploadService;
import com.campus.contest.security.AuthenticatedUser;
import com.campus.contest.security.CurrentUserProvider;
import com.campus.contest.storage.StorageService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionTemplate;
import org.springframework.util.StringUtils;

import java.io.ByteArrayInputStream;
import java.io.InputStream;
import java.io.OutputStream;
import java.security.DigestInputStream;
import java.security.DigestOutputStream;
import java.security.MessageDigest;
import java.time.Duration;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.UUID;

/**
 * 分片上传实现。
 *
 * 越权防线两道：
 * 1. Controller 上的 @PreAuthorize("hasAuthority('file:upload')") 负责功能权限；
 * 2. 这里 requireTask 校验 uploaderId，非本人且非管理员一律按 404 处理，
 *    不泄露他人上传任务是否存在，堵住上传任务的水平越权（EP-046 ~ EP-049 的 SELF / TEAM 数据范围）。
 *
 * 幂等：EP-046 与 EP-049 的 Idempotency-Key 是必填项，缺 key 直接 400 + 10001；
 * 带 key 时走 IdempotencyService，同一 key 重试返回第一次的结果，不会重复建任务或重复合并。
 * MySQL 事务由 TransactionTemplate 包住，Redis 幂等调用在事务之外，符合“MySQL 事务不含 Redis”的约定。
 */
@Slf4j
@Service
public class UploadServiceImpl implements UploadService {

    /** 默认分片 5 MiB，接口文档允许 1 KiB ~ 20 MiB */
    private static final int DEFAULT_CHUNK_SIZE = 5 * 1024 * 1024;
    private static final int MIN_CHUNK_SIZE = 1024;
    private static final int MAX_CHUNK_SIZE = 20 * 1024 * 1024;
    /** 单文件上限 5 GiB */
    private static final long MAX_FILE_SIZE = 5L * 1024 * 1024 * 1024;

    private static final int STATUS_UPLOADING = 1;
    private static final int STATUS_MERGING = 2;
    private static final int STATUS_DONE = 3;
    private static final int STATUS_FAILED = 4;

    private static final String ADMIN_ROLE = "ADMIN";
    private static final String SCOPE_ALL = "ALL";
    private static final String OP_INIT = "UPLOAD_INIT";
    private static final String OP_MERGE = "UPLOAD_MERGE";
    private static final Duration IDEMPOTENCY_TTL = Duration.ofHours(24);
    private static final int MAX_IDEMPOTENCY_KEY_LENGTH = 64;

    private final UploadTaskMapper uploadTaskMapper;
    private final FileChunkMapper fileChunkMapper;
    private final FileMapper fileMapper;
    private final StorageService storageService;
    private final CurrentUserProvider currentUserProvider;
    private final IdempotencyService idempotencyService;
    private final TransactionTemplate transactionTemplate;

    public UploadServiceImpl(UploadTaskMapper uploadTaskMapper,
                             FileChunkMapper fileChunkMapper,
                             FileMapper fileMapper,
                             StorageService storageService,
                             CurrentUserProvider currentUserProvider,
                             IdempotencyService idempotencyService,
                             TransactionTemplate transactionTemplate) {
        this.uploadTaskMapper = uploadTaskMapper;
        this.fileChunkMapper = fileChunkMapper;
        this.fileMapper = fileMapper;
        this.storageService = storageService;
        this.currentUserProvider = currentUserProvider;
        this.idempotencyService = idempotencyService;
        this.transactionTemplate = transactionTemplate;
    }

    @Override
    public UploadDtos.InitResponse init(UploadDtos.InitRequest request, String idempotencyKey) {
        String key = requireIdempotencyKey(idempotencyKey);
        Long uploaderId = currentUserProvider.requireUserId();

        Map<String, Object> fingerprint = new LinkedHashMap<>();
        fingerprint.put("file_name", request == null ? null : request.getFileName());
        fingerprint.put("total_size", request == null ? null : request.getTotalSize());
        fingerprint.put("sha256", request == null ? null : request.getSha256());
        fingerprint.put("chunk_size", request == null ? null : request.getChunkSize());

        return idempotencyService.execute(
                userScope(uploaderId),
                OP_INIT,
                key,
                fingerprint,
                IDEMPOTENCY_TTL,
                UploadDtos.InitResponse.class,
                () -> inTransaction(() -> doInit(request, uploaderId))
        );
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public UploadDtos.ChunkResponse uploadChunk(Long uploadId, Integer chunkIndex,
                                                String expectedSha256, byte[] body) {
        UploadTaskDO task = requireTask(uploadId);
        if (task.getStatus() == null || task.getStatus() != STATUS_UPLOADING) {
            throw new BizException(409, "当前任务不在上传中状态");
        }
        if (chunkIndex == null || chunkIndex < 0 || chunkIndex >= task.getTotalChunks()) {
            throw new BizException("分片序号越界");
        }
        if (body == null || body.length == 0) {
            throw new BizException("分片内容为空");
        }
        if (body.length > task.getChunkSize()) {
            throw new BizException("分片大小超过限制");
        }
        if (expectedSha256 == null || !expectedSha256.matches("[0-9a-fA-F]{64}")) {
            throw new BizException("X-Chunk-SHA256 必须为 64 位十六进制字符串");
        }

        String actualSha256 = sha256(body);
        if (!actualSha256.equalsIgnoreCase(expectedSha256)) {
            Map<String, Object> detail = Map.of(
                    "invalidChunks", List.of(chunkIndex),
                    "reason", "chunk-sha256-mismatch",
                    "expectedSha256", expectedSha256.toLowerCase(),
                    "actualSha256", actualSha256
            );
            throw new BizException(422, "分片 " + chunkIndex + " 校验失败，请重新上传该分片", detail);
        }

        String relative = chunkPath(uploadId, chunkIndex);
        storageService.write(relative, new ByteArrayInputStream(body));

        FileChunkDO chunk = fileChunkMapper.selectOne(Wrappers.<FileChunkDO>lambdaQuery()
                .eq(FileChunkDO::getUploadId, uploadId)
                .eq(FileChunkDO::getChunkIndex, chunkIndex)
                .last("limit 1"));
        if (chunk == null) {
            chunk = new FileChunkDO();
            chunk.setUploadId(uploadId);
            chunk.setChunkIndex(chunkIndex);
            chunk.setChunkSize(body.length);
            chunk.setChunkPath(relative);
            chunk.setSha256(actualSha256);
            chunk.setStatus(1);
            chunk.setCreatedAt(LocalDateTime.now());
            chunk.setCreatedBy(currentUserProvider.requireUserId());
            chunk.setDeleted(0);
            chunk.setVersion(0);
            fileChunkMapper.insert(chunk);
        } else {
            chunk.setChunkSize(body.length);
            chunk.setChunkPath(relative);
            chunk.setSha256(actualSha256);
            chunk.setStatus(1);
            fileChunkMapper.updateById(chunk);
        }

        List<FileChunkDO> activeChunks = fileChunkMapper.selectList(Wrappers.<FileChunkDO>lambdaQuery()
                .eq(FileChunkDO::getUploadId, uploadId)
                .eq(FileChunkDO::getStatus, 1));
        task.setUploadedChunks(activeChunks == null ? 0 : activeChunks.size());
        uploadTaskMapper.updateById(task);

        UploadDtos.ChunkResponse response = new UploadDtos.ChunkResponse();
        response.setUploadId(String.valueOf(uploadId));
        response.setChunkIndex(chunkIndex);
        response.setReceived(task.getUploadedChunks());
        response.setMissingChunks(findMissingChunks(
                task.getTotalChunks(),
                activeChunks == null ? List.of() : activeChunks
        ));
        return response;
    }

    @Override
    public UploadDtos.ProgressResponse progress(Long uploadId) {
        UploadTaskDO task = requireTask(uploadId);
        List<Integer> uploaded = new ArrayList<>();
        for (FileChunkDO chunk : fileChunkMapper.selectList(Wrappers.<FileChunkDO>lambdaQuery()
                .eq(FileChunkDO::getUploadId, uploadId)
                .eq(FileChunkDO::getStatus, 1)
                .orderByAsc(FileChunkDO::getChunkIndex))) {
            uploaded.add(chunk.getChunkIndex());
        }

        UploadDtos.ProgressResponse response = new UploadDtos.ProgressResponse();
        response.setUploadId(String.valueOf(uploadId));
        response.setStatus(statusName(task.getStatus()));
        response.setTotalChunks(task.getTotalChunks());
        response.setUploadedChunks(uploaded.size());
        response.setMissingChunks(findMissingChunks(task.getTotalChunks(), fileChunkMapper.selectList(
                Wrappers.<FileChunkDO>lambdaQuery()
                        .eq(FileChunkDO::getUploadId, uploadId)
                        .eq(FileChunkDO::getStatus, 1)
        )));
        response.setExpiresAt(task.getExpireAt() == null
                ? null
                : task.getExpireAt().atZone(java.time.ZoneId.systemDefault()).toInstant());
        return response;
    }

    @Override
    public UploadDtos.MergeResponse merge(Long uploadId, UploadDtos.MergeRequest request, String idempotencyKey) {
        String key = requireIdempotencyKey(idempotencyKey);
        Long currentUserId = currentUserProvider.requireUserId();
        // 先做一次归属校验，避免拿他人的 uploadId 去打幂等 key
        requireTask(uploadId);

        Map<String, Object> fingerprint = new LinkedHashMap<>();
        fingerprint.put("upload_id", String.valueOf(uploadId));
        fingerprint.put("version", request == null ? null : request.getVersion());

        return idempotencyService.execute(
                userScope(currentUserId),
                OP_MERGE,
                key,
                fingerprint,
                IDEMPOTENCY_TTL,
                UploadDtos.MergeResponse.class,
                () -> inTransaction(() -> doMerge(uploadId, request))
        );
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void abort(Long uploadId) {
        UploadTaskDO task = requireTask(uploadId);
        List<FileChunkDO> chunks = fileChunkMapper.selectList(Wrappers.<FileChunkDO>lambdaQuery()
                .eq(FileChunkDO::getUploadId, uploadId));
        cleanChunks(uploadId, chunks);
        task.setStatus(STATUS_FAILED);
        task.setDeleted(1);
        task.setDeletedAt(LocalDateTime.now());
        uploadTaskMapper.updateById(task);
    }

    // ------------------------------------------------------------------
    // 事务内的业务本体
    // ------------------------------------------------------------------

    private UploadDtos.InitResponse doInit(UploadDtos.InitRequest request, Long uploaderId) {
        if (request == null || request.getFileName() == null || request.getTotalSize() == null) {
            throw new BizException("文件名和文件大小不能为空");
        }
        if (request.getTotalSize() <= 0 || request.getTotalSize() > MAX_FILE_SIZE) {
            throw new BizException("文件大小不合法（最大 5 GiB）");
        }
        String sha256 = request.getSha256();
        if (sha256 != null && !sha256.matches("[0-9a-fA-F]{64}")) {
            throw new BizException("sha256 格式不正确");
        }
        int chunkSize = request.getChunkSize() == null ? DEFAULT_CHUNK_SIZE : request.getChunkSize();
        if (chunkSize < MIN_CHUNK_SIZE || chunkSize > MAX_CHUNK_SIZE) {
            throw new BizException("分片大小不合法（1 KiB ~ 20 MiB）");
        }

        UploadDtos.InitResponse response = new UploadDtos.InitResponse();
        response.setChunkSize(chunkSize);

        // 秒传：本人已上传过同样内容的文件才直接返回，避免把他人 file_id 泄露给当前用户
        if (sha256 != null) {
            FileDO exist = fileMapper.selectOne(Wrappers.<FileDO>lambdaQuery()
                    .eq(FileDO::getSha256, sha256.toLowerCase())
                    .eq(FileDO::getStorageType, storageService.type())
                    .eq(FileDO::getUploaderId, uploaderId)
                    .eq(FileDO::getDeleted, 0)
                    .last("limit 1"));
            if (exist != null) {
                response.setInstant(true);
                response.setFileId(String.valueOf(exist.getId()));
                response.setTotalChunks(0);
                return response;
            }
        }

        int totalChunks = (int) ((request.getTotalSize() + chunkSize - 1) / chunkSize);
        UploadTaskDO task = new UploadTaskDO();
        task.setFileName(request.getFileName());
        task.setTotalSize(request.getTotalSize());
        task.setChunkSize(chunkSize);
        task.setTotalChunks(totalChunks);
        task.setUploadedChunks(0);
        task.setExpectedSha256(sha256 == null ? null : sha256.toLowerCase());
        task.setStatus(STATUS_UPLOADING);
        task.setExpireAt(LocalDateTime.now().plusDays(1));
        task.setUploaderId(uploaderId);
        task.setCreatedAt(LocalDateTime.now());
        task.setCreatedBy(uploaderId);
        task.setDeleted(0);
        task.setVersion(0);
        uploadTaskMapper.insert(task);

        response.setUploadId(String.valueOf(task.getId()));
        response.setTotalChunks(totalChunks);
        return response;
    }

    private UploadDtos.MergeResponse doMerge(Long uploadId, UploadDtos.MergeRequest request) {
        UploadTaskDO task = requireTask(uploadId);
        if (task.getStatus() == null || task.getStatus() != STATUS_UPLOADING) {
            throw new BizException(409, "当前任务不在上传中状态");
        }
        if (request != null && request.getVersion() != null
                && task.getVersion() != null && !request.getVersion().equals(task.getVersion())) {
            throw new BizException(409, "上传任务版本已变化，请刷新后重试");
        }

        List<FileChunkDO> chunks = fileChunkMapper.selectList(Wrappers.<FileChunkDO>lambdaQuery()
                .eq(FileChunkDO::getUploadId, uploadId)
                .eq(FileChunkDO::getStatus, 1)
                .orderByAsc(FileChunkDO::getChunkIndex));
        List<Integer> missingChunks = findMissingChunks(task.getTotalChunks(), chunks);
        List<Integer> invalidChunks = verifyChunkChecksums(chunks);
        if (!missingChunks.isEmpty() || !invalidChunks.isEmpty()) {
            Set<Integer> retryChunks = new LinkedHashSet<>(missingChunks);
            retryChunks.addAll(invalidChunks);
            Map<String, Object> detail = Map.of(
                    "missingChunks", missingChunks,
                    "invalidChunks", invalidChunks,
                    "retryChunks", new ArrayList<>(retryChunks)
            );
            throw new BizException(409, "分片校验未通过，请重传 retryChunks 中的分片", detail);
        }

        task.setStatus(STATUS_MERGING);
        uploadTaskMapper.updateById(task);

        String relative = "files/" + UUID.randomUUID() + extensionOf(task.getFileName());
        String sha256;
        try (OutputStream out = storageService.create(relative)) {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            try (DigestOutputStream dos = new DigestOutputStream(out, digest)) {
                for (FileChunkDO chunk : chunks) {
                    try (InputStream in = storageService.open(chunk.getChunkPath())) {
                        in.transferTo(dos);
                    }
                }
            }
            sha256 = toHex(digest.digest());
        } catch (Exception e) {
            task.setStatus(STATUS_FAILED);
            uploadTaskMapper.updateById(task);
            log.error("合并分片失败，uploadId={}", uploadId, e);
            throw new BizException(500, "合并分片失败：" + e.getMessage());
        }

        if (task.getExpectedSha256() != null && !sha256.equalsIgnoreCase(task.getExpectedSha256())) {
            try {
                storageService.delete(relative);
            } catch (Exception e) {
                log.warn("删除整文件校验失败的合并结果时出错：{}", relative, e);
            }
            Map<String, Object> detail = Map.of(
                    "reason", "file-sha256-mismatch",
                    "expectedSha256", task.getExpectedSha256(),
                    "actualSha256", sha256
            );
            throw new BizException(422, "整文件 SHA-256 校验失败，请重新上传", detail);
        }

        FileDO file = new FileDO();
        file.setOriginalName(task.getFileName());
        file.setFileSize(task.getTotalSize());
        file.setStorageType(storageService.type());
        file.setStoragePath(relative);
        file.setSha256(sha256);
        file.setUploaderId(task.getUploaderId());
        file.setCreatedAt(LocalDateTime.now());
        file.setCreatedBy(task.getUploaderId());
        file.setDeleted(0);
        file.setVersion(0);
        fileMapper.insert(file);

        task.setFileId(file.getId());
        task.setStatus(STATUS_DONE);
        uploadTaskMapper.updateById(task);
        cleanChunks(uploadId, chunks);

        UploadDtos.MergeResponse response = new UploadDtos.MergeResponse();
        response.setUploadId(String.valueOf(uploadId));
        response.setFileId(String.valueOf(file.getId()));
        response.setStatus(statusName(task.getStatus()));
        return response;
    }

    // ------------------------------------------------------------------
    // 归属、幂等与工具方法
    // ------------------------------------------------------------------

    /**
     * 读取上传任务并校验归属：非上传人且非管理员时按 404 处理，避免通过状态码探测他人任务。
     */
    private UploadTaskDO requireTask(Long uploadId) {
        UploadTaskDO task = uploadId == null ? null : uploadTaskMapper.selectById(uploadId);
        if (task == null || Integer.valueOf(1).equals(task.getDeleted())) {
            throw notFound();
        }
        Long currentUserId = currentUserProvider.requireUserId();
        if (!Objects.equals(currentUserId, task.getUploaderId()) && !isAdminOrAllScope()) {
            log.warn("上传任务归属校验失败：uploadId={}, uploaderId={}, currentUserId={}",
                    uploadId, task.getUploaderId(), currentUserId);
            throw notFound();
        }
        return task;
    }

    private boolean isAdminOrAllScope() {
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        if (authentication == null
                || !authentication.isAuthenticated()
                || !(authentication.getPrincipal() instanceof AuthenticatedUser principal)) {
            return false;
        }
        return principal.roleCodes().contains(ADMIN_ROLE) || principal.dataScopes().contains(SCOPE_ALL);
    }

    private BizException notFound() {
        return new BizException(
                ErrorCode.RESOURCE_NOT_FOUND.getCode(),
                ErrorCode.RESOURCE_NOT_FOUND.getMessage()
        );
    }

    private String requireIdempotencyKey(String idempotencyKey) {
        if (!StringUtils.hasText(idempotencyKey)) {
            throw new BizException(ErrorCode.INVALID_PARAM.getCode(), "缺少 Idempotency-Key 请求头");
        }
        String trimmed = idempotencyKey.trim();
        if (trimmed.length() > MAX_IDEMPOTENCY_KEY_LENGTH) {
            throw new BizException(
                    ErrorCode.INVALID_PARAM.getCode(),
                    "Idempotency-Key 必须为 1-" + MAX_IDEMPOTENCY_KEY_LENGTH + " 位非空字符串"
            );
        }
        return trimmed;
    }

    private IdempotencyScope userScope(Long userId) {
        return new IdempotencyScope(IdempotencyScope.PrincipalType.USER, String.valueOf(userId));
    }

    private <T> T inTransaction(java.util.function.Supplier<T> action) {
        T result = transactionTemplate.execute(status -> action.get());
        if (result == null) {
            throw new BizException(ErrorCode.INTERNAL_ERROR.getCode(), "事务未返回结果");
        }
        return result;
    }

    private void cleanChunks(Long uploadId, List<FileChunkDO> chunks) {
        for (FileChunkDO chunk : chunks) {
            try {
                storageService.delete(chunk.getChunkPath());
            } catch (Exception e) {
                log.warn("删除分片文件失败：{}", chunk.getChunkPath(), e);
            }
        }
        fileChunkMapper.delete(Wrappers.<FileChunkDO>lambdaQuery()
                .eq(FileChunkDO::getUploadId, uploadId));
    }

    private String chunkPath(Long uploadId, Integer index) {
        return "chunks/" + uploadId + "/" + index + ".chunk";
    }

    private List<Integer> findMissingChunks(int totalChunks, List<FileChunkDO> chunks) {
        Set<Integer> received = new LinkedHashSet<>();
        for (FileChunkDO chunk : chunks) {
            received.add(chunk.getChunkIndex());
        }
        List<Integer> missing = new ArrayList<>();
        for (int i = 0; i < totalChunks; i++) {
            if (!received.contains(i)) {
                missing.add(i);
            }
        }
        return missing;
    }

    private String statusName(Integer status) {
        if (status == null) {
            return "FAILED";
        }
        return switch (status) {
            case STATUS_UPLOADING -> "UPLOADING";
            case STATUS_MERGING -> "MERGING";
            case STATUS_DONE -> "COMPLETED";
            default -> "FAILED";
        };
    }

    private List<Integer> verifyChunkChecksums(List<FileChunkDO> chunks) {
        List<Integer> invalid = new ArrayList<>();
        for (FileChunkDO chunk : chunks) {
            if (!chunkChecksumMatches(chunk)) {
                invalid.add(chunk.getChunkIndex());
            }
        }
        return invalid;
    }

    private boolean chunkChecksumMatches(FileChunkDO chunk) {
        if (chunk.getSha256() == null || chunk.getChunkPath() == null) {
            return false;
        }
        try (InputStream in = storageService.open(chunk.getChunkPath())) {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            try (DigestInputStream dis = new DigestInputStream(in, digest)) {
                byte[] buffer = new byte[8192];
                while (dis.read(buffer) != -1) {
                    // 摘要由 DigestInputStream 计算
                }
            }
            return toHex(digest.digest()).equalsIgnoreCase(chunk.getSha256());
        } catch (Exception e) {
            log.warn("校验分片失败：uploadId={}, chunkIndex={}", chunk.getUploadId(), chunk.getChunkIndex(), e);
            return false;
        }
    }

    private String extensionOf(String fileName) {
        if (fileName != null && fileName.lastIndexOf('.') >= 0) {
            return fileName.substring(fileName.lastIndexOf('.'));
        }
        return "";
    }

    private String sha256(byte[] data) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            return toHex(digest.digest(data));
        } catch (Exception e) {
            throw new RuntimeException("计算摘要失败", e);
        }
    }

    private String toHex(byte[] bytes) {
        StringBuilder sb = new StringBuilder();
        for (byte b : bytes) {
            sb.append(String.format("%02x", b));
        }
        return sb.toString();
    }
}
