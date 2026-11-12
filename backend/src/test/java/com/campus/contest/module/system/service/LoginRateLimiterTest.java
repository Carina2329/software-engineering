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
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class LoginRateLimiterTest {

    private StringRedisTemplate redisTemplate;
    private LoginRateLimiter limiter;

    @BeforeEach
    void setUp() {
        redisTemplate = mock(StringRedisTemplate.class);
        limiter = new LoginRateLimiter(redisTemplate);
    }

    @Test
    @SuppressWarnings("unchecked")
    void checkAllowedAllowsWhenLuaReturnsOne() {
        when(redisTemplate.execute(
                any(RedisScript.class), anyList(), any(Object[].class)
        )).thenReturn(1L);

        assertDoesNotThrow(() -> limiter.checkAllowed("student_a", "127.0.0.1"));
    }

    @Test
    @SuppressWarnings("unchecked")
    void checkAllowedRejectsWhenLuaReturnsTwo() {
        when(redisTemplate.execute(
                any(RedisScript.class), anyList(), any(Object[].class)
        )).thenReturn(2L);

        BizException exception = assertThrows(
                BizException.class,
                () -> limiter.checkAllowed("student_a", "127.0.0.1")
        );

        assertEquals(ErrorCode.RATE_LIMITED.getCode(), exception.getCode());
    }

    @Test
    @SuppressWarnings("unchecked")
    void recordFailureFailsClosedWhenRedisReturnsNull() {
        when(redisTemplate.execute(
                any(RedisScript.class), anyList(), any(Object[].class)
        )).thenReturn(null);

        BizException exception = assertThrows(
                BizException.class,
                () -> limiter.recordFailure("student_a", "127.0.0.1")
        );

        assertEquals(ErrorCode.AUTH_IDEMPOTENCY_UNAVAILABLE.getCode(), exception.getCode());
    }

    @Test
    void clearFailuresDeletesAccountAndLockKeys() {
        limiter.clearAccountFailures("student_a");

        verify(redisTemplate).delete(anyList());
    }
}
