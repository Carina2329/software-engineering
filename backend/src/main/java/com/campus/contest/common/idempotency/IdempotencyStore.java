package com.campus.contest.common.idempotency;

import java.time.Duration;
import java.util.Optional;

interface IdempotencyStore {

    AcquireResult acquire(
            String key,
            String fingerprint,
            String ownerToken,
            Duration lockTtl
    );

    Optional<String> completedResult(String key, String fingerprint);

    boolean complete(String key, String ownerToken, String resultJson, Duration ttl);

    void release(String key, String ownerToken);

    enum AcquireState {
        ACQUIRED,
        COMPLETED,
        IN_PROGRESS,
        FINGERPRINT_MISMATCH
    }

    record AcquireResult(AcquireState state, String resultJson) {
    }
}
