package com.campus.contest.module.system.service;

import com.campus.contest.common.BizException;
import com.campus.contest.common.ErrorCode;
import lombok.RequiredArgsConstructor;
import org.springframework.dao.DataAccessException;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.script.DefaultRedisScript;
import org.springframework.data.redis.core.script.RedisScript;
import org.springframework.stereotype.Service;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.time.Duration;
import java.util.List;
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

    /**
     * 原子预占尝试额度：锁定、账号上限、IP 上限全部通过后，才同时增加两个计数，
     * 并记录本次 IP 供成功登录释放。1=允许，2=限流。
     */
    private static final RedisScript<Long> CHECK_SCRIPT = new DefaultRedisScript<>(
            """
            if redis.call('EXISTS', KEYS[2]) == 1 then
              return 2
            end
            local accountCount = tonumber(redis.call('GET', KEYS[1]) or '0')
            if accountCount >= tonumber(ARGV[1]) then
              redis.call('SET', KEYS[2], '1', 'PX', ARGV[3])
              return 2
            end
            local ipCount = tonumber(redis.call('GET', KEYS[3]) or '0')
            if ipCount >= tonumber(ARGV[2]) then
              return 2
            end
            local newAccount = redis.call('INCR', KEYS[1])
            if newAccount == 1 then
              redis.call('PEXPIRE', KEYS[1], ARGV[4])
            end
            local newIp = redis.call('INCR', KEYS[3])
            if newIp == 1 then
              redis.call('PEXPIRE', KEYS[3], ARGV[4])
            end
            redis.call('SET', KEYS[4], KEYS[3], 'PX', ARGV[4])
            return 1
            """,
            Long.class
    );

    /**
     * 失败时只根据已预占的账号计数上锁，不再重复增加计数。
     */
    private static final RedisScript<Long> FAILURE_SCRIPT = new DefaultRedisScript<>(
            """
            local accountCount = tonumber(redis.call('GET', KEYS[1]) or '0')
            if accountCount >= tonumber(ARGV[1]) then
              redis.call('SET', KEYS[2], '1', 'PX', ARGV[2])
            end
            return 1
            """,
            Long.class
    );

    /**
     * 成功登录后释放本次预占：清空账号计数和锁定，并递减最近一次预占的 IP 计数。
     */
    private static final RedisScript<Long> CLEAR_SCRIPT = new DefaultRedisScript<>(
            """
            local ipKey = redis.call('GET', KEYS[3])
            local accountCount = tonumber(redis.call('GET', KEYS[1]) or '0')
            if accountCount > 0 then
              redis.call('DECR', KEYS[1])
              if accountCount <= 1 then
                redis.call('DEL', KEYS[1])
              end
            end
            if ipKey then
              local ipCount = tonumber(redis.call('GET', ipKey) or '0')
              if ipCount > 0 then
                redis.call('DECR', ipKey)
                if ipCount <= 1 then
                  redis.call('DEL', ipKey)
                end
              end
              redis.call('DEL', KEYS[3])
            end
            redis.call('DEL', KEYS[2])
            return 1
            """,
            Long.class
    );

    private final StringRedisTemplate redisTemplate;

    public void checkAllowed(String account, String clientIp) {
        try {
            Long result = redisTemplate.execute(
                    CHECK_SCRIPT,
                    List.of(accountKey(account), lockKey(account), ipKey(clientIp), lastIpKey(account)),
                    String.valueOf(ACCOUNT_LIMIT),
                    String.valueOf(IP_LIMIT),
                    String.valueOf(LOCK.toMillis()),
                    String.valueOf(WINDOW.toMillis())
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

    public void recordFailure(String account, String clientIp) {
        try {
            Long result = redisTemplate.execute(
                    FAILURE_SCRIPT,
                    List.of(accountKey(account), lockKey(account)),
                    String.valueOf(ACCOUNT_LIMIT),
                    String.valueOf(LOCK.toMillis())
            );
            if (result == null) {
                throw unavailable();
            }
        } catch (DataAccessException e) {
            throw unavailable();
        }
    }

    public void clearAccountFailures(String account) {
        try {
            Long result = redisTemplate.execute(
                    CLEAR_SCRIPT,
                    List.of(accountKey(account), lockKey(account), lastIpKey(account))
            );
            if (result == null) {
                throw unavailable();
            }
        } catch (DataAccessException e) {
            throw unavailable();
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

    private String lastIpKey(String account) {
        return PREFIX + "last-ip:" + digest(normalize(account));
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
