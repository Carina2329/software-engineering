package com.campus.contest.security;

import com.campus.contest.common.BizException;
import com.campus.contest.common.ErrorCode;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.dao.DataAccessException;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.script.DefaultRedisScript;
import org.springframework.data.redis.core.script.RedisScript;
import org.springframework.stereotype.Service;

import java.time.Duration;
import java.time.Instant;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;

/**
 * 认证会话的 Redis 唯一存储入口，同时对外提供公共会话撤销能力。
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class AuthSessionService implements SessionRevocationService {

    private static final String SESSION_KEY_PREFIX = "auth:session:";
    private static final String USER_SESSIONS_KEY_PREFIX = "auth:user-sessions:";
    private static final TypeReference<LinkedHashSet<String>> STRING_SET_TYPE = new TypeReference<>() {
    };

    private static final RedisScript<Long> CREATE_SCRIPT = new DefaultRedisScript<>(
            """
            redis.call('HSET', KEYS[1],
                'userId', ARGV[1],
                'username', ARGV[2],
                'realName', ARGV[3],
                'email', ARGV[4],
                'roleCodes', ARGV[5],
                'permissionCodes', ARGV[6],
                'dataScopes', ARGV[7],
                'refreshTokenHash', ARGV[8],
                'refreshExpiresAt', ARGV[9],
                'createdAt', ARGV[10],
                'sessionId', ARGV[12])
            redis.call('PEXPIRE', KEYS[1], ARGV[11])
            redis.call('SADD', KEYS[2], ARGV[12])
            redis.call('PEXPIRE', KEYS[2], ARGV[11])
            return 1
            """,
            Long.class
    );

    private static final RedisScript<Long> ROTATE_SCRIPT = new DefaultRedisScript<>(
            """
            local current = redis.call('HGET', KEYS[1], 'refreshTokenHash')
            if not current or current ~= ARGV[1] then
                return 0
            end
            redis.call('HSET', KEYS[1],
                'refreshTokenHash', ARGV[2],
                'refreshExpiresAt', ARGV[3])
            redis.call('PEXPIRE', KEYS[1], ARGV[4])
            redis.call('SADD', KEYS[2], ARGV[5])
            redis.call('PEXPIRE', KEYS[2], ARGV[4])
            return 1
            """,
            Long.class
    );

    private static final RedisScript<Long> DELETE_SCRIPT = new DefaultRedisScript<>(
            """
            redis.call('DEL', KEYS[1])
            redis.call('SREM', KEYS[2], ARGV[1])
            if redis.call('SCARD', KEYS[2]) == 0 then
                redis.call('DEL', KEYS[2])
            end
            return 1
            """,
            Long.class
    );

    private final StringRedisTemplate redisTemplate;
    private final ObjectMapper objectMapper;
    private final SessionTokenCodec tokenCodec;

    public AuthSession create(
            Long userId,
            String username,
            String realName,
            String email,
            Set<String> roleCodes,
            Set<String> permissionCodes,
            Set<String> dataScopes,
            String refreshToken,
            Duration ttl
    ) {
        String sessionId = tokenCodec.extractSessionId(refreshToken)
                .orElseThrow(() -> new BizException(
                        ErrorCode.UNAUTHENTICATED.getCode(),
                        "Refresh Token 不合法"
                ));
        Instant now = Instant.now();
        Instant refreshExpiresAt = now.plus(ttl);
        AuthSession session = new AuthSession(
                sessionId,
                userId,
                safe(username),
                safe(realName),
                safe(email),
                safeSet(roleCodes),
                safeSet(permissionCodes),
                safeSet(dataScopes),
                tokenCodec.hash(refreshToken),
                refreshExpiresAt,
                now
        );
        try {
            Long result = redisTemplate.execute(
                    CREATE_SCRIPT,
                    List.of(sessionKey(sessionId), userSessionsKey(userId)),
                    String.valueOf(userId),
                    session.username(),
                    session.realName(),
                    session.email(),
                    objectMapper.writeValueAsString(session.roleCodes()),
                    objectMapper.writeValueAsString(session.permissionCodes()),
                    objectMapper.writeValueAsString(session.dataScopes()),
                    session.refreshTokenHash(),
                    session.refreshExpiresAt().toString(),
                    session.createdAt().toString(),
                    String.valueOf(ttl.toMillis()),
                    sessionId
            );
            if (result == null || result != 1L) {
                throw unavailable();
            }
            return session;
        } catch (DataAccessException e) {
            log.warn("Redis 会话写入失败: {}", e.getClass().getSimpleName());
            throw unavailable();
        } catch (JsonProcessingException e) {
            log.warn("Redis 会话序列化失败: {}", e.getClass().getSimpleName());
            throw unavailable();
        }
    }

    public Optional<AuthSession> find(String sessionId) {
        if (sessionId == null || sessionId.isBlank()) {
            return Optional.empty();
        }
        try {
            Map<Object, Object> values = redisTemplate.opsForHash().entries(sessionKey(sessionId));
            if (values == null || values.isEmpty()) {
                return Optional.empty();
            }
            return Optional.of(toSession(values));
        } catch (DataAccessException e) {
            throw unavailable();
        }
    }

    public boolean matchesRefreshToken(AuthSession session, String rawRefreshToken) {
        return session != null
                && rawRefreshToken != null
                && tokenCodec.matches(rawRefreshToken, session.refreshTokenHash());
    }

    /**
     * 原子比较旧 Refresh Token 摘要并轮换，保证并发刷新只有一个请求成功。
     */
    public AuthSession rotate(
            AuthSession session,
            String oldRefreshToken,
            String newRefreshToken,
            Duration ttl
    ) {
        if (session == null || oldRefreshToken == null || newRefreshToken == null) {
            throw tokenExpiredOrRevoked();
        }
        if (!matchesRefreshToken(session, oldRefreshToken)) {
            throw tokenExpiredOrRevoked();
        }
        Instant newExpiresAt = Instant.now().plus(ttl);
        try {
            Long result = redisTemplate.execute(
                    ROTATE_SCRIPT,
                    List.of(sessionKey(session.sessionId()), userSessionsKey(session.userId())),
                    session.refreshTokenHash(),
                    tokenCodec.hash(newRefreshToken),
                    newExpiresAt.toString(),
                    String.valueOf(ttl.toMillis()),
                    session.sessionId()
            );
            if (result == null || result != 1L) {
                throw tokenExpiredOrRevoked();
            }
            return find(session.sessionId()).orElseThrow(this::tokenExpiredOrRevoked);
        } catch (DataAccessException e) {
            throw unavailable();
        }
    }

    @Override
    public void revokeSession(String sessionId) {
        if (sessionId == null || sessionId.isBlank()) {
            return;
        }
        AuthSession session = find(sessionId).orElse(null);
        delete(sessionId, session == null ? null : session.userId());
    }

    @Override
    public void revokeAllSessions(Long userId) {
        if (userId == null) {
            return;
        }
        try {
            Set<String> sessionIds = redisTemplate.opsForSet().members(userSessionsKey(userId));
            if (sessionIds != null) {
                for (String sessionId : sessionIds) {
                    revokeSession(sessionId);
                }
            }
            redisTemplate.delete(userSessionsKey(userId));
        } catch (DataAccessException e) {
            throw unavailable();
        }
    }

    @Override
    public Set<String> findSessionIds(Long userId) {
        if (userId == null) {
            return Set.of();
        }
        try {
            Set<String> sessionIds = redisTemplate.opsForSet().members(userSessionsKey(userId));
            if (sessionIds == null || sessionIds.isEmpty()) {
                return Set.of();
            }
            Set<String> active = new LinkedHashSet<>();
            for (String sessionId : sessionIds) {
                if (find(sessionId).isPresent()) {
                    active.add(sessionId);
                } else {
                    redisTemplate.opsForSet().remove(userSessionsKey(userId), sessionId);
                }
            }
            return active;
        } catch (DataAccessException e) {
            throw unavailable();
        }
    }

    private void delete(String sessionId, Long userId) {
        try {
            String indexKey = userId == null
                    ? userSessionsKey(0L)
                    : userSessionsKey(userId);
            redisTemplate.execute(
                    DELETE_SCRIPT,
                    List.of(sessionKey(sessionId), indexKey),
                    sessionId
            );
        } catch (DataAccessException e) {
            throw unavailable();
        }
    }

    private AuthSession toSession(Map<Object, Object> values) {
        try {
            return new AuthSession(
                    value(values, "sessionId"),
                    parseLong(value(values, "userId")),
                    value(values, "username"),
                    emptyToNull(value(values, "realName")),
                    emptyToNull(value(values, "email")),
                    readSet(value(values, "roleCodes")),
                    readSet(value(values, "permissionCodes")),
                    readSet(value(values, "dataScopes")),
                    value(values, "refreshTokenHash"),
                    parseInstant(value(values, "refreshExpiresAt")),
                    parseInstant(value(values, "createdAt"))
            );
        } catch (JsonProcessingException | RuntimeException e) {
            throw unavailable();
        }
    }

    private Set<String> readSet(String json) throws JsonProcessingException {
        if (json == null || json.isBlank()) {
            return Set.of();
        }
        return objectMapper.readValue(json, STRING_SET_TYPE);
    }

    private String value(Map<Object, Object> values, String key) {
        Object value = values.get(key);
        return value == null ? null : String.valueOf(value);
    }

    private Long parseLong(String value) {
        if (value == null || value.isBlank()) {
            throw new IllegalArgumentException("会话缺少用户 ID");
        }
        return Long.valueOf(value);
    }

    private Instant parseInstant(String value) {
        return value == null || value.isBlank() ? null : Instant.parse(value);
    }

    private Set<String> safeSet(Set<String> values) {
        return values == null ? Set.of() : Set.copyOf(values);
    }

    private String safe(String value) {
        return value == null ? "" : value;
    }

    private String emptyToNull(String value) {
        return value == null || value.isBlank() ? null : value;
    }

    private String sessionKey(String sessionId) {
        return SESSION_KEY_PREFIX + sessionId;
    }

    private String userSessionsKey(Long userId) {
        return USER_SESSIONS_KEY_PREFIX + userId;
    }

    private BizException unavailable() {
        return new BizException(
                ErrorCode.AUTH_IDEMPOTENCY_UNAVAILABLE.getCode(),
                ErrorCode.AUTH_IDEMPOTENCY_UNAVAILABLE.getMessage()
        );
    }

    private BizException tokenExpiredOrRevoked() {
        return new BizException(
                ErrorCode.TOKEN_EXPIRED_OR_REVOKED.getCode(),
                ErrorCode.TOKEN_EXPIRED_OR_REVOKED.getMessage()
        );
    }
}
