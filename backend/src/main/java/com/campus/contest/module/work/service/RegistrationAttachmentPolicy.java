package com.campus.contest.module.work.service;

import com.campus.contest.module.work.model.ValidatedRegistrationAttachment;

import java.util.List;

public interface RegistrationAttachmentPolicy {

    /**
     * 校验当前队长有权使用全部报名附件。
     * 返回顺序与入参一致；任一文件不合格时整体失败。
     */
    List<ValidatedRegistrationAttachment> validateForCaptain(
            Long captainUserId,
            List<Long> fileIds
    );
}
