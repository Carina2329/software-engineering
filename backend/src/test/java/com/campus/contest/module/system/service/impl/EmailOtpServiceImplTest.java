package com.campus.contest.module.system.service.impl;

import com.campus.contest.common.BizException;
import com.campus.contest.module.system.enums.EmailOtpScene;
import com.campus.contest.module.system.service.AuthUserLookupService;
import com.campus.contest.module.system.service.CaptchaService;
import com.campus.contest.module.system.service.VerificationRateLimiter;
import com.campus.contest.module.system.service.VerificationTokens;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.data.redis.core.HashOperations;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.mail.javamail.JavaMailSender;

import java.util.HashMap;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class EmailOtpServiceImplTest {

    private StringRedisTemplate redisTemplate;
    private HashOperations<String, Object, Object> hashOps;
    private EmailOtpServiceImpl service;

    @BeforeEach
    @SuppressWarnings("unchecked")
    void setUp() {
        redisTemplate = mock(StringRedisTemplate.class);
        hashOps = mock(HashOperations.class);
        when(redisTemplate.opsForHash()).thenReturn(hashOps);
        service = new EmailOtpServiceImpl(
                redisTemplate,
                mock(VerificationRateLimiter.class),
                mock(AuthUserLookupService.class),
                mock(CaptchaService.class),
                mock(JavaMailSender.class)
        );
    }

    @Test
    void verifySuccessConsumesChallenge() {
        String challengeId = "challenge-1";
        String email = "student@example.edu";
        String code = "123456";
        Map<Object, Object> fields = new HashMap<>();
        fields.put("email", email);
        fields.put("scene", EmailOtpScene.REGISTER.name());
        fields.put("codeHash", VerificationTokens.sha256Hex(code));
        fields.put("attempts", "0");
        when(hashOps.entries("auth:challenge:" + challengeId)).thenReturn(fields);

        service.verify(challengeId, email, code, EmailOtpScene.REGISTER);

        verify(redisTemplate).delete("auth:challenge:" + challengeId);
    }

    @Test
    void verifyRejectsSceneIsolation() {
        String challengeId = "challenge-2";
        String email = "student@example.edu";
        Map<Object, Object> fields = new HashMap<>();
        fields.put("email", email);
        fields.put("scene", EmailOtpScene.LOGIN.name());
        fields.put("codeHash", VerificationTokens.sha256Hex("123456"));
        fields.put("attempts", "0");
        when(hashOps.entries("auth:challenge:" + challengeId)).thenReturn(fields);

        BizException exception = assertThrows(
                BizException.class,
                () -> service.verify(challengeId, email, "123456", EmailOtpScene.REGISTER)
        );
        assertEquals(10001, exception.getCode());
        verify(redisTemplate).delete("auth:challenge:" + challengeId);
    }
}
