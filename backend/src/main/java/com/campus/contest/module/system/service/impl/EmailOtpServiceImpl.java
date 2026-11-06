package com.campus.contest.module.system.service.impl;

import com.campus.contest.common.BizException;
import com.campus.contest.common.ErrorCode;
import com.campus.contest.module.system.dto.EmailVerificationCodeRequest;
import com.campus.contest.module.system.dto.PasswordResetRequest;
import com.campus.contest.module.system.enums.CaptchaScene;
import com.campus.contest.module.system.enums.EmailOtpScene;
import com.campus.contest.module.system.service.AuthUserLookupService;
import com.campus.contest.module.system.service.CaptchaService;
import com.campus.contest.module.system.service.EmailOtpService;
import com.campus.contest.module.system.service.VerificationRateLimiter;
import com.campus.contest.module.system.service.VerificationTokens;
import com.campus.contest.module.system.vo.EmailVerificationChallengeVO;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.dao.DataAccessException;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.mail.MailException;
import org.springframework.mail.SimpleMailMessage;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.stereotype.Service;

import java.security.SecureRandom;
import java.time.Duration;
import java.time.Instant;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.regex.Pattern;

/**
 * 邮件 OTP 实现：6 位安全随机数字、5 分钟有效、只保存哈希、一次性消费、场景隔离。
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class EmailOtpServiceImpl implements EmailOtpService {

    private static final String KEY_PREFIX = "auth:challenge:";
    private static final Duration TTL = Duration.ofMinutes(5);
    private static final int MAX_ATTEMPTS = 5;
    private static final int USER_ACTIVE = 1;
    private static final Pattern EMAIL_PATTERN = Pattern.compile("^[^\\s@]+@[^\\s@]+\\.[^\\s@]+$");
    private static final Pattern CODE_PATTERN = Pattern.compile("^[0-9]{6}$");
    private static final String MAIL_FROM = "no-reply@contest-agent-platform.local";
    private static final SecureRandom SECURE_RANDOM = new SecureRandom();

    private final StringRedisTemplate redisTemplate;
    private final VerificationRateLimiter rateLimiter;
    private final AuthUserLookupService userLookupService;
    private final CaptchaService captchaService;
    private final JavaMailSender mailSender;

    @Override
    public EmailVerificationChallengeVO send(EmailVerificationCodeRequest request, String clientIp) {
        String email = normalizeEmail(request.getEmail());
        EmailOtpScene scene = requireScene(request.getScene());
        captchaService.verify(request.getCaptchaId(), request.getCaptchaCode(), captchaSceneOf(scene));
        rateLimiter.checkAndRecordOtpSend(email, clientIp);
        return createAndSend(email, scene);
    }

    @Override
    public EmailVerificationChallengeVO requestPasswordReset(
            PasswordResetRequest request,
            String clientIp
    ) {
        String email = normalizeEmail(request.getEmail());
        captchaService.verify(
                request.getCaptchaId(),
                request.getCaptchaCode(),
                CaptchaScene.RESET_PASSWORD
        );
        rateLimiter.checkAndRecordOtpSend(email, clientIp);

        boolean accountExists = userLookupService.findByEmail(email)
                .map(user -> Objects.equals(USER_ACTIVE, user.getStatus()))
                .orElse(false);
        if (!accountExists) {
            return new EmailVerificationChallengeVO(
                    VerificationTokens.nextId(),
                    Instant.now().plus(TTL)
            );
        }
        return createAndSend(email, EmailOtpScene.RESET_PASSWORD);
    }

    @Override
    public void verify(String challengeId, String email, String emailCode, EmailOtpScene scene) {
        if (challengeId == null || challengeId.isBlank()
                || emailCode == null || !CODE_PATTERN.matcher(emailCode).matches()
                || scene == null) {
            throw invalidOtp();
        }
        String normalizedEmail = normalizeEmail(email);
        String key = KEY_PREFIX + challengeId;
        try {
            Map<Object, Object> fields = redisTemplate.opsForHash().entries(key);
            if (fields == null || fields.isEmpty()) {
                throw invalidOtp();
            }
            String boundEmail = str(fields.get("email"));
            String boundScene = str(fields.get("scene"));
            String codeHash = str(fields.get("codeHash"));
            int attempts = parseInt(str(fields.get("attempts")));
            if (!normalizedEmail.equals(boundEmail)
                    || !scene.name().equals(boundScene)
                    || attempts >= MAX_ATTEMPTS) {
                redisTemplate.delete(key);
                throw invalidOtp();
            }
            if (VerificationTokens.matches(emailCode, codeHash)) {
                redisTemplate.delete(key);
                return;
            }
            long next = redisTemplate.opsForHash().increment(key, "attempts", 1);
            if (next >= MAX_ATTEMPTS) {
                redisTemplate.delete(key);
                throw tooManyAttempts();
            }
            throw invalidOtp();
        } catch (DataAccessException e) {
            throw unavailable();
        }
    }

    private EmailVerificationChallengeVO createAndSend(String email, EmailOtpScene scene) {
        String code = randomCode();
        String challengeId = VerificationTokens.nextId();
        Instant expiresAt = Instant.now().plus(TTL);
        sendMail(email, scene, code);
        try {
            redisTemplate.opsForHash().putAll(KEY_PREFIX + challengeId, Map.of(
                    "email", email,
                    "scene", scene.name(),
                    "codeHash", VerificationTokens.sha256Hex(code),
                    "attempts", "0"
            ));
            redisTemplate.expire(KEY_PREFIX + challengeId, TTL);
        } catch (DataAccessException e) {
            throw unavailable();
        }
        return new EmailVerificationChallengeVO(challengeId, expiresAt);
    }

    private void sendMail(String email, EmailOtpScene scene, String code) {
        try {
            SimpleMailMessage message = new SimpleMailMessage();
            message.setFrom(MAIL_FROM);
            message.setTo(email);
            message.setSubject("竞赛平台邮箱验证码");
            message.setText("您的验证码是：" + code + "，5 分钟内有效，请勿泄露。用途：" + scene.name() + "。");
            mailSender.send(message);
        } catch (MailException e) {
            log.warn("邮件发送失败: {}", e.getClass().getSimpleName());
            throw unavailable();
        }
    }

    private CaptchaScene captchaSceneOf(EmailOtpScene scene) {
        return switch (scene) {
            case LOGIN -> CaptchaScene.LOGIN;
            case REGISTER -> CaptchaScene.REGISTER;
            case RESET_PASSWORD -> CaptchaScene.RESET_PASSWORD;
        };
    }

    private EmailOtpScene requireScene(EmailOtpScene scene) {
        if (scene == null) {
            throw invalidParam("scene 不能为空");
        }
        return scene;
    }

    private String randomCode() {
        return String.format("%06d", SECURE_RANDOM.nextInt(1_000_000));
    }

    private String normalizeEmail(String email) {
        if (email == null || email.length() > 128
                || !EMAIL_PATTERN.matcher(email.trim()).matches()) {
            throw invalidParam("email 格式不合法");
        }
        return email.trim().toLowerCase(Locale.ROOT);
    }

    private String str(Object value) {
        return value == null ? null : String.valueOf(value);
    }

    private int parseInt(String value) {
        if (value == null || value.isBlank()) {
            return 0;
        }
        try {
            return Integer.parseInt(value);
        } catch (NumberFormatException e) {
            return 0;
        }
    }

    private BizException invalidOtp() {
        return new BizException(
                ErrorCode.INVALID_PARAM.getCode(),
                "邮箱验证码不正确或已失效"
        );
    }

    private BizException tooManyAttempts() {
        return new BizException(
                ErrorCode.RATE_LIMITED.getCode(),
                "邮箱验证码错误次数过多，请重新获取"
        );
    }

    private BizException invalidParam(String message) {
        return new BizException(ErrorCode.INVALID_PARAM.getCode(), message);
    }

    private BizException unavailable() {
        return new BizException(
                ErrorCode.AUTH_IDEMPOTENCY_UNAVAILABLE.getCode(),
                ErrorCode.AUTH_IDEMPOTENCY_UNAVAILABLE.getMessage()
        );
    }
}
