package com.campus.contest.module.work.service.impl;

import com.campus.contest.common.BizException;
import com.campus.contest.common.ErrorCode;
import com.campus.contest.module.work.entity.FileDO;
import com.campus.contest.module.work.mapper.FileAccessScopeMapper;
import com.campus.contest.module.work.service.FileAccessGuard;
import com.campus.contest.security.AuthenticatedUser;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Service;

import java.util.Objects;

/**
 * 文件归属检查实现（EP-050 / EP-051）。
 *
 * OpenAPI 给 EP-050 标注的数据范围是 SELF / TEAM / GUIDED / ASSIGNED / ALL，判定顺序：
 * 1. 未认证 → 401；
 * 2. ALL 数据范围或 ADMIN 角色 → 允许（管理员查看用户文件由审计日志留痕）；
 * 3. SELF：当前用户就是上传人 → 允许（本人文件永不因数据范围收窄而不可见）；
 * 4. TEAM：当前用户是该文件所属作品版本的队员 → 允许；
 * 5. GUIDED：当前用户是该文件所属队伍的指导教师 → 允许；
 * 6. ASSIGNED：当前用户是该文件所属作品版本的指派评委 → 允许；
 * 7. 其他情况 → 404，不泄露文件是否存在，也不返回他人文件名。
 *
 * 只有当前用户确实拥有对应数据范围时才会去查库，避免多打一次 SQL 也避免越权。
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class FileAccessGuardImpl implements FileAccessGuard {

    private static final String ADMIN_ROLE = "ADMIN";
    private static final String SCOPE_ALL = "ALL";
    private static final String SCOPE_TEAM = "TEAM";
    private static final String SCOPE_GUIDED = "GUIDED";
    private static final String SCOPE_ASSIGNED = "ASSIGNED";

    private final FileAccessScopeMapper fileAccessScopeMapper;

    @Override
    public void requireDownloadable(FileDO file) {
        if (file == null || Integer.valueOf(1).equals(file.getDeleted())) {
            throw notFound();
        }
        AuthenticatedUser current = currentUser();
        if (current == null || current.userId() == null) {
            throw new BizException(ErrorCode.UNAUTHENTICATED.getCode(), "未登录或令牌缺失");
        }
        if (current.roleCodes().contains(ADMIN_ROLE) || current.dataScopes().contains(SCOPE_ALL)) {
            return;
        }
        if (Objects.equals(current.userId(), file.getUploaderId())) {
            return;
        }
        Long userId = current.userId();
        if (hasScope(current, SCOPE_TEAM) && fileAccessScopeMapper.countTeamAccess(file.getId(), userId) > 0) {
            return;
        }
        if (hasScope(current, SCOPE_GUIDED) && fileAccessScopeMapper.countGuidedAccess(file.getId(), userId) > 0) {
            return;
        }
        if (hasScope(current, SCOPE_ASSIGNED)
                && fileAccessScopeMapper.countAssignedAccess(file.getId(), userId) > 0) {
            return;
        }
        log.debug("文件数据范围校验未通过：fileId={}, userId={}, scopes={}",
                file.getId(), userId, current.dataScopes());
        throw notFound();
    }

    private boolean hasScope(AuthenticatedUser current, String scope) {
        // SELF 之外的范围都必须显式授予；没有任何范围时不做额外查询
        return !current.dataScopes().isEmpty() && current.dataScopes().contains(scope);
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
