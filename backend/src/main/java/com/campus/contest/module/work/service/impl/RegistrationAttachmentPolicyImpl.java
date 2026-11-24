package com.campus.contest.module.work.service.impl;

import com.campus.contest.common.BizException;
import com.campus.contest.common.ErrorCode;
import com.campus.contest.module.work.entity.FileDO;
import com.campus.contest.module.work.mapper.FileMapper;
import com.campus.contest.module.work.model.ValidatedRegistrationAttachment;
import com.campus.contest.module.work.service.RegistrationAttachmentPolicy;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Objects;
import java.util.Set;

@Service
@RequiredArgsConstructor
public class RegistrationAttachmentPolicyImpl implements RegistrationAttachmentPolicy {

    static final int MAX_ATTACHMENTS = 20;
    static final long MAX_FILE_SIZE = 5L * 1024 * 1024 * 1024;
    static final String MATERIAL_TYPE = "REGISTRATION_ATTACHMENT";

    private final FileMapper fileMapper;

    @Override
    public List<ValidatedRegistrationAttachment> validateForCaptain(
            Long captainUserId,
            List<Long> fileIds
    ) {
        if (captainUserId == null || fileIds == null || fileIds.isEmpty()) {
            throw invalid("报名附件不能为空");
        }
        if (fileIds.size() > MAX_ATTACHMENTS) {
            throw invalid("一次最多提交 " + MAX_ATTACHMENTS + " 个报名附件");
        }
        Set<Long> seen = new HashSet<>();
        for (Long fileId : fileIds) {
            if (fileId == null || !seen.add(fileId)) {
                throw invalid("报名附件 ID 不能为空或重复");
            }
        }
        List<ValidatedRegistrationAttachment> result = new ArrayList<>(fileIds.size());
        for (Long fileId : fileIds) {
            FileDO file = fileMapper.selectById(fileId);
            if (file == null
                    || Integer.valueOf(1).equals(file.getDeleted())
                    || !Objects.equals(captainUserId, file.getUploaderId())) {
                throw notFound();
            }
            if (!StringUtils.hasText(file.getOriginalName())
                    || !StringUtils.hasText(file.getStoragePath())
                    || file.getStorageType() == null
                    || file.getFileSize() == null
                    || file.getFileSize() <= 0
                    || file.getFileSize() > MAX_FILE_SIZE) {
                throw invalid("报名附件名称或大小不合法");
            }
            String mimeType = StringUtils.hasText(file.getMimeType())
                    ? file.getMimeType().trim()
                    : "application/octet-stream";
            result.add(new ValidatedRegistrationAttachment(
                    file.getId(),
                    file.getOriginalName(),
                    mimeType,
                    file.getFileSize(),
                    MATERIAL_TYPE
            ));
        }
        return List.copyOf(result);
    }

    private BizException invalid(String message) {
        return new BizException(ErrorCode.INVALID_PARAM.getCode(), message);
    }

    private BizException notFound() {
        return new BizException(
                ErrorCode.RESOURCE_NOT_FOUND.getCode(),
                "报名附件不存在或对当前用户不可见"
        );
    }
}
