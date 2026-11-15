package com.campus.contest.security;

import com.campus.contest.common.BizException;
import com.campus.contest.common.ErrorCode;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Component;

/**
 * 从 Spring Security 上下文读取当前认证用户，供其他模块统一使用。
 */
@Component
public class SecurityCurrentUserProvider implements CurrentUserProvider {

    @Override
    public Long requireUserId() {
        return requirePrincipal().userId();
    }

    @Override
    public String requireSessionId() {
        return requirePrincipal().sessionId();
    }

    private AuthenticatedUser requirePrincipal() {
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        if (authentication == null
                || !authentication.isAuthenticated()
                || !(authentication.getPrincipal() instanceof AuthenticatedUser principal)) {
            throw new BizException(
                    ErrorCode.UNAUTHENTICATED.getCode(),
                    ErrorCode.UNAUTHENTICATED.getMessage()
            );
        }
        return principal;
    }
}
