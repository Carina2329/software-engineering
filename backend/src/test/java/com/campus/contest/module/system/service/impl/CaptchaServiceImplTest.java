package com.campus.contest.module.system.service.impl;

import com.campus.contest.common.BizException;
import com.campus.contest.module.system.enums.CaptchaScene;
import com.campus.contest.module.system.service.VerificationRateLimiter;
import com.campus.contest.module.system.vo.CaptchaChallengeVO;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.script.RedisScript;

import java.time.Instant;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class CaptchaServiceImplTest {

    private StringRedisTemplate redisTemplate;
    private CaptchaServiceImpl captchaService;

    @BeforeEach
    void setUp() {
        redisTemplate = mock(StringRedisTemplate.class);
        captchaService = new CaptchaServiceImpl(redisTemplate, mock(VerificationRateLimiter.class));
    }

    @Test
    @SuppressWarnings("unchecked")
    void createReturnsPngChallenge() {
        when(redisTemplate.execute(
                any(RedisScript.class), anyList(), any(Object[].class)
        )).thenReturn(1L);

        CaptchaChallengeVO challenge = captchaService.create(CaptchaScene.LOGIN, "127.0.0.1");
        assertNotNull(challenge);
        assertNotNull(challenge.captchaId());
        assertFalse(challenge.captchaId().isBlank());
        assertTrue(challenge.imageBase64().startsWith("iVBOR"));
        assertTrue(challenge.expiresAt().isAfter(Instant.now()));
    }

    @Test
    @SuppressWarnings("unchecked")
    void verifySuccessConsumesCaptcha() {
        when(redisTemplate.execute(
                any(RedisScript.class), anyList(), any(Object[].class)
        )).thenReturn(1L);

        captchaService.verify("captcha-1", "ABCD", CaptchaScene.LOGIN);
    }

    @Test
    @SuppressWarnings("unchecked")
    void verifyRejectsSceneMismatch() {
        when(redisTemplate.execute(
                any(RedisScript.class), anyList(), any(Object[].class)
        )).thenReturn(2L);

        BizException exception = assertThrows(
                BizException.class,
                () -> captchaService.verify("captcha-2", "ABCD", CaptchaScene.LOGIN)
        );
        assertEquals(10001, exception.getCode());
    }
}
