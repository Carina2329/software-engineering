package com.campus.contest.module.system.service.impl;

import com.campus.contest.common.BizException;
import com.campus.contest.module.system.enums.CaptchaScene;
import com.campus.contest.module.system.service.VerificationRateLimiter;
import com.campus.contest.module.system.service.VerificationTokens;
import com.campus.contest.module.system.vo.CaptchaChallengeVO;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.data.redis.core.HashOperations;
import org.springframework.data.redis.core.StringRedisTemplate;

import java.time.Instant;
import java.util.HashMap;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class CaptchaServiceImplTest {

    private StringRedisTemplate redisTemplate;
    private HashOperations<String, Object, Object> hashOps;
    private CaptchaServiceImpl captchaService;

    @BeforeEach
    @SuppressWarnings("unchecked")
    void setUp() {
        redisTemplate = mock(StringRedisTemplate.class);
        hashOps = mock(HashOperations.class);
        when(redisTemplate.opsForHash()).thenReturn(hashOps);
        captchaService = new CaptchaServiceImpl(redisTemplate, mock(VerificationRateLimiter.class));
    }

    @Test
    void createReturnsPngChallenge() {
        CaptchaChallengeVO challenge = captchaService.create(CaptchaScene.LOGIN, "127.0.0.1");
        assertNotNull(challenge);
        assertNotNull(challenge.captchaId());
        assertFalse(challenge.captchaId().isBlank());
        assertTrue(challenge.imageBase64().startsWith("iVBOR"));
        assertTrue(challenge.expiresAt().isAfter(Instant.now()));
    }

    @Test
    void verifySuccessConsumesCaptcha() {
        String captchaId = "captcha-1";
        String code = "ABCD";
        Map<Object, Object> fields = new HashMap<>();
        fields.put("scene", CaptchaScene.LOGIN.name());
        fields.put("answerHash", VerificationTokens.sha256Hex(code));
        fields.put("attempts", "0");
        when(hashOps.entries("auth:captcha:" + captchaId)).thenReturn(fields);

        captchaService.verify(captchaId, code, CaptchaScene.LOGIN);

        verify(redisTemplate).delete("auth:captcha:" + captchaId);
    }

    @Test
    void verifyRejectsSceneMismatch() {
        String captchaId = "captcha-2";
        Map<Object, Object> fields = new HashMap<>();
        fields.put("scene", CaptchaScene.REGISTER.name());
        fields.put("answerHash", VerificationTokens.sha256Hex("ABCD"));
        fields.put("attempts", "0");
        when(hashOps.entries("auth:captcha:" + captchaId)).thenReturn(fields);

        BizException exception = assertThrows(
                BizException.class,
                () -> captchaService.verify(captchaId, "ABCD", CaptchaScene.LOGIN)
        );
        assertEquals(10001, exception.getCode());
        verify(redisTemplate).delete("auth:captcha:" + captchaId);
    }
}
