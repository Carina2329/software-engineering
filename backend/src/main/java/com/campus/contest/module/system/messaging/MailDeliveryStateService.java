package com.campus.contest.module.system.messaging;

import com.campus.contest.common.BizException;
import com.campus.contest.common.ErrorCode;
import lombok.RequiredArgsConstructor;
import org.springframework.dao.DataAccessException;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;

import java.time.Duration;
import java.time.Instant;

/**
 * C 负责的邮件投递状态和发送租约。
 *
 * <p>Redis 只负责正常情况下的重复消费防护和短期租约；RabbitMQ 仍按至少一次投递语义工作。</p>
 */
@Service
@RequiredArgsConstructor
public class MailDeliveryStateService {

    private static final String SENT_PREFIX = "auth:otp:delivery:sent:";
    private static final String LEASE_PREFIX = "auth:otp:delivery:lease:";
    private static final Duration LEASE_TTL = Duration.ofSeconds(45);

    private final StringRedisTemplate redisTemplate;

    public enum AcquireResult {
        ACQUIRED,
        ALREADY_SENT,
        BUSY
    }

    /**
     * 尝试获取当前 challenge 的短租约，防止两个消费者同时发送。
     */
    public AcquireResult acquireLease(String challengeId) {
        if (challengeId == null || challengeId.isBlank()) {
            throw invalidMessage();
        }
        try {
            if (Boolean.TRUE.equals(redisTemplate.hasKey(sentKey(challengeId)))) {
                return AcquireResult.ALREADY_SENT;
            }
            Boolean acquired = redisTemplate.opsForValue()
                    .setIfAbsent(leaseKey(challengeId), "1", LEASE_TTL);
            return Boolean.TRUE.equals(acquired) ? AcquireResult.ACQUIRED : AcquireResult.BUSY;
        } catch (DataAccessException e) {
            throw unavailable();
        }
    }

    public void releaseLease(String challengeId) {
        if (challengeId == null || challengeId.isBlank()) {
            return;
        }
        try {
            redisTemplate.delete(leaseKey(challengeId));
        } catch (DataAccessException e) {
            throw unavailable();
        }
    }

    /**
     * 标记正常发送完成，保留到 challenge 过期，避免重复消费再次发信。
     */
    public void markSent(String challengeId, Instant expiresAt) {
        if (challengeId == null || challengeId.isBlank()) {
            throw invalidMessage();
        }
        try {
            Duration ttl = ttlUntil(expiresAt);
            redisTemplate.opsForValue().set(sentKey(challengeId), "SENT", ttl);
            redisTemplate.delete(leaseKey(challengeId));
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
