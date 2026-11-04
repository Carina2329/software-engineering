package com.campus.contest.module.system.service;

import com.campus.contest.common.BizException;
import com.campus.contest.common.ErrorCode;
import lombok.RequiredArgsConstructor;
import org.springframework.dao.DataAccessException;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.time.Duration;
import java.util.Locale;

/**
 * 登录失败限流：账号 5 次/5 分钟并锁定 15 分钟，IP 10 次/5 分钟。
 */
@Service
@RequiredArgsConstructor
public class LoginRateLimiter {

    private static final Duration WINDOW = Duration.ofMinutes(5);
    private static final Duration LOCK = Duration.ofMinutes(15);
    private static final int ACCOUNT_LIMIT = 5;
    private static final int IP_LIMIT = 10;
    private static final String PREFIX = "auth:rate:login:";

    private final StringRedisTemplate redisTemplate;

    public void checkAllowed(String account, String clientIp) {
        try {
            String accountKey = accountKey(account);
            String lockKey = lockKey(account);
            if (Boolean.TRUE.equals(redisTemplate.hasKey(lockKey))
                    || count(accountKey) >= ACCOUNT_LIMIT) {
                redisTemplate.opsForValue().set(lockKey, "1", LOCK);
                throw rateLimited();
            }
            if (count(ipKey(clientIp)) >= IP_LIMIT) {
                throw rateLimited();
            }
        } catch (DataAccessException e) {
            throw unavailable();
        }
    }

    public void recordFailure(String account, String clientIp) {
        try {
            String accountKey = accountKey(account);
            Long accountCount = redisTemplate.opsForValue().increment(accountKey);
            if (accountCount == null) {
                throw unavailable();
            }
            redisTemplate.expire(accountKey, WINDOW);
            if (accountCount >= ACCOUNT_LIMIT) {
                redisTemplate.opsForValue().set(lockKey(account), "1", LOCK);
            }

            String ipKey = ipKey(clientIp);
            Long ipCount = redisTemplate.opsForValue().increment(ipKey);
            if (ipCount == null) {
                throw unavailable();
            }
            redisTemplate.expire(ipKey, WINDOW);
        } catch (DataAccessException e) {
            throw unavailable();
        }
    }

    public void clearAccountFailures(String account) {
        try {
            redisTemplate.delete(accountKey(account));
            redisTemplate.delete(lockKey(account));
        } catch (DataAccessException e) {
            throw unavailable();
        }
    }

    private long count(String key) {
        String value = redisTemplate.opsForValue().get(key);
        if (value == null || value.isBlank()) {
            return 0;
        }
        try {
            return Long.parseLong(value);
        } catch (NumberFormatException e) {
            return 0;
        }
    }

    private String accountKey(String account) {
        return PREFIX + "account:" + digest(normalize(account));
    }

    private String lockKey(String account) {
        return PREFIX + "lock:" + digest(normalize(account));
    }

    private String ipKey(String clientIp) {
        return PREFIX + "ip:" + digest(normalize(clientIp));
    }

    private String normalize(String value) {
        return value == null || value.isBlank() ? "unknown" : value.trim().toLowerCase(Locale.ROOT);
    }

    private String digest(String value) {
        try {
            byte[] bytes = MessageDigest.getInstance("SHA-256")
                    .digest(value.getBytes(StandardCharsets.UTF_8));
            StringBuilder result = new StringBuilder(bytes.length * 2);
            for (byte item : bytes) {
                result.append(String.format("%02x", item));
            }
            return result.toString();
        } catch (Exception e) {
            throw new IllegalStateException("无法计算限流键摘要", e);
        }
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
