package com.campus.contest.module.system.service;

import com.campus.contest.common.BizException;
import com.campus.contest.common.ErrorCode;
import lombok.RequiredArgsConstructor;
import org.springframework.dao.DataAccessException;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;

import java.time.Duration;
import java.util.Locale;

/**
 * 验证码、邮件 OTP 和注册流程的 Redis 限流，统一返回 HTTP 429 与业务码 40001。
 */
@Service
@RequiredArgsConstructor
public class VerificationRateLimiter {

    private static final Duration CAPTCHA_WINDOW = Duration.ofMinutes(1);
    private static final int CAPTCHA_IP_LIMIT = 30;

    private static final Duration OTP_WINDOW = Duration.ofHours(1);
    private static final Duration OTP_COOLDOWN = Duration.ofSeconds(60);
    private static final int OTP_EMAIL_HOURLY_LIMIT = 5;
    private static final int OTP_IP_HOURLY_LIMIT = 20;

    private static final int REGISTER_IP_HOURLY_LIMIT = 20;

    private final StringRedisTemplate redisTemplate;

    public void checkCaptchaGeneration(String clientIp) {
        countAndCheck("auth:rate:captcha:ip:", clientIp, CAPTCHA_WINDOW, CAPTCHA_IP_LIMIT);
    }

    public void checkAndRecordOtpSend(String email, String clientIp) {
        String normalizedEmail = normalize(email);
        String cooldownKey = "auth:rate:otp:cooldown:email:"
                + VerificationTokens.sha256Hex(normalizedEmail);
        try {
            Boolean acquired = redisTemplate.opsForValue()
                    .setIfAbsent(cooldownKey, "1", OTP_COOLDOWN);
            if (!Boolean.TRUE.equals(acquired)) {
                throw rateLimited();
            }
        } catch (DataAccessException e) {
            throw unavailable();
        }
        countAndCheck("auth:rate:otp:email:", normalizedEmail, OTP_WINDOW, OTP_EMAIL_HOURLY_LIMIT);
        countAndCheck("auth:rate:otp:ip:", clientIp, OTP_WINDOW, OTP_IP_HOURLY_LIMIT);
    }

    public void checkRegister(String clientIp) {
        countAndCheck("auth:rate:register:ip:", clientIp, OTP_WINDOW, REGISTER_IP_HOURLY_LIMIT);
    }

    private void countAndCheck(String prefix, String subject, Duration window, int limit) {
        String key = prefix + VerificationTokens.sha256Hex(normalize(subject));
        try {
            Long count = redisTemplate.opsForValue().increment(key);
            if (count == null) {
                throw unavailable();
            }
            if (count == 1L) {
                redisTemplate.expire(key, window);
            }
            if (count > limit) {
                throw rateLimited();
            }
        } catch (DataAccessException e) {
            throw unavailable();
        }
    }

    private String normalize(String value) {
        return value == null || value.isBlank() ? "unknown" : value.trim().toLowerCase(Locale.ROOT);
    }

    private BizException rateLimited() {
        return new BizException(
                ErrorCode.RATE_LIMITED.getCode(),
                "请求过于频繁，请稍后再试"
        );
    }

    private BizException unavailable() {
        return new BizException(
                ErrorCode.AUTH_IDEMPOTENCY_UNAVAILABLE.getCode(),
                ErrorCode.AUTH_IDEMPOTENCY_UNAVAILABLE.getMessage()
        );
    }
}
