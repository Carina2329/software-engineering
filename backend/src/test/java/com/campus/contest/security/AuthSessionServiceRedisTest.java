package com.campus.contest.security;

import com.campus.contest.common.BizException;
import com.campus.contest.common.ErrorCode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;
import org.springframework.data.redis.connection.lettuce.LettuceConnectionFactory;
import org.springframework.data.redis.core.StringRedisTemplate;

import java.time.Duration;
import java.util.Set;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

@EnabledIfEnvironmentVariable(named = "RUN_REDIS_TESTS", matches = "true")
class AuthSessionServiceRedisTest {

    @Test
    void createsIndexesRotatesAndRevokesSession() {
        LettuceConnectionFactory factory = connectionFactory(6379);
        try {
            StringRedisTemplate redis = new StringRedisTemplate(factory);
            SessionTokenCodec codec = new SessionTokenCodec();
            AuthSessionService sessions = new AuthSessionService(redis, new ObjectMapper(), codec);
            long userId = Math.abs(UUID.randomUUID().getMostSignificantBits());
            String sessionId = codec.newSessionId();
            String refreshToken = codec.newRefreshToken(sessionId);

            AuthSession created = sessions.create(
                    userId,
                    "redis_user",
                    "Redis User",
                    "redis@example.edu",
                    Set.of("STUDENT"),
                    Set.of("competition:read"),
                    Set.of("SELF"),
                    refreshToken,
                    Duration.ofMinutes(10)
            );
            assertEquals(sessionId, created.sessionId());
            assertTrue(sessions.find(sessionId).isPresent());
            assertTrue(sessions.findSessionIds(userId).contains(sessionId));
            assertTrue(sessions.matchesRefreshToken(created, refreshToken));

            String rotatedToken = codec.newRefreshToken(sessionId);
            AuthSession rotated = sessions.rotate(
                    created,
                    refreshToken,
                    rotatedToken,
                    Duration.ofMinutes(10)
            );
            assertTrue(sessions.matchesRefreshToken(rotated, rotatedToken));
            assertFalse(sessions.matchesRefreshToken(rotated, refreshToken));

            sessions.revokeSession(sessionId);
            assertTrue(sessions.find(sessionId).isEmpty());
            assertFalse(sessions.findSessionIds(userId).contains(sessionId));
        } finally {
            factory.destroy();
        }
    }

    @Test
    void revokeAllSessionsClearsUserIndex() {
        LettuceConnectionFactory factory = connectionFactory(6379);
        try {
            StringRedisTemplate redis = new StringRedisTemplate(factory);
            SessionTokenCodec codec = new SessionTokenCodec();
            AuthSessionService sessions = new AuthSessionService(redis, new ObjectMapper(), codec);
            long userId = Math.abs(UUID.randomUUID().getMostSignificantBits());

            for (int i = 0; i < 2; i++) {
                String sessionId = codec.newSessionId();
                sessions.create(
                        userId,
                        "redis_user",
                        "Redis User",
                        "redis@example.edu",
                        Set.of("STUDENT"),
                        Set.of(),
                        Set.of("SELF"),
                        codec.newRefreshToken(sessionId),
                        Duration.ofMinutes(10)
                );
            }
            assertEquals(2, sessions.findSessionIds(userId).size());

            sessions.revokeAllSessions(userId);

            assertTrue(sessions.findSessionIds(userId).isEmpty());
        } finally {
            factory.destroy();
        }
    }

    @Test
    void redisFailureFailsClosedWith503Code() {
        LettuceConnectionFactory factory = connectionFactory(6390);
        try {
            StringRedisTemplate redis = new StringRedisTemplate(factory);
            SessionTokenCodec codec = new SessionTokenCodec();
            AuthSessionService sessions = new AuthSessionService(redis, new ObjectMapper(), codec);
            String sessionId = codec.newSessionId();

            BizException exception = assertThrows(
                    BizException.class,
                    () -> sessions.create(
                            1L,
                            "redis_user",
                            "Redis User",
                            null,
                            Set.of(),
                            Set.of(),
                            Set.of(),
                            codec.newRefreshToken(sessionId),
                            Duration.ofMinutes(1)
                    )
            );
            assertEquals(ErrorCode.AUTH_IDEMPOTENCY_UNAVAILABLE.getCode(), exception.getCode());
        } finally {
            factory.destroy();
        }
    }

    private LettuceConnectionFactory connectionFactory(int port) {
        LettuceConnectionFactory factory = new LettuceConnectionFactory("127.0.0.1", port);
        String password = System.getenv("REDIS_PASSWORD");
        if (password != null && !password.isBlank()) {
            factory.setPassword(password);
        }
        factory.afterPropertiesSet();
        return factory;
    }
}
