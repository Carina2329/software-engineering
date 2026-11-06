package com.campus.contest.module.system.service.impl;

import com.campus.contest.common.BizException;
import com.campus.contest.module.system.dto.EmailVerificationCodeRequest;
import com.campus.contest.module.system.entity.SysUserDO;
import com.campus.contest.module.system.enums.EmailOtpScene;
import com.campus.contest.module.system.service.AuthUserLookupService;
import com.campus.contest.module.system.service.CaptchaService;
import com.campus.contest.module.system.service.VerificationRateLimiter;
import com.campus.contest.module.system.vo.EmailVerificationChallengeVO;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.data.redis.core.HashOperations;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.script.RedisScript;
import org.springframework.mail.SimpleMailMessage;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.test.util.ReflectionTestUtils;

import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class EmailOtpServiceImplTest {

    private StringRedisTemplate redisTemplate;
    private HashOperations<String, Object, Object> hashOps;
    private AuthUserLookupService userLookupService;
    private JavaMailSender mailSender;
    private EmailOtpServiceImpl service;

    @BeforeEach
    @SuppressWarnings("unchecked")
    void setUp() {
        redisTemplate = mock(StringRedisTemplate.class);
        hashOps = mock(HashOperations.class);
        when(redisTemplate.opsForHash()).thenReturn(hashOps);
        userLookupService = mock(AuthUserLookupService.class);
        mailSender = mock(JavaMailSender.class);
        service = new EmailOtpServiceImpl(
                redisTemplate,
                mock(VerificationRateLimiter.class),
                userLookupService,
                mock(CaptchaService.class),
                mailSender
        );
    }

    @Test
    @SuppressWarnings("unchecked")
    void verifySuccessDoesNotThrow() {
        when(redisTemplate.execute(
                any(RedisScript.class), anyList(), any(Object[].class)
        )).thenReturn(1L);

        service.verify("challenge-1", "student@example.edu", "123456", EmailOtpScene.REGISTER);
    }

    @Test
    @SuppressWarnings("unchecked")
    void verifyRejectsSceneIsolation() {
        when(redisTemplate.execute(
                any(RedisScript.class), anyList(), any(Object[].class)
        )).thenReturn(2L);

        BizException exception = assertThrows(
                BizException.class,
                () -> service.verify("challenge-2", "student@example.edu", "123456", EmailOtpScene.REGISTER)
        );
        assertEquals(10001, exception.getCode());
    }

    @Test
    @SuppressWarnings("unchecked")
    void verifyExhaustedThrowsRateLimited() {
        when(redisTemplate.execute(
                any(RedisScript.class), anyList(), any(Object[].class)
        )).thenReturn(4L);

        BizException exception = assertThrows(
                BizException.class,
                () -> service.verify("challenge-3", "student@example.edu", "123456", EmailOtpScene.REGISTER)
        );
        assertEquals(40001, exception.getCode());
    }

    @Test
    void sendSkipsMailForUnknownLoginEmail() {
        when(userLookupService.findByEmail("nobody@example.edu")).thenReturn(Optional.empty());

        EmailVerificationChallengeVO challenge = service.send(request(EmailOtpScene.LOGIN), "127.0.0.1");

        assertNotNull(challenge.challengeId());
        assertFalse(challenge.challengeId().isBlank());
        verify(mailSender, never()).send(any(SimpleMailMessage.class));
    }

    @Test
    void sendSkipsMailForResetPasswordScene() {
        when(userLookupService.findByEmail("student@example.edu")).thenReturn(Optional.of(activeUser()));

        EmailVerificationChallengeVO challenge = service.send(request(EmailOtpScene.RESET_PASSWORD), "127.0.0.1");

        assertNotNull(challenge.challengeId());
        verify(mailSender, never()).send(any(SimpleMailMessage.class));
    }

    @Test
    void sendSkipsMailWhenRegisterEmailTaken() {
        when(userLookupService.findByEmail("student@example.edu")).thenReturn(Optional.of(activeUser()));

        EmailVerificationChallengeVO challenge = service.send(request(EmailOtpScene.REGISTER), "127.0.0.1");

        assertNotNull(challenge.challengeId());
        verify(mailSender, never()).send(any(SimpleMailMessage.class));
    }

    @Test
    void sendSendsMailForActiveLoginEmail() {
        when(userLookupService.findByEmail("student@example.edu")).thenReturn(Optional.of(activeUser()));
        ReflectionTestUtils.setField(service, "mailFrom", "no-reply@test.local");

        EmailVerificationChallengeVO challenge = service.send(request(EmailOtpScene.LOGIN), "127.0.0.1");

        assertNotNull(challenge.challengeId());
        assertFalse(challenge.challengeId().isBlank());
        verify(mailSender).send(any(SimpleMailMessage.class));
    }

    private EmailVerificationCodeRequest request(EmailOtpScene scene) {
        EmailVerificationCodeRequest request = new EmailVerificationCodeRequest();
        request.setEmail("student@example.edu");
        request.setScene(scene);
        request.setCaptchaId("captcha-1");
        request.setCaptchaCode("ABCD");
        return request;
    }

    private SysUserDO activeUser() {
        SysUserDO user = new SysUserDO();
        user.setId(1L);
        user.setUsername("student");
        user.setEmail("student@example.edu");
        user.setStatus(1);
        return user;
    }
}
