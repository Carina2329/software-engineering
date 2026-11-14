package com.campus.contest.module.system.service;

import com.campus.contest.common.BizException;
import com.campus.contest.common.ErrorCode;
import lombok.RequiredArgsConstructor;
import org.springframework.dao.DataAccessException;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.script.DefaultRedisScript;
import org.springframework.data.redis.core.script.RedisScript;
import org.springframework.stereotype.Service;

import java.time.Duration;
import java.util.List;
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

    /**
     * 原子计数：首次写入时设置 TTL，超过阈值返回 0。
     */
    private static final RedisScript<Long> COUNT_SCRIPT = new DefaultRedisScript<>(
            """
            local count = redis.call('INCR', KEYS[1])
            if count == 1 then
              redis.call('PEXPIRE', KEYS[1], ARGV[2])
            end
            if count > tonumber(ARGV[1]) then
              return 0
            end
            return 1
            """,
            Long.class
    );

    /**
     * OTP 发送原子限流：60 秒冷却、邮箱小时上限、IP 小时上限。
     */
    private static final RedisScript<Long> OTP_SEND_SCRIPT = new DefaultRedisScript<>(
            """
            if redis.call('EXISTS', KEYS[1]) == 1 then
              return 0
            end
            local emailCount = tonumber(redis.call('GET', KEYS[2]) or '0')
            if emailCount >= tonumber(ARGV[2]) then
              return 0
            end
            local ipCount = tonumber(redis.call('GET', KEYS[3]) or '0')
            if ipCount >= tonumber(ARGV[3]) then
              return 0
            end
            redis.call('SET', KEYS[1], '1', 'PX', ARGV[1])
            local newEmail = redis.call('INCR', KEYS[2])
            if newEmail == 1 then
              redis.call('PEXPIRE', KEYS[2], ARGV[4])
            end
            local newIp = redis.call('INCR', KEYS[3])
            if newIp == 1 then
              redis.call('PEXPIRE', KEYS[3], ARGV[4])
            end
            return 1
            """,
            Long.class
    );

    private final StringRedisTemplate redisTemplate;

    public void checkCaptchaGeneration(String clientIp) {
        checkCount(
                "auth:rate:captcha:ip:" + VerificationTokens.sha256Hex(normalize(clientIp)),
                CAPTCHA_WINDOW,
                CAPTCHA_IP_LIMIT
        );
    }

    public void checkAndRecordOtpSend(String email, String clientIp) {
        String normalizedEmail = normalize(email);
        try {
            Long result = redisTemplate.execute(
                    OTP_SEND_SCRIPT,
                    List.of(
                            "auth:rate:otp:cooldown:email:"
                                    + VerificationTokens.sha256Hex(normalizedEmail),
                            "auth:rate:otp:email:"
                                    + VerificationTokens.sha256Hex(normalizedEmail),
                            "auth:rate:otp:ip:"
                                    + VerificationTokens.sha256Hex(normalize(clientIp))
                    ),
                    String.valueOf(OTP_COOLDOWN.toMillis()),
                    String.valueOf(OTP_EMAIL_HOURLY_LIMIT),
                    String.valueOf(OTP_IP_HOURLY_LIMIT),
                    String.valueOf(OTP_WINDOW.toMillis())
            );
            if (result == null) {
                throw unavailable();
            }
            if (result != 1L) {
                throw rateLimited();
            }
        } catch (DataAccessException e) {
            throw unavailable();
        }
    }

    public void checkRegister(String clientIp) {
        checkCount(
                "auth:rate:register:ip:" + VerificationTokens.sha256Hex(normalize(clientIp)),
                OTP_WINDOW,
                REGISTER_IP_HOURLY_LIMIT
        );
    }

    private void checkCount(String key, Duration window, int limit) {
        try {
            Long result = redisTemplate.execute(
                    COUNT_SCRIPT,
                    List.of(key),
                    String.valueOf(limit),
                    String.valueOf(window.toMillis())
            );
            if (result == null) {
                throw unavailable();
            }
            if (result != 1L) {
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
