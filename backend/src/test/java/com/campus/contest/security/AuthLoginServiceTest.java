package com.campus.contest.security;

import com.campus.contest.common.BizException;
import com.campus.contest.common.ErrorCode;
import com.campus.contest.module.system.dto.AuthorizationSnapshot;
import com.campus.contest.module.system.dto.LoginRequest;
import com.campus.contest.module.system.enums.LoginType;
import com.campus.contest.module.system.entity.SysUserDO;
import com.campus.contest.module.system.service.AuthLoginService;
import com.campus.contest.module.system.service.AuthTokenService;
import com.campus.contest.module.system.service.AuthUserLookupService;
import com.campus.contest.module.system.service.AuthorizationSnapshotService;
import com.campus.contest.module.system.service.EmailOtpVerifier;
import com.campus.contest.module.system.service.LoginRateLimiter;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;

import java.util.Optional;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class AuthLoginServiceTest {

    private ObjectProvider<LoginCaptchaVerifier> captchaProvider;
    private ObjectProvider<EmailOtpVerifier> otpProvider;
    private AuthUserLookupService userLookupService;
    private AuthorizationSnapshotService snapshotService;
    private AuthTokenService tokenService;
    private LoginRateLimiter rateLimiter;
    private PasswordEncoder passwordEncoder;
    private AuthLoginService loginService;

    @BeforeEach
    @SuppressWarnings("unchecked")
    void setUp() {
        captchaProvider = mock(ObjectProvider.class);
        otpProvider = mock(ObjectProvider.class);
        userLookupService = mock(AuthUserLookupService.class);
        snapshotService = mock(AuthorizationSnapshotService.class);
        tokenService = mock(AuthTokenService.class);
        rateLimiter = mock(LoginRateLimiter.class);
        passwordEncoder = new BCryptPasswordEncoder(4);
        loginService = new AuthLoginService(
                captchaProvider,
                otpProvider,
                userLookupService,
                snapshotService,
                passwordEncoder,
                tokenService,
                rateLimiter
        );
    }

    @Test
    void missingCaptchaDependencyFailsClosedWith503Code() {
        when(captchaProvider.getIfAvailable()).thenReturn(null);

        BizException exception = assertThrows(BizException.class, () -> loginService.login(usernameRequest(), "127.0.0.1"));

        assertEquals(ErrorCode.AUTH_IDEMPOTENCY_UNAVAILABLE.getCode(), exception.getCode());
        verify(userLookupService, never()).findByUsername(anyString());
    }

    @Test
    void wrongPasswordReturnsSameGenericCredentialError() {
        LoginCaptchaVerifier captcha = mock(LoginCaptchaVerifier.class);
        when(captchaProvider.getIfAvailable()).thenReturn(captcha);
        SysUserDO user = user();
        user.setPasswordHash(passwordEncoder.encode("correct-password"));
        when(userLookupService.findByUsername("student_a")).thenReturn(Optional.of(user));

        BizException exception = assertThrows(BizException.class, () -> loginService.login(usernameRequest(), "127.0.0.1"));

        assertEquals(ErrorCode.UNAUTHENTICATED.getCode(), exception.getCode());
        assertEquals("账号或凭证不正确", exception.getMessage());
        verify(tokenService, never()).createSession(any(), any());
    }

    @Test
    void missingUserStillPerformsPasswordComparisonThenReturnsGenericError() {
        LoginCaptchaVerifier captcha = mock(LoginCaptchaVerifier.class);
        when(captchaProvider.getIfAvailable()).thenReturn(captcha);
        when(userLookupService.findByUsername("student_a")).thenReturn(Optional.empty());

        BizException exception = assertThrows(BizException.class, () -> loginService.login(usernameRequest(), "127.0.0.1"));

        assertEquals(ErrorCode.UNAUTHENTICATED.getCode(), exception.getCode());
        verify(tokenService, never()).createSession(any(), any());
    }

    @Test
    void validPasswordCreatesSessionAfterCaptchaAndSnapshot() {
        LoginCaptchaVerifier captcha = mock(LoginCaptchaVerifier.class);
        when(captchaProvider.getIfAvailable()).thenReturn(captcha);
        SysUserDO user = user();
        user.setPasswordHash(passwordEncoder.encode("StudentA#2026"));
        when(userLookupService.findByUsername("student_a")).thenReturn(Optional.of(user));
        AuthorizationSnapshot snapshot = new AuthorizationSnapshot(
                7L, "student_a", 1, Set.of("STUDENT"), Set.of("competition:read"), Set.of("SELF")
        );
        when(snapshotService.loadRequired(7L)).thenReturn(snapshot);
        AuthTokenService.LoginResult result = mock(AuthTokenService.LoginResult.class);
        when(tokenService.createSession(user, snapshot)).thenReturn(result);

        assertEquals(result, loginService.login(usernameRequest(), "127.0.0.1"));
    }

    private LoginRequest usernameRequest() {
        LoginRequest request = new LoginRequest();
        request.setLoginType(LoginType.USERNAME_PASSWORD);
        request.setUsername("student_a");
        request.setPassword("StudentA#2026");
        request.setCaptchaId("captcha-1");
        request.setCaptchaCode("AB12");
        return request;
    }

    private SysUserDO user() {
        SysUserDO user = new SysUserDO();
        user.setId(7L);
        user.setUsername("student_a");
        user.setRealName("学生 A");
        user.setEmail("student_a@example.edu");
        user.setStatus(1);
        return user;
    }

    @Test
    void fakePasswordHashUsesCost12() {
        assertTrue(AuthLoginService.FAKE_PASSWORD_HASH.startsWith("$2a$12$"));
    }
}
