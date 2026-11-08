package com.campus.contest.module.work.service.impl;

import com.campus.contest.common.BizException;
import com.campus.contest.common.ErrorCode;
import com.campus.contest.module.work.entity.FileDO;
import com.campus.contest.module.work.service.FileAccessGuard;
import com.campus.contest.security.AuthenticatedUser;
import lombok.RequiredArgsConstructor;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Service;

import java.util.Objects;

/**
 * 文件归属检查实现。
 *
 * 判定顺序：
 * 1. 未认证 → 401（由 SecurityConfig 兜底，这里再防一层）；
 * 2. 当前用户是 ADMIN → 允许（管理员查看用户文件的场景由审计日志留痕）；
 * 3. 当前用户是上传人 → 允许；
 * 4. 其他情况 → 404，不泄露文件是否存在，也不返回他人文件名。
 */
@Service
@RequiredArgsConstructor
public class FileAccessGuardImpl implements FileAccessGuard {

    private static final String ADMIN_ROLE = "ADMIN";

    @Override
    public void requireDownloadable(FileDO file) {
        if (file == null) {
            throw notFound();
        }
        AuthenticatedUser current = currentUser();
        if (current == null || current.userId() == null) {
            throw new BizException(ErrorCode.UNAUTHENTICATED.getCode(), "未登录或令牌缺失");
        }
        if (current.roleCodes().contains(ADMIN_ROLE)) {
            return;
        }
        if (Objects.equals(current.userId(), file.getUploaderId())) {
            return;
        }
        throw notFound();
    }

    private AuthenticatedUser currentUser() {
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        if (authentication == null || !authentication.isAuthenticated()) {
            return null;
        }
        if (authentication.getPrincipal() instanceof AuthenticatedUser user) {
            return user;
        }
        return null;
    }

    private BizException notFound() {
        return new BizException(
                ErrorCode.RESOURCE_NOT_FOUND.getCode(),
                "资源不存在或对当前用户不可见"
        );
    }
}
