package com.campus.contest.module.system.service;

import com.campus.contest.common.BizException;
import com.campus.contest.common.ErrorCode;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.script.RedisScript;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class VerificationRateLimiterTest {

    private StringRedisTemplate redisTemplate;
    private VerificationRateLimiter rateLimiter;

    @BeforeEach
    void setUp() {
        redisTemplate = mock(StringRedisTemplate.class);
        rateLimiter = new VerificationRateLimiter(redisTemplate);
    }

    @Test
    @SuppressWarnings("unchecked")
    void otpSendRejectedWhenLuaReturnsNotAllowed() {
        when(redisTemplate.execute(
                any(RedisScript.class), anyList(), any(Object[].class)
        )).thenReturn(0L);

        BizException exception = assertThrows(
                BizException.class,
                () -> rateLimiter.checkAndRecordOtpSend("A@Example.edu", "127.0.0.1")
        );

        assertEquals(ErrorCode.RATE_LIMITED.getCode(), exception.getCode());
    }

    @Test
    @SuppressWarnings("unchecked")
    void otpSendAllowedWhenLuaReturnsOne() {
        when(redisTemplate.execute(
                any(RedisScript.class), anyList(), any(Object[].class)
        )).thenReturn(1L);

        assertDoesNotThrow(() -> rateLimiter.checkAndRecordOtpSend("a@example.edu", "127.0.0.1"));
    }

    @Test
    @SuppressWarnings("unchecked")
    void captchaGenerationRejectedWhenLuaReturnsNotAllowed() {
        when(redisTemplate.execute(
                any(RedisScript.class), anyList(), any(Object[].class)
        )).thenReturn(0L);

        BizException exception = assertThrows(
                BizException.class,
                () -> rateLimiter.checkCaptchaGeneration("127.0.0.1")
        );

        assertEquals(ErrorCode.RATE_LIMITED.getCode(), exception.getCode());
    }

    @Test
    @SuppressWarnings("unchecked")
    void registerRejectedWhenLuaReturnsNotAllowed() {
        when(redisTemplate.execute(
                any(RedisScript.class), anyList(), any(Object[].class)
        )).thenReturn(0L);

        BizException exception = assertThrows(
                BizException.class,
                () -> rateLimiter.checkRegister("127.0.0.1")
        );

        assertEquals(ErrorCode.RATE_LIMITED.getCode(), exception.getCode());
    }
}
