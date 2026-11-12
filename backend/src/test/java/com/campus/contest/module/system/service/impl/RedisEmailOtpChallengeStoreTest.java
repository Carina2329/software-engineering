package com.campus.contest.module.system.service.impl;

import com.campus.contest.common.BizException;
import com.campus.contest.common.ErrorCode;
import com.campus.contest.module.system.enums.EmailOtpScene;
import com.campus.contest.module.system.service.VerificationTokens;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.script.RedisScript;

import java.time.Duration;
import java.time.Instant;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class RedisEmailOtpChallengeStoreTest {

    private StringRedisTemplate redisTemplate;
    private RedisEmailOtpChallengeStore store;

    @BeforeEach
    void setUp() {
        redisTemplate = mock(StringRedisTemplate.class);
        store = new RedisEmailOtpChallengeStore(redisTemplate);
    }

    @Test
    @SuppressWarnings("unchecked")
    void createOrReplaceReturnsChallenge() {
        when(redisTemplate.execute(
                any(RedisScript.class), anyList(), any(Object[].class)
        )).thenReturn(1L);

        RedisEmailOtpChallengeStore.CreatedChallenge challenge = store.createOrReplace(
                "Student@Example.edu",
                EmailOtpScene.REGISTER,
                VerificationTokens.sha256Hex("123456"),
                Duration.ofMinutes(5)
        );

        assertNotNull(challenge.challengeId());
        assertTrue(challenge.expiresAt().isAfter(Instant.now()));
    }

    @Test
    @SuppressWarnings("unchecked")
    void verifySuccessDoesNotThrow() {
        when(redisTemplate.execute(
                any(RedisScript.class), anyList(), any(Object[].class)
        )).thenReturn(1L);

        store.verifyAndConsume(
                "challenge-1",
                "student@example.edu",
                VerificationTokens.sha256Hex("123456"),
                EmailOtpScene.REGISTER
        );
    }

    @Test
    @SuppressWarnings("unchecked")
    void verifyMismatchThrowsInvalidParam() {
        when(redisTemplate.execute(
                any(RedisScript.class), anyList(), any(Object[].class)
        )).thenReturn(2L);

        BizException exception = assertThrows(
                BizException.class,
                () -> store.verifyAndConsume(
                        "challenge-2",
                        "student@example.edu",
                        VerificationTokens.sha256Hex("123456"),
                        EmailOtpScene.REGISTER
                )
        );

        assertEquals(ErrorCode.INVALID_PARAM.getCode(), exception.getCode());
    }

    @Test
    @SuppressWarnings("unchecked")
    void verifyExhaustedThrowsRateLimited() {
        when(redisTemplate.execute(
                any(RedisScript.class), anyList(), any(Object[].class)
        )).thenReturn(4L);

        BizException exception = assertThrows(
                BizException.class,
                () -> store.verifyAndConsume(
                        "challenge-3",
                        "student@example.edu",
                        VerificationTokens.sha256Hex("123456"),
                        EmailOtpScene.REGISTER
                )
        );

        assertEquals(ErrorCode.RATE_LIMITED.getCode(), exception.getCode());
    }

    @Test
    @SuppressWarnings("unchecked")
    void currentCheckMapsLuaResult() {
        when(redisTemplate.execute(
                any(RedisScript.class), anyList(), any(Object[].class)
        )).thenReturn(1L, 0L);

        assertTrue(store.isCurrentAndActive(
                "challenge-4", "Student@Example.edu", EmailOtpScene.REGISTER, Instant.now()
        ));
        assertFalse(store.isCurrentAndActive(
                "challenge-4", "Student@Example.edu", EmailOtpScene.REGISTER, Instant.now()
        ));
    }

    @Test
    void rollbackCallsAtomicScript() {
        store.rollbackFailedDispatch("challenge-5", "Student@Example.edu", EmailOtpScene.LOGIN);

        verify(redisTemplate).execute(
                any(RedisScript.class),
                anyList(),
                any(Object[].class)
        );
    }

    @Test
    void currentIndexKeyUsesNormalizedEmailHash() {
        String expected = "auth:challenge:current:REGISTER:"
                + VerificationTokens.sha256Hex("student@example.edu");

        assertEquals(expected, store.indexKey(" Student@Example.edu ", EmailOtpScene.REGISTER));
    }

    @Test
    @SuppressWarnings("unchecked")
    void redisFailureReturns50003() {
        when(redisTemplate.execute(
                any(RedisScript.class), anyList(), any(Object[].class)
        )).thenReturn(null);

        BizException exception = assertThrows(
                BizException.class,
                () -> store.createOrReplace(
                        "student@example.edu",
                        EmailOtpScene.REGISTER,
                        VerificationTokens.sha256Hex("123456"),
                        Duration.ofMinutes(5)
                )
        );

        assertEquals(ErrorCode.AUTH_IDEMPOTENCY_UNAVAILABLE.getCode(), exception.getCode());
    }
}
