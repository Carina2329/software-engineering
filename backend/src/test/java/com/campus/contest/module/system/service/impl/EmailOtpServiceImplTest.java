package com.campus.contest.module.system.service.impl;

import com.campus.contest.common.BizException;
import com.campus.contest.module.system.dto.EmailVerificationCodeRequest;
import com.campus.contest.module.system.entity.SysUserDO;
import com.campus.contest.module.system.enums.EmailOtpScene;
import com.campus.contest.module.system.service.AuthUserLookupService;
import com.campus.contest.module.system.service.CaptchaService;
import com.campus.contest.module.system.service.VerificationRateLimiter;
import com.campus.contest.module.system.service.VerificationTokens;
import com.campus.contest.module.system.vo.EmailVerificationChallengeVO;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.mail.MailSendException;
import org.springframework.mail.SimpleMailMessage;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.test.util.ReflectionTestUtils;

import java.time.Instant;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class EmailOtpServiceImplTest {

    private RedisEmailOtpChallengeStore challengeStore;
    private AuthUserLookupService userLookupService;
    private JavaMailSender mailSender;
    private EmailOtpServiceImpl service;

    @BeforeEach
    void setUp() {
        challengeStore = mock(RedisEmailOtpChallengeStore.class);
        userLookupService = mock(AuthUserLookupService.class);
        mailSender = mock(JavaMailSender.class);
        service = new EmailOtpServiceImpl(
                challengeStore,
                mock(VerificationRateLimiter.class),
                userLookupService,
                mock(CaptchaService.class),
                mailSender
        );
    }

    @Test
    void verifyDelegatesToChallengeStore() {
        service.verify("challenge-1", "Student@Example.edu", "123456", EmailOtpScene.REGISTER);

        verify(challengeStore).verifyAndConsume(
                eq("challenge-1"),
                eq("student@example.edu"),
                eq(VerificationTokens.sha256Hex("123456")),
                eq(EmailOtpScene.REGISTER)
        );
    }

    @Test
    void verifyRejectsSceneIsolation() {
        doThrow(new BizException(10001, "邮箱验证码不正确或已失效"))
                .when(challengeStore)
                .verifyAndConsume(any(), any(), any(), any());

        BizException exception = assertThrows(
                BizException.class,
                () -> service.verify("challenge-2", "student@example.edu", "123456", EmailOtpScene.REGISTER)
        );
        assertEquals(10001, exception.getCode());
    }

    @Test
    void verifyExhaustedThrowsRateLimited() {
        doThrow(new BizException(40001, "邮箱验证码错误次数过多，请重新获取"))
                .when(challengeStore)
                .verifyAndConsume(any(), any(), any(), any());

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
        when(challengeStore.createOrReplace(any(), any(), any(), any()))
                .thenReturn(new RedisEmailOtpChallengeStore.CreatedChallenge(
                        "challenge-1",
                        Instant.now().plusSeconds(300)
                ));
        ReflectionTestUtils.setField(service, "mailFrom", "no-reply@test.local");

        EmailVerificationChallengeVO challenge = service.send(request(EmailOtpScene.LOGIN), "127.0.0.1");

        assertEquals("challenge-1", challenge.challengeId());
        verify(mailSender).send(any(SimpleMailMessage.class));
    }

    @Test
    void mailFailureRollsBackChallengeOnce() {
        when(userLookupService.findByEmail("student@example.edu")).thenReturn(Optional.of(activeUser()));
        when(challengeStore.createOrReplace(any(), any(), any(), any()))
                .thenReturn(new RedisEmailOtpChallengeStore.CreatedChallenge(
                        "challenge-2",
                        Instant.now().plusSeconds(300)
                ));
        ReflectionTestUtils.setField(service, "mailFrom", "no-reply@test.local");
        doThrow(new MailSendException("down"))
                .when(mailSender)
                .send(any(SimpleMailMessage.class));

        BizException exception = assertThrows(
                BizException.class,
                () -> service.send(request(EmailOtpScene.LOGIN), "127.0.0.1")
        );

        assertEquals(50003, exception.getCode());
        verify(challengeStore).rollbackFailedDispatch(
                "challenge-2",
                "student@example.edu",
                EmailOtpScene.LOGIN
        );
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
