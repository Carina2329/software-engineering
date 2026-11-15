package com.campus.contest.security;

import java.time.Instant;
import java.util.Set;

/**
 * Redis 中的认证会话快照。Refresh Token 只保存摘要，不保存明文。
 */
public record AuthSession(
        String sessionId,
        Long userId,
        String username,
        String realName,
        String email,
        Set<String> roleCodes,
        Set<String> permissionCodes,
        Set<String> dataScopes,
        String refreshTokenHash,
        Instant refreshExpiresAt,
        Instant createdAt
) {

    public AuthenticatedUser toPrincipal() {
        return new AuthenticatedUser(userId, sessionId, roleCodes, permissionCodes, dataScopes);
    }
}
