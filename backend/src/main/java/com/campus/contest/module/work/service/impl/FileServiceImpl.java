package com.campus.contest.module.work.service.impl;

import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.campus.contest.common.BizException;
import com.campus.contest.module.work.dto.FileUploadResult;
import com.campus.contest.module.work.entity.FileDO;
import com.campus.contest.module.work.mapper.FileMapper;
import com.campus.contest.module.work.service.FileService;
import com.campus.contest.security.CurrentUserProvider;
import com.campus.contest.storage.StorageService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;

import java.io.InputStream;
import java.security.DigestInputStream;
import java.security.MessageDigest;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

@Slf4j
@Service
public class FileServiceImpl extends ServiceImpl<FileMapper, FileDO> implements FileService {

    /** 接口文档规定单次最多 20 个文件 */
    private static final int MAX_BATCH = 20;
    private final StorageService storageService;
    /** 上传人取当前登录用户，禁止再用固定的演示账号 */
    private final CurrentUserProvider currentUserProvider;

    public FileServiceImpl(StorageService storageService, CurrentUserProvider currentUserProvider) {
        this.storageService = storageService;
        this.currentUserProvider = currentUserProvider;
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public List<FileUploadResult> batchUpload(List<MultipartFile> files, String purpose) {
        if (files == null || files.isEmpty()) {
            throw new BizException("请选择要上传的文件");
        }
        if (files.size() > MAX_BATCH) {
            throw new BizException("一次最多上传 " + MAX_BATCH + " 个文件");
        }
        log.info("批量上传开始：{} 个文件，用途={}", files.size(), purpose);
        Long uploaderId = currentUserProvider.requireUserId();

        List<FileUploadResult> results = new ArrayList<>(files.size());
        for (MultipartFile file : files) {
            FileUploadResult result = new FileUploadResult();
            result.setFileName(file.getOriginalFilename());
            result.setSize(file.getSize());
            try {
                String sha256 = sha256(file);
                FileDO exist = getOne(Wrappers.<FileDO>lambdaQuery()
                        .eq(FileDO::getSha256, sha256)
                        .eq(FileDO::getStorageType, storageService.type())
                        .eq(FileDO::getDeleted, 0)
                        .last("limit 1"), false);
                if (exist != null) {
                    // 秒传只允许复用本人文件：复用他人文件会把 file_id 和存储路径泄露给其他账号
                    if (!Objects.equals(exist.getUploaderId(), uploaderId)) {
                        throw new BizException("相同内容的文件已归属其他账号，暂不支持秒传");
                    }
                    result.setFileId(exist.getId());
                    result.setPath(exist.getStoragePath());
                    result.setSuccess(true);
                    result.setDuplicated(true);
                    results.add(result);
                    continue;
                }

                String relativePath = storageService.store(file);
                FileDO entity = new FileDO();
                entity.setOriginalName(file.getOriginalFilename());
                entity.setMimeType(file.getContentType());
                entity.setFileSize(file.getSize());
                entity.setStorageType(storageService.type());
                entity.setStoragePath(relativePath);
                entity.setSha256(sha256);
                entity.setUploaderId(uploaderId);
                entity.setCreatedAt(LocalDateTime.now());
                entity.setCreatedBy(uploaderId);
                entity.setDeleted(0);
                entity.setVersion(0);
                save(entity);

                result.setFileId(entity.getId());
                result.setPath(relativePath);
                result.setSuccess(true);
                results.add(result);
            } catch (Exception e) {
                log.warn("文件上传失败：{}", file.getOriginalFilename(), e);
                result.setSuccess(false);
                result.setError(e.getMessage());
                results.add(result);
            }
        }
        return results;
    }

    /** 计算文件 SHA-256，用于去重（秒传） */
    private String sha256(MultipartFile file) throws Exception {
        MessageDigest digest = MessageDigest.getInstance("SHA-256");
        try (InputStream in = file.getInputStream();
             DigestInputStream dis = new DigestInputStream(in, digest)) {
            byte[] buffer = new byte[8192];
            while (dis.read(buffer) != -1) {
                // 读取即可，摘要由 DigestInputStream 计算
            }
        }
        StringBuilder sb = new StringBuilder();
        for (byte b : digest.digest()) {
            sb.append(String.format("%02x", b));
        }
        return sb.toString();
    }
}
