package com.campus.contest.module.system.service;

import com.campus.contest.common.BizException;
import com.campus.contest.common.ErrorCode;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.ValueOperations;

import java.time.Duration;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class VerificationRateLimiterTest {

    private StringRedisTemplate redisTemplate;
    private ValueOperations<String, String> valueOps;
    private VerificationRateLimiter rateLimiter;

    @BeforeEach
    @SuppressWarnings("unchecked")
    void setUp() {
        redisTemplate = mock(StringRedisTemplate.class);
        valueOps = mock(ValueOperations.class);
        when(redisTemplate.opsForValue()).thenReturn(valueOps);
        rateLimiter = new VerificationRateLimiter(redisTemplate);
    }

    @Test
    void otpSendCooldownRejectsSecondRequest() {
        when(valueOps.setIfAbsent(anyString(), eq("1"), any(Duration.class))).thenReturn(false);

        BizException exception = assertThrows(
                BizException.class,
                () -> rateLimiter.checkAndRecordOtpSend("a@example.edu", "127.0.0.1")
        );

        assertEquals(ErrorCode.RATE_LIMITED.getCode(), exception.getCode());
    }

    @Test
    void otpSendRejectsWhenEmailHourlyLimitExceeded() {
        when(valueOps.setIfAbsent(anyString(), eq("1"), any(Duration.class))).thenReturn(true);
        when(valueOps.increment(anyString())).thenReturn(6L);

        BizException exception = assertThrows(
                BizException.class,
                () -> rateLimiter.checkAndRecordOtpSend("a@example.edu", "127.0.0.1")
        );

        assertEquals(ErrorCode.RATE_LIMITED.getCode(), exception.getCode());
    }

    @Test
    void captchaGenerationRejectsWhenIpLimitExceeded() {
        when(valueOps.increment(anyString())).thenReturn(31L);

        BizException exception = assertThrows(
                BizException.class,
                () -> rateLimiter.checkCaptchaGeneration("127.0.0.1")
        );

        assertEquals(ErrorCode.RATE_LIMITED.getCode(), exception.getCode());
    }
}
