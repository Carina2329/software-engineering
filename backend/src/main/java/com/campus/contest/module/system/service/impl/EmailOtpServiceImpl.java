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
import org.springframework.beans.factory.annotation.Value;
import org.springframework.dao.DataAccessException;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.script.DefaultRedisScript;
import org.springframework.data.redis.core.script.RedisScript;
import org.springframework.mail.MailException;
import org.springframework.mail.SimpleMailMessage;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.stereotype.Service;

import java.security.SecureRandom;
import java.time.Duration;
import java.time.Instant;
import java.util.List;
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
    private static final SecureRandom SECURE_RANDOM = new SecureRandom();

    /**
     * 原子校验：1=成功，0=不存在，2=邮箱或场景不匹配，3=已超次数，4=错误次数达上限，5=错误未达上限。
     */
    private static final RedisScript<Long> VERIFY_SCRIPT = new DefaultRedisScript<>(
            """
            local stored = redis.call('HGETALL', KEYS[1])
            if #stored == 0 then
              return 0
            end
            local fields = {}
            for i = 1, #stored, 2 do
              fields[stored[i]] = stored[i + 1]
            end
            if fields['email'] ~= ARGV[1] or fields['scene'] ~= ARGV[2] then
              redis.call('DEL', KEYS[1])
              return 2
            end
            local attempts = tonumber(fields['attempts'] or '0')
            if attempts >= tonumber(ARGV[4]) then
              redis.call('DEL', KEYS[1])
              return 3
            end
            if fields['codeHash'] == ARGV[3] then
              redis.call('DEL', KEYS[1])
              return 1
            end
            attempts = attempts + 1
            if attempts >= tonumber(ARGV[4]) then
              redis.call('DEL', KEYS[1])
              return 4
            end
            redis.call('HSET', KEYS[1], 'attempts', tostring(attempts))
            return 5
            """,
            Long.class
    );

    private final StringRedisTemplate redisTemplate;
    private final VerificationRateLimiter rateLimiter;
    private final AuthUserLookupService userLookupService;
    private final CaptchaService captchaService;
    private final JavaMailSender mailSender;

    @Value("${app.mail.from:}")
    private String mailFrom;

    @Override
    public EmailVerificationChallengeVO send(EmailVerificationCodeRequest request, String clientIp) {
        String email = normalizeEmail(request.getEmail());
        EmailOtpScene scene = requireScene(request.getScene());
        captchaService.verify(request.getCaptchaId(), request.getCaptchaCode(), captchaSceneOf(scene));
        rateLimiter.checkAndRecordOtpSend(email, clientIp);
        if (!shouldActuallySend(email, scene)) {
            return syntheticChallenge();
        }
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
            return syntheticChallenge();
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
        try {
            Long result = redisTemplate.execute(
                    VERIFY_SCRIPT,
                    List.of(KEY_PREFIX + challengeId),
                    normalizedEmail,
                    scene.name(),
                    VerificationTokens.sha256Hex(emailCode),
                    String.valueOf(MAX_ATTEMPTS)
            );
            if (result == null) {
                throw unavailable();
            }
            if (result == 1L) {
                return;
            }
            if (result == 4L) {
                throw tooManyAttempts();
            }
            throw invalidOtp();
        } catch (DataAccessException e) {
            throw unavailable();
        }
    }

    private boolean shouldActuallySend(String email, EmailOtpScene scene) {
        return switch (scene) {
            case REGISTER -> userLookupService.findByEmail(email).isEmpty();
            case LOGIN -> userLookupService.findByEmail(email)
                    .map(user -> Objects.equals(USER_ACTIVE, user.getStatus()))
                    .orElse(false);
            case RESET_PASSWORD -> false;
        };
    }

    private EmailVerificationChallengeVO syntheticChallenge() {
        return new EmailVerificationChallengeVO(
                VerificationTokens.nextId(),
                Instant.now().plus(TTL)
        );
    }

    private EmailVerificationChallengeVO createAndSend(String email, EmailOtpScene scene) {
        String code = randomCode();
        String challengeId = VerificationTokens.nextId();
        Instant expiresAt = Instant.now().plus(TTL);
        String key = KEY_PREFIX + challengeId;
        try {
            redisTemplate.opsForHash().putAll(key, Map.of(
                    "email", email,
                    "scene", scene.name(),
                    "codeHash", VerificationTokens.sha256Hex(code),
                    "attempts", "0"
            ));
            redisTemplate.expire(key, TTL);
        } catch (DataAccessException e) {
            throw unavailable();
        }
        try {
            sendMail(email, scene, code);
        } catch (RuntimeException e) {
            try {
                redisTemplate.delete(key);
            } catch (DataAccessException ignored) {
                log.warn("邮件发送失败后清理挑战失败: {}", ignored.getClass().getSimpleName());
            }
            throw e;
        }
        return new EmailVerificationChallengeVO(challengeId, expiresAt);
    }

    private void sendMail(String email, EmailOtpScene scene, String code) {
        if (mailFrom == null || mailFrom.isBlank()) {
            throw new BizException(
                    ErrorCode.AUTH_IDEMPOTENCY_UNAVAILABLE.getCode(),
                    "邮件发件人(app.mail.from)未配置"
            );
        }
        try {
            SimpleMailMessage message = new SimpleMailMessage();
            message.setFrom(mailFrom);
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
