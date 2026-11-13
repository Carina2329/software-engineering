package com.campus.contest.module.system.messaging;

import com.campus.contest.common.BizException;
import com.campus.contest.common.ErrorCode;
import lombok.RequiredArgsConstructor;
import org.springframework.dao.DataAccessException;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.script.DefaultRedisScript;
import org.springframework.data.redis.core.script.RedisScript;
import org.springframework.stereotype.Service;

import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

/**
 * C 负责的邮件投递状态和发送租约。
 *
 * <p>sent 检查与租约申请在同一 Lua 中完成；每次租约都有唯一 owner，
 * 释放和标记发送成功都使用 compare-and-delete/compare-and-set，避免
 * 并发重复发送和误删其他消费者的租约。</p>
 */
@Service
@RequiredArgsConstructor
public class MailDeliveryStateService {

    private static final String SENT_PREFIX = "auth:otp:delivery:sent:";
    private static final String LEASE_PREFIX = "auth:otp:delivery:lease:";
    private static final Duration LEASE_TTL = Duration.ofSeconds(45);

    private static final RedisScript<Long> ACQUIRE_SCRIPT = new DefaultRedisScript<>(
            """
            if redis.call('EXISTS', KEYS[1]) == 1 then
              return -1
            end
            local acquired = redis.call('SET', KEYS[2], ARGV[1], 'NX', 'PX', ARGV[2])
            if acquired then
              return 1
            end
            return 0
            """,
            Long.class
    );

    private static final RedisScript<Long> RELEASE_SCRIPT = new DefaultRedisScript<>(
            """
            local owner = redis.call('GET', KEYS[1])
            if owner == ARGV[1] then
              return redis.call('DEL', KEYS[1])
            end
            return 0
            """,
            Long.class
    );

    private static final RedisScript<Long> MARK_SENT_SCRIPT = new DefaultRedisScript<>(
            """
            local owner = redis.call('GET', KEYS[1])
            if owner ~= ARGV[1] then
              return 0
            end
            redis.call('SET', KEYS[2], 'SENT', 'PX', ARGV[2])
            redis.call('DEL', KEYS[1])
            return 1
            """,
            Long.class
    );

    private final StringRedisTemplate redisTemplate;

    public enum AcquireResult {
        ACQUIRED,
        ALREADY_SENT,
        BUSY
    }

    public record LeaseAcquireResult(AcquireResult result, String ownerToken) {
    }

    /**
     * 原子检查 SENT 并申请带唯一 owner 的租约。
     */
    public LeaseAcquireResult acquireLease(String challengeId) {
        if (challengeId == null || challengeId.isBlank()) {
            throw invalidMessage();
        }
        String ownerToken = UUID.randomUUID().toString();
        try {
            Long result = redisTemplate.execute(
                    ACQUIRE_SCRIPT,
                    List.of(sentKey(challengeId), leaseKey(challengeId)),
                    ownerToken,
                    String.valueOf(LEASE_TTL.toMillis())
            );
            if (result == null) {
                throw unavailable();
            }
            if (result == -1L) {
                return new LeaseAcquireResult(AcquireResult.ALREADY_SENT, null);
            }
            if (result == 1L) {
                return new LeaseAcquireResult(AcquireResult.ACQUIRED, ownerToken);
            }
            return new LeaseAcquireResult(AcquireResult.BUSY, null);
        } catch (DataAccessException e) {
            throw unavailable();
        }
    }

    /**
     * compare-and-delete：只有当前租约 owner 才能释放。
     */
    public boolean releaseLease(String challengeId, String ownerToken) {
        if (challengeId == null || challengeId.isBlank()
                || ownerToken == null || ownerToken.isBlank()) {
            return false;
        }
        try {
            Long result = redisTemplate.execute(
                    RELEASE_SCRIPT,
                    List.of(leaseKey(challengeId)),
                    ownerToken
            );
            return result != null && result == 1L;
        } catch (DataAccessException e) {
            throw unavailable();
        }
    }

    /**
     * compare-and-set：只有当前租约 owner 才能标记 SENT。
     */
    public void markSent(String challengeId, String ownerToken, Instant expiresAt) {
        if (challengeId == null || challengeId.isBlank()
                || ownerToken == null || ownerToken.isBlank()) {
            throw invalidMessage();
        }
        try {
            Long result = redisTemplate.execute(
                    MARK_SENT_SCRIPT,
                    List.of(leaseKey(challengeId), sentKey(challengeId)),
                    ownerToken,
                    String.valueOf(ttlUntil(expiresAt).toMillis())
            );
            if (result == null || result != 1L) {
                throw unavailable();
            }
        } catch (DataAccessException e) {
            throw unavailable();
        }
    }

    public boolean isSent(String challengeId) {
        if (challengeId == null || challengeId.isBlank()) {
            return false;
        }
        try {
            return Boolean.TRUE.equals(redisTemplate.hasKey(sentKey(challengeId)));
        } catch (DataAccessException e) {
            throw unavailable();
        }
    }

    private Duration ttlUntil(Instant expiresAt) {
        if (expiresAt == null) {
            return Duration.ofMinutes(5);
        }
        Duration ttl = Duration.between(Instant.now(), expiresAt);
        return ttl.isZero() || ttl.isNegative() ? Duration.ofSeconds(1) : ttl;
    }

    private String sentKey(String challengeId) {
        return SENT_PREFIX + challengeId;
    }

    private String leaseKey(String challengeId) {
        return LEASE_PREFIX + challengeId;
    }

    private BizException unavailable() {
        return new BizException(
                ErrorCode.AUTH_IDEMPOTENCY_UNAVAILABLE.getCode(),
                ErrorCode.AUTH_IDEMPOTENCY_UNAVAILABLE.getMessage()
        );
    }

    private BizException invalidMessage() {
        return new BizException(ErrorCode.INVALID_PARAM.getCode(), "邮件 OTP 消息缺少 challengeId");
    }
}
