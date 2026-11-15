package com.campus.contest.module.work.service;

import com.campus.contest.module.work.entity.FileDO;

/**
 * 文件资源归属检查（EP-050 / EP-051）。
 *
 * 功能权限由 @PreAuthorize 负责（没有 file:download 权限返回 403），
 * 这里只负责水平越权：非上传人且非管理员访问他人文件时，按
 * 「资源不存在或对当前用户不可见」返回 404，避免通过状态码探测他人文件是否存在。
 */
public interface FileAccessGuard {

    /**
     * 校验当前用户是否可以访问该文件，不可访问时抛 BizException(40404)。
     */
    void requireDownloadable(FileDO file);
}
