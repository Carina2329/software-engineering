package com.campus.contest.common.idempotency;

import com.campus.contest.common.BizException;
import com.campus.contest.common.ErrorCode;
import lombok.RequiredArgsConstructor;
import org.springframework.dao.DataAccessException;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.script.DefaultRedisScript;
import org.springframework.data.redis.core.script.RedisScript;
import org.springframework.stereotype.Component;

import java.time.Duration;
import java.util.List;
import java.util.Map;
import java.util.Optional;

@Component
@RequiredArgsConstructor
class RedisIdempotencyStore implements IdempotencyStore {

    private static final String STATUS = "status";
    private static final String FINGERPRINT = "fingerprint";
    private static final String OWNER = "owner";
    private static final String RESULT = "result";
    private static final String IN_PROGRESS = "IN_PROGRESS";
    private static final String COMPLETED = "COMPLETED";

    private static final RedisScript<Long> ACQUIRE_SCRIPT = new DefaultRedisScript<>(
            """
            local status = redis.call('HGET', KEYS[1], 'status')
            if status then
                local fingerprint = redis.call('HGET', KEYS[1], 'fingerprint')
                if fingerprint ~= ARGV[1] then
                    return -2
                end
                if status == 'COMPLETED' then
                    return 0
                end
                return 2
            end
            redis.call('HSET', KEYS[1],
                'status', 'IN_PROGRESS',
                'fingerprint', ARGV[1],
                'owner', ARGV[2])
            redis.call('PEXPIRE', KEYS[1], ARGV[3])
            return 1
            """,
            Long.class
    );

    private static final RedisScript<Long> COMPLETE_SCRIPT = new DefaultRedisScript<>(
            """
            local status = redis.call('HGET', KEYS[1], 'status')
            local owner = redis.call('HGET', KEYS[1], 'owner')
            if status ~= 'IN_PROGRESS' or owner ~= ARGV[1] then
                return 0
            end
            redis.call('HSET', KEYS[1],
                'status', 'COMPLETED',
                'result', ARGV[2])
            redis.call('HDEL', KEYS[1], 'owner')
            redis.call('PEXPIRE', KEYS[1], ARGV[3])
            return 1
            """,
            Long.class
    );

    private static final RedisScript<Long> RELEASE_SCRIPT = new DefaultRedisScript<>(
            """
            local owner = redis.call('HGET', KEYS[1], 'owner')
            if owner == ARGV[1] then
                return redis.call('DEL', KEYS[1])
            end
            return 0
            """,
            Long.class
    );

    private final StringRedisTemplate redisTemplate;

    @Override
    public AcquireResult acquire(
            String key,
            String fingerprint,
            String ownerToken,
            Duration lockTtl
    ) {
        try {
            Long result = redisTemplate.execute(
                    ACQUIRE_SCRIPT,
                    List.of(key),
                    fingerprint,
                    ownerToken,
                    String.valueOf(lockTtl.toMillis())
            );
            if (result == null) {
                throw unavailable();
            }
            return switch (result.intValue()) {
                case 1 -> new AcquireResult(AcquireState.ACQUIRED, null);
                case 0 -> new AcquireResult(
                        AcquireState.COMPLETED,
                        completedResult(key, fingerprint).orElse(null)
                );
                case 2 -> new AcquireResult(AcquireState.IN_PROGRESS, null);
                case -2 -> new AcquireResult(AcquireState.FINGERPRINT_MISMATCH, null);
                default -> throw unavailable();
            };
        } catch (DataAccessException e) {
            throw unavailable();
        }
    }

    @Override
    public Optional<String> completedResult(String key, String fingerprint) {
        try {
            Map<Object, Object> values = redisTemplate.opsForHash().entries(key);
            String status = stringValue(values.get(STATUS));
            String storedFingerprint = stringValue(values.get(FINGERPRINT));
            String resultJson = stringValue(values.get(RESULT));
            if (COMPLETED.equals(status)
                    && fingerprint.equals(storedFingerprint)
                    && resultJson != null) {
                return Optional.of(resultJson);
            }
            return Optional.empty();
        } catch (DataAccessException e) {
            throw unavailable();
        }
    }

    @Override
    public boolean complete(String key, String ownerToken, String resultJson, Duration ttl) {
        try {
            Long completed = redisTemplate.execute(
                    COMPLETE_SCRIPT,
                    List.of(key),
                    ownerToken,
                    resultJson,
                    String.valueOf(ttl.toMillis())
            );
            return completed != null && completed == 1L;
        } catch (DataAccessException e) {
            throw unavailable();
        }
    }

    @Override
    public void release(String key, String ownerToken) {
        try {
            redisTemplate.execute(RELEASE_SCRIPT, List.of(key), ownerToken);
        } catch (DataAccessException e) {
            throw unavailable();
        }
    }

    private String stringValue(Object value) {
        return value == null ? null : value.toString();
    }

    private BizException unavailable() {
        return new BizException(
                ErrorCode.AUTH_IDEMPOTENCY_UNAVAILABLE.getCode(),
                ErrorCode.AUTH_IDEMPOTENCY_UNAVAILABLE.getMessage()
        );
    }
}
