package com.campus.contest.module.work.service.impl;

import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.campus.contest.common.BizException;
import com.campus.contest.module.work.dto.UploadDtos;
import com.campus.contest.module.work.entity.FileChunkDO;
import com.campus.contest.module.work.entity.FileDO;
import com.campus.contest.module.work.entity.UploadTaskDO;
import com.campus.contest.module.work.mapper.FileChunkMapper;
import com.campus.contest.module.work.mapper.FileMapper;
import com.campus.contest.module.work.mapper.UploadTaskMapper;
import com.campus.contest.module.work.service.UploadService;
import com.campus.contest.storage.StorageService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.io.ByteArrayInputStream;
import java.io.InputStream;
import java.io.OutputStream;
import java.security.DigestOutputStream;
import java.security.MessageDigest;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

@Slf4j
@Service
public class UploadServiceImpl implements UploadService {

    /** 默认分片 5 MiB，接口文档允许 1 KiB ~ 20 MiB */
    private static final int DEFAULT_CHUNK_SIZE = 5 * 1024 * 1024;
    private static final int MIN_CHUNK_SIZE = 1024;
    private static final int MAX_CHUNK_SIZE = 20 * 1024 * 1024;
    /** 单文件上限 5 GiB */
    private static final long MAX_FILE_SIZE = 5L * 1024 * 1024 * 1024;
    private static final long DEMO_USER_ID = 1L;

    private static final int STATUS_UPLOADING = 1;
    private static final int STATUS_MERGING = 2;
    private static final int STATUS_DONE = 3;
    private static final int STATUS_FAILED = 4;

    private final UploadTaskMapper uploadTaskMapper;
    private final FileChunkMapper fileChunkMapper;
    private final FileMapper fileMapper;
    private final StorageService storageService;

    public UploadServiceImpl(UploadTaskMapper uploadTaskMapper,
                             FileChunkMapper fileChunkMapper,
                             FileMapper fileMapper,
                             StorageService storageService) {
        this.uploadTaskMapper = uploadTaskMapper;
        this.fileChunkMapper = fileChunkMapper;
        this.fileMapper = fileMapper;
        this.storageService = storageService;
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public UploadDtos.InitResponse init(UploadDtos.InitRequest request) {
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

        // 秒传：整个文件已存在，直接返回
        if (sha256 != null) {
            FileDO exist = fileMapper.selectOne(Wrappers.<FileDO>lambdaQuery()
                    .eq(FileDO::getSha256, sha256.toLowerCase())
                    .eq(FileDO::getDeleted, 0)
                    .last("limit 1"));
            if (exist != null) {
                response.setInstant(true);
                response.setFileId(exist.getId());
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
        task.setStatus(STATUS_UPLOADING);
        task.setExpireAt(LocalDateTime.now().plusDays(1));
        task.setUploaderId(DEMO_USER_ID);
        task.setCreatedAt(LocalDateTime.now());
        task.setCreatedBy(DEMO_USER_ID);
        task.setDeleted(0);
        task.setVersion(0);
        uploadTaskMapper.insert(task);

        response.setUploadId(task.getId());
        response.setTotalChunks(totalChunks);
        return response;
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public UploadDtos.ChunkResponse uploadChunk(Long uploadId, Integer chunkIndex, byte[] body) {
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

        String relative = chunkPath(uploadId, chunkIndex);
        storageService.write(relative, new ByteArrayInputStream(body));
        String sha256 = sha256(body);

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
            chunk.setSha256(sha256);
            chunk.setStatus(1);
            chunk.setCreatedAt(LocalDateTime.now());
            chunk.setCreatedBy(DEMO_USER_ID);
            chunk.setDeleted(0);
            chunk.setVersion(0);
            fileChunkMapper.insert(chunk);
        } else {
            chunk.setChunkSize(body.length);
            chunk.setChunkPath(relative);
            chunk.setSha256(sha256);
            chunk.setStatus(1);
            fileChunkMapper.updateById(chunk);
        }

        Long uploaded = fileChunkMapper.selectCount(Wrappers.<FileChunkDO>lambdaQuery()
                .eq(FileChunkDO::getUploadId, uploadId)
                .eq(FileChunkDO::getStatus, 1));
        task.setUploadedChunks(uploaded == null ? 0 : uploaded.intValue());
        uploadTaskMapper.updateById(task);

        UploadDtos.ChunkResponse response = new UploadDtos.ChunkResponse();
        response.setUploadId(uploadId);
        response.setChunkIndex(chunkIndex);
        response.setReceived(task.getUploadedChunks());
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
        response.setUploadId(uploadId);
        response.setStatus(task.getStatus());
        response.setTotalChunks(task.getTotalChunks());
        response.setUploadedChunks(uploaded.size());
        response.setUploaded(uploaded);
        return response;
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public UploadDtos.MergeResponse merge(Long uploadId, UploadDtos.MergeRequest request) {
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
        if (chunks.size() != task.getTotalChunks()) {
            throw new BizException(409, "分片不完整：" + chunks.size() + "/" + task.getTotalChunks());
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

        FileDO file = new FileDO();
        file.setOriginalName(task.getFileName());
        file.setFileSize(task.getTotalSize());
        file.setStorageType(1);
        file.setStoragePath(relative);
        file.setSha256(sha256);
        file.setUploaderId(DEMO_USER_ID);
        file.setCreatedAt(LocalDateTime.now());
        file.setCreatedBy(DEMO_USER_ID);
        file.setDeleted(0);
        file.setVersion(0);
        fileMapper.insert(file);

        task.setFileId(file.getId());
        task.setStatus(STATUS_DONE);
        uploadTaskMapper.updateById(task);
        cleanChunks(uploadId, chunks);

        UploadDtos.MergeResponse response = new UploadDtos.MergeResponse();
        response.setFileId(file.getId());
        response.setPath(relative);
        response.setSize(task.getTotalSize());
        response.setSha256(sha256);
        return response;
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

    private UploadTaskDO requireTask(Long uploadId) {
        UploadTaskDO task = uploadTaskMapper.selectById(uploadId);
        if (task == null || Integer.valueOf(1).equals(task.getDeleted())) {
            throw new BizException(404, "上传任务不存在");
        }
        return task;
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
