package com.campus.contest.module.system.service;

import com.campus.contest.common.BizException;
import com.campus.contest.common.ErrorCode;
import com.campus.contest.common.idempotency.IdempotencyScope;
import com.campus.contest.common.idempotency.IdempotencyService;
import com.campus.contest.module.system.entity.SysUserDO;
import com.campus.contest.module.system.dto.AuthorizationSnapshot;
import com.campus.contest.module.system.vo.AuthTokenData;
import com.campus.contest.module.system.vo.AuthUserData;
import com.campus.contest.security.AuthSession;
import com.campus.contest.security.AuthSessionService;
import com.campus.contest.security.JwtService;
import com.campus.contest.security.SessionTokenCodec;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.time.Duration;
import java.util.List;
import java.util.Map;

/**
 * 登录成功后的统一令牌签发、刷新和退出逻辑。
 */
@Service
@RequiredArgsConstructor
public class AuthTokenService {

    private static final String LOGOUT_OPERATION = "auth.logout";

    private final AuthSessionService sessionService;
    private final SessionTokenCodec tokenCodec;
    private final JwtService jwtService;
    private final IdempotencyService idempotencyService;

    @Value("${app.auth.jwt.refresh-token-ttl-seconds:604800}")
    private long refreshTokenTtlSeconds;

    public LoginResult createSession(
            SysUserDO user,
            AuthorizationSnapshot snapshot
    ) {
        String sessionId = tokenCodec.newSessionId();
        String refreshToken = tokenCodec.newRefreshToken(sessionId);
        Duration ttl = refreshTtl();
        AuthSession session = sessionService.create(
                user.getId(),
                user.getUsername(),
                user.getRealName(),
                user.getEmail(),
                snapshot.roleCodes(),
                snapshot.permissionCodes(),
                snapshot.dataScopes(),
                refreshToken,
                ttl
        );
        try {
            JwtService.IssuedJwt jwt = jwtService.issue(session.userId(), session.sessionId());
            return new LoginResult(toTokenData(session, jwt), refreshToken, session.sessionId());
        } catch (RuntimeException e) {
            sessionService.revokeSession(sessionId);
            throw e;
        }
    }

    public LoginResult refresh(String rawRefreshToken) {
        String sessionId = tokenCodec.extractSessionId(rawRefreshToken)
                .orElseThrow(this::tokenExpiredOrRevoked);
        AuthSession session = sessionService.find(sessionId)
                .orElseThrow(this::tokenExpiredOrRevoked);
        if (!sessionService.matchesRefreshToken(session, rawRefreshToken)) {
            throw tokenExpiredOrRevoked();
        }

        JwtService.IssuedJwt jwt = jwtService.issue(session.userId(), session.sessionId());
        String rotatedRefreshToken = tokenCodec.newRefreshToken(sessionId);
        AuthSession rotated = sessionService.rotate(
                session,
                rawRefreshToken,
                rotatedRefreshToken,
                refreshTtl()
        );
        return new LoginResult(toTokenData(rotated, jwt), rotatedRefreshToken, sessionId);
    }

    public void logout(String rawRefreshToken, String idempotencyKey) {
        String sessionId = tokenCodec.extractSessionId(rawRefreshToken)
                .orElseThrow(this::tokenExpiredOrRevoked);
        IdempotencyScope scope = new IdempotencyScope(IdempotencyScope.PrincipalType.SESSION, sessionId);
        idempotencyService.execute(
                scope,
                LOGOUT_OPERATION,
                idempotencyKey,
                Map.of("session_id", sessionId),
                refreshTtl(),
                Boolean.class,
                () -> {
                    AuthSession session = sessionService.find(sessionId)
                            .orElseThrow(this::tokenExpiredOrRevoked);
                    if (!sessionService.matchesRefreshToken(session, rawRefreshToken)) {
                        throw tokenExpiredOrRevoked();
                    }
                    sessionService.revokeSession(sessionId);
                    return Boolean.TRUE;
                }
        );
    }

    private AuthTokenData toTokenData(AuthSession session, JwtService.IssuedJwt jwt) {
        return new AuthTokenData(
                jwt.token(),
                "Bearer",
                jwt.expiresAt(),
                new AuthUserData(
                        String.valueOf(session.userId()),
                        session.username(),
                        session.realName(),
                        session.email()
                ),
                List.copyOf(session.roleCodes()),
                List.copyOf(session.permissionCodes())
        );
    }

    private Duration refreshTtl() {
        return Duration.ofSeconds(refreshTokenTtlSeconds);
    }

    private BizException tokenExpiredOrRevoked() {
        return new BizException(
                ErrorCode.TOKEN_EXPIRED_OR_REVOKED.getCode(),
                ErrorCode.TOKEN_EXPIRED_OR_REVOKED.getMessage()
        );
    }

    public record LoginResult(
            AuthTokenData tokenData,
            String refreshToken,
            String sessionId
    ) {
    }
}
