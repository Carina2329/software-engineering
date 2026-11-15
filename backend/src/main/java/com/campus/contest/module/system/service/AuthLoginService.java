package com.campus.contest.module.system.service;

import com.campus.contest.common.BizException;
import com.campus.contest.common.ErrorCode;
import com.campus.contest.module.system.dto.AuthorizationSnapshot;
import com.campus.contest.module.system.dto.LoginRequest;
import com.campus.contest.module.system.entity.SysUserDO;
import com.campus.contest.module.system.enums.EmailOtpScene;
import com.campus.contest.module.system.enums.LoginType;
import com.campus.contest.security.LoginCaptchaVerifier;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;

import java.util.Locale;
import java.util.Objects;
import java.util.regex.Pattern;

/**
 * EP-001 统一登录入口的认证编排。
 */
@Service
@RequiredArgsConstructor
public class AuthLoginService {

    private static final int USER_ACTIVE = 1;
    private static final Pattern EMAIL_PATTERN =
            Pattern.compile("^[^\\s@]+@[^\\s@]+\\.[^\\s@]+$");
    private static final Pattern EMAIL_CODE_PATTERN = Pattern.compile("^[0-9]{6}$");
    public static final String FAKE_PASSWORD_HASH =
            "$2a$12$WGbPDHU0mYJ5ueQDbzMGUurHrpHhPSjTSa/YooZKZDdAHa7N2GLsq";

    private final ObjectProvider<LoginCaptchaVerifier> captchaVerifierProvider;
    private final ObjectProvider<EmailOtpVerifier> emailOtpVerifierProvider;
    private final AuthUserLookupService userLookupService;
    private final AuthorizationSnapshotService authorizationSnapshotService;
    private final PasswordEncoder passwordEncoder;
    private final AuthTokenService authTokenService;
    private final LoginRateLimiter rateLimiter;

    public AuthTokenService.LoginResult login(LoginRequest request, String clientIp) {
        validate(request);
        return switch (request.getLoginType()) {
            case USERNAME_PASSWORD -> loginWithPassword(request, clientIp);
            case EMAIL_OTP -> loginWithEmailOtp(request, clientIp);
        };
    }

    private AuthTokenService.LoginResult loginWithPassword(LoginRequest request, String clientIp) {
        String username = request.getUsername().trim();
        rateLimiter.checkAllowed(username, clientIp);
        try {
            LoginCaptchaVerifier captchaVerifier = captchaVerifierProvider.getIfAvailable();
            if (captchaVerifier == null) {
                throw authDependencyUnavailable("图片验证码校验能力尚未接入");
            }
            captchaVerifier.verify(request.getCaptchaId(), request.getCaptchaCode());

            String password = request.getPassword();
            SysUserDO user = userLookupService.findByUsername(username).orElse(null);
            if (user == null) {
                passwordEncoder.matches(password, FAKE_PASSWORD_HASH);
                throw invalidCredentials();
            }

            String passwordHash = user.getPasswordHash();
            boolean passwordMatches = passwordHash != null && !passwordHash.isBlank()
                    ? passwordEncoder.matches(password, passwordHash)
                    : passwordEncoder.matches(password, FAKE_PASSWORD_HASH);
            if (!passwordMatches || !Objects.equals(USER_ACTIVE, user.getStatus())) {
                throw invalidCredentials();
            }
            AuthTokenService.LoginResult result = issueFor(user);
            rateLimiter.clearAccountFailures(username, clientIp);
            return result;
        } catch (BizException e) {
            if (isInfrastructureFailure(e) || isRateLimited(e)) {
                throw e;
            }
            rateLimiter.recordFailure(username, clientIp);
            throw invalidCredentials();
        }
    }

    private AuthTokenService.LoginResult loginWithEmailOtp(LoginRequest request, String clientIp) {
        String email = normalizeEmail(request.getEmail());
        rateLimiter.checkAllowed(email, clientIp);
        try {
            EmailOtpVerifier emailOtpVerifier = emailOtpVerifierProvider.getIfAvailable();
            if (emailOtpVerifier == null) {
                throw authDependencyUnavailable("邮件 OTP 校验能力尚未接入");
            }
            emailOtpVerifier.verify(
                    request.getChallengeId(),
                    email,
                    request.getEmailCode(),
                    EmailOtpScene.LOGIN
            );

            SysUserDO user = userLookupService.findByEmail(email).orElse(null);
            if (user == null || !Objects.equals(USER_ACTIVE, user.getStatus())) {
                throw invalidCredentials();
            }
            AuthTokenService.LoginResult result = issueFor(user);
            rateLimiter.clearAccountFailures(email, clientIp);
            return result;
        } catch (BizException e) {
            if (isInfrastructureFailure(e) || isRateLimited(e)) {
                throw e;
            }
            rateLimiter.recordFailure(email, clientIp);
            throw invalidCredentials();
        }
    }

    private AuthTokenService.LoginResult issueFor(SysUserDO user) {
        AuthorizationSnapshot snapshot = authorizationSnapshotService.loadRequired(user.getId());
        return authTokenService.createSession(user, snapshot);
    }

    private void validate(LoginRequest request) {
        if (request == null || request.getLoginType() == null) {
            throw invalidParam("login_type 不能为空");
        }
        if (request.getLoginType() == LoginType.USERNAME_PASSWORD) {
            if (request.getUsername() == null
                    || request.getUsername().trim().length() < 4
                    || request.getUsername().trim().length() > 32) {
                throw invalidParam("username 长度必须为 4-32");
            }
            if (request.getPassword() == null
                    || request.getPassword().length() < 8
                    || request.getPassword().length() > 64) {
                throw invalidParam("password 长度必须为 8-64");
            }
            if (request.getCaptchaId() == null || request.getCaptchaId().isBlank()) {
                throw invalidParam("captcha_id 不能为空");
            }
            if (request.getCaptchaCode() == null || request.getCaptchaCode().isBlank()) {
                throw invalidParam("captcha_code 不能为空");
            }
            return;
        }

        if (request.getEmail() == null
                || request.getEmail().length() > 128
                || !EMAIL_PATTERN.matcher(request.getEmail().trim()).matches()) {
            throw invalidParam("email 格式不合法");
        }
        if (request.getChallengeId() == null || request.getChallengeId().isBlank()) {
            throw invalidParam("challenge_id 不能为空");
        }
        if (request.getEmailCode() == null
                || !EMAIL_CODE_PATTERN.matcher(request.getEmailCode()).matches()) {
            throw invalidParam("email_code 必须是 6 位数字");
        }
    }

    private String normalizeEmail(String email) {
        return email.trim().toLowerCase(Locale.ROOT);
    }

    private boolean isInfrastructureFailure(BizException e) {
        return Objects.equals(e.getCode(), ErrorCode.AUTH_IDEMPOTENCY_UNAVAILABLE.getCode());
    }

    private boolean isRateLimited(BizException e) {
        return Objects.equals(e.getCode(), ErrorCode.RATE_LIMITED.getCode());
    }

    private BizException invalidParam(String message) {
        return new BizException(ErrorCode.INVALID_PARAM.getCode(), message);
    }

    private BizException invalidCredentials() {
        return new BizException(ErrorCode.UNAUTHENTICATED.getCode(), "账号或凭证不正确");
    }

    private BizException authDependencyUnavailable(String message) {
        return new BizException(
                ErrorCode.AUTH_IDEMPOTENCY_UNAVAILABLE.getCode(),
                message
        );
    }
}
