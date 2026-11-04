package com.campus.contest.common.idempotency;

import com.campus.contest.common.BizException;
import com.campus.contest.common.ErrorCode;
import com.campus.contest.common.idempotency.IdempotencyStore.AcquireResult;
import com.campus.contest.common.idempotency.IdempotencyStore.AcquireState;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.SecureRandom;
import java.time.Duration;
import java.util.Locale;
import java.util.function.Supplier;

@Service
@RequiredArgsConstructor
public class RedisIdempotencyService implements IdempotencyService {

    private static final Duration DEFAULT_TTL = Duration.ofHours(24);
    private static final Duration LOCK_TTL = Duration.ofSeconds(30);
    private static final long WAIT_TIMEOUT_MILLIS = 2_000L;
    private static final long POLL_INTERVAL_MILLIS = 100L;
    private static final SecureRandom SECURE_RANDOM = new SecureRandom();

    private final IdempotencyStore store;
    private final ObjectMapper objectMapper;

    @Override
    public <T> T execute(
            IdempotencyScope scope,
            String operation,
            String idempotencyKey,
            Object request,
            Duration ttl,
            Class<T> resultType,
            Supplier<T> action
    ) {
        validate(scope, operation, idempotencyKey, resultType, action);
        Duration effectiveTtl = ttl == null ? DEFAULT_TTL : ttl;
        if (effectiveTtl.isZero() || effectiveTtl.isNegative()) {
            throw new BizException(ErrorCode.INVALID_PARAM.getCode(), "幂等有效期必须大于 0");
        }

        String key = buildKey(scope, operation, idempotencyKey);
        String fingerprint = fingerprint(request);
        String ownerToken = ownerToken();
        AcquireResult acquired = store.acquire(key, fingerprint, ownerToken, LOCK_TTL);

        return switch (acquired.state()) {
            case FINGERPRINT_MISMATCH -> throw conflict();
            case COMPLETED -> deserialize(acquired.resultJson(), resultType);
            case IN_PROGRESS -> waitForCompleted(key, fingerprint, resultType);
            case ACQUIRED -> executeAsOwner(key, ownerToken, effectiveTtl, resultType, action);
        };
    }

    private <T> T executeAsOwner(
            String key,
            String ownerToken,
            Duration ttl,
            Class<T> resultType,
            Supplier<T> action
    ) {
        T result;
        try {
            result = action.get();
        } catch (RuntimeException e) {
            releaseSafely(key, ownerToken, e);
            throw e;
        }

        String resultJson;
        try {
            resultJson = objectMapper.writeValueAsString(result);
        } catch (com.fasterxml.jackson.core.JsonProcessingException e) {
            releaseSafely(key, ownerToken, e);
            throw new BizException(ErrorCode.INTERNAL_ERROR.getCode(), "幂等结果无法序列化", null);
        }

        if (!store.complete(key, ownerToken, resultJson, ttl)) {
            throw conflict();
        }
        return deserialize(resultJson, resultType);
    }

    private <T> T waitForCompleted(String key, String fingerprint, Class<T> resultType) {
        long deadline = System.currentTimeMillis() + WAIT_TIMEOUT_MILLIS;
        while (System.currentTimeMillis() < deadline) {
            try {
                Thread.sleep(POLL_INTERVAL_MILLIS);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                throw conflict();
            }
            var completed = store.completedResult(key, fingerprint);
            if (completed.isPresent()) {
                return deserialize(completed.get(), resultType);
            }
        }
        throw conflict();
    }

    private <T> T deserialize(String json, Class<T> resultType) {
        if (json == null) {
            throw conflict();
        }
        try {
            return objectMapper.readValue(json, resultType);
        } catch (com.fasterxml.jackson.core.JsonProcessingException e) {
            throw new BizException(
                    ErrorCode.AUTH_IDEMPOTENCY_UNAVAILABLE.getCode(),
                    ErrorCode.AUTH_IDEMPOTENCY_UNAVAILABLE.getMessage()
            );
        }
    }

    private void releaseSafely(String key, String ownerToken, Throwable original) {
        try {
            store.release(key, ownerToken);
        } catch (RuntimeException releaseError) {
            original.addSuppressed(releaseError);
        }
    }

    private String buildKey(IdempotencyScope scope, String operation, String idempotencyKey) {
        String principalType = scope.principalType().name().toLowerCase(Locale.ROOT);
        return "auth:idempotency:"
                + principalType + ":"
                + sha256(scope.principalId()) + ":"
                + sha256(operation) + ":"
                + sha256(idempotencyKey);
    }

    private String fingerprint(Object request) {
        try {
            String json = objectMapper.writer()
                    .with(SerializationFeature.ORDER_MAP_ENTRIES_BY_KEYS)
                    .writeValueAsString(request);
            return sha256(json);
        } catch (com.fasterxml.jackson.core.JsonProcessingException e) {
            throw new BizException(ErrorCode.INVALID_PARAM.getCode(), "幂等请求内容无法解析");
        }
    }

    private String ownerToken() {
        byte[] bytes = new byte[16];
        SECURE_RANDOM.nextBytes(bytes);
        StringBuilder result = new StringBuilder(bytes.length * 2);
        for (byte item : bytes) {
            result.append(String.format("%02x", item));
        }
        return result.toString();
    }

    private String sha256(String value) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            byte[] bytes = digest.digest(value.getBytes(StandardCharsets.UTF_8));
            StringBuilder result = new StringBuilder(bytes.length * 2);
            for (byte item : bytes) {
                result.append(String.format("%02x", item));
            }
            return result.toString();
        } catch (Exception e) {
            throw new IllegalStateException("无法计算幂等摘要", e);
        }
    }

    private void validate(
            IdempotencyScope scope,
            String operation,
            String idempotencyKey,
            Class<?> resultType,
            Supplier<?> action
    ) {
        if (scope == null
                || scope.principalType() == null
                || scope.principalId() == null
                || scope.principalId().isBlank()
                || operation == null
                || operation.isBlank()
                || idempotencyKey == null
                || idempotencyKey.isBlank()
                || idempotencyKey.length() > 64
                || resultType == null
                || action == null) {
            throw new BizException(ErrorCode.INVALID_PARAM.getCode(), "幂等参数不合法");
        }
    }

    private BizException conflict() {
        return new BizException(
                ErrorCode.IDEMPOTENCY_CONFLICT.getCode(),
                ErrorCode.IDEMPOTENCY_CONFLICT.getMessage()
        );
    }
}
