package com.campus.contest.common.idempotency;

import com.campus.contest.common.BizException;
import com.campus.contest.common.ErrorCode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;

import java.time.Duration;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class RedisIdempotencyServiceTest {

    private static final IdempotencyScope USER_A =
            new IdempotencyScope(IdempotencyScope.PrincipalType.USER, "101");
    private static final IdempotencyScope USER_B =
            new IdempotencyScope(IdempotencyScope.PrincipalType.USER, "102");

    @Test
    void sameUserAndKeyWithSameRequestExecutesOnceAndReturnsCachedResult() {
        FakeStore store = new FakeStore();
        RedisIdempotencyService service = service(store);
        AtomicInteger executions = new AtomicInteger();

        String first = service.execute(
                USER_A, "role.create", "key-1", Map.of("name", "teacher"),
                Duration.ofHours(1), String.class,
                () -> "created-" + executions.incrementAndGet()
        );
        String second = service.execute(
                USER_A, "role.create", "key-1", Map.of("name", "teacher"),
                Duration.ofHours(1), String.class,
                () -> "created-" + executions.incrementAndGet()
        );

        assertThat(first).isEqualTo("created-1");
        assertThat(second).isEqualTo("created-1");
        assertThat(executions).hasValue(1);
    }

    @Test
    void sameKeyWithDifferentRequestReturnsExistingDuplicateCode() {
        FakeStore store = new FakeStore();
        RedisIdempotencyService service = service(store);

        service.execute(
                USER_A, "role.create", "key-2", Map.of("name", "teacher"),
                Duration.ofHours(1), String.class, () -> "created"
        );

        assertThatThrownBy(() -> service.execute(
                USER_A, "role.create", "key-2", Map.of("name", "reviewer"),
                Duration.ofHours(1), String.class, () -> "wrong"
        )).isInstanceOfSatisfying(BizException.class,
                error -> assertThat(error.getCode()).isEqualTo(ErrorCode.IDEMPOTENCY_CONFLICT.getCode()));
    }

    @Test
    void differentUsersCanUseSameIdempotencyKey() {
        FakeStore store = new FakeStore();
        RedisIdempotencyService service = service(store);
        AtomicInteger executions = new AtomicInteger();

        String first = service.execute(
                USER_A, "role.create", "shared-key", Map.of("name", "same"),
                Duration.ofHours(1), String.class,
                () -> "a-" + executions.incrementAndGet()
        );
        String second = service.execute(
                USER_B, "role.create", "shared-key", Map.of("name", "same"),
                Duration.ofHours(1), String.class,
                () -> "b-" + executions.incrementAndGet()
        );

        assertThat(first).isEqualTo("a-1");
        assertThat(second).isEqualTo("b-2");
        assertThat(executions).hasValue(2);
    }

    @Test
    void concurrentSameRequestWaitsAndDoesNotExecuteTwice() throws Exception {
        FakeStore store = new FakeStore();
        RedisIdempotencyService service = service(store);
        AtomicInteger executions = new AtomicInteger();
        CountDownLatch actionStarted = new CountDownLatch(1);
        CountDownLatch allowFinish = new CountDownLatch(1);
        ExecutorService executor = Executors.newFixedThreadPool(2);

        try {
            Future<String> first = executor.submit(() -> service.execute(
                    USER_A, "role.create", "concurrent-key", Map.of("name", "teacher"),
                    Duration.ofHours(1), String.class,
                    () -> {
                        executions.incrementAndGet();
                        actionStarted.countDown();
                        await(allowFinish);
                        return "created";
                    }
            ));
            assertThat(actionStarted.await(2, TimeUnit.SECONDS)).isTrue();

            Future<String> second = executor.submit(() -> service.execute(
                    USER_A, "role.create", "concurrent-key", Map.of("name", "teacher"),
                    Duration.ofHours(1), String.class,
                    () -> {
                        executions.incrementAndGet();
                        return "duplicate";
                    }
            ));

            Thread.sleep(150);
            allowFinish.countDown();

            assertThat(first.get(3, TimeUnit.SECONDS)).isEqualTo("created");
            assertThat(second.get(3, TimeUnit.SECONDS)).isEqualTo("created");
            assertThat(executions).hasValue(1);
        } finally {
            executor.shutdownNow();
        }
    }

    @Test
    void failedActionDoesNotLeaveSuccessfulCache() {
        FakeStore store = new FakeStore();
        RedisIdempotencyService service = service(store);
        AtomicInteger executions = new AtomicInteger();

        assertThatThrownBy(() -> service.execute(
                USER_A, "role.create", "failure-key", Map.of("name", "teacher"),
                Duration.ofHours(1), String.class,
                () -> {
                    executions.incrementAndGet();
                    throw new IllegalStateException("failed");
                }
        )).isInstanceOf(IllegalStateException.class);

        String retried = service.execute(
                USER_A, "role.create", "failure-key", Map.of("name", "teacher"),
                Duration.ofHours(1), String.class,
                () -> "success-" + executions.incrementAndGet()
        );

        assertThat(retried).isEqualTo("success-2");
    }

    @Test
    void redisFailureStopsOriginalBusinessAction() {
        FakeStore store = new FakeStore();
        store.unavailable = true;
        RedisIdempotencyService service = service(store);
        AtomicInteger executions = new AtomicInteger();

        assertThatThrownBy(() -> service.execute(
                USER_A, "role.create", "redis-down", Map.of("name", "teacher"),
                Duration.ofHours(1), String.class,
                () -> "executed-" + executions.incrementAndGet()
        )).isInstanceOfSatisfying(BizException.class,
                error -> assertThat(error.getCode())
                        .isEqualTo(ErrorCode.AUTH_IDEMPOTENCY_UNAVAILABLE.getCode()));

        assertThat(executions).hasValue(0);
    }

    private RedisIdempotencyService service(IdempotencyStore store) {
        return new RedisIdempotencyService(store, new ObjectMapper());
    }

    private static void await(CountDownLatch latch) {
        try {
            if (!latch.await(2, TimeUnit.SECONDS)) {
                throw new IllegalStateException("latch timeout");
            }
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new IllegalStateException(e);
        }
    }

    private static final class FakeStore implements IdempotencyStore {

        private record Entry(
                String fingerprint,
                String owner,
                AcquireState state,
                String result
        ) {
        }

        private final ConcurrentHashMap<String, Entry> entries = new ConcurrentHashMap<>();
        private volatile boolean unavailable;

        @Override
        public synchronized AcquireResult acquire(
                String key,
                String fingerprint,
                String ownerToken,
                Duration lockTtl
        ) {
            failIfUnavailable();
            Entry existing = entries.get(key);
            if (existing == null) {
                entries.put(key, new Entry(
                        fingerprint, ownerToken, AcquireState.ACQUIRED, null
                ));
                return new AcquireResult(AcquireState.ACQUIRED, null);
            }
            if (!existing.fingerprint().equals(fingerprint)) {
                return new AcquireResult(AcquireState.FINGERPRINT_MISMATCH, null);
            }
            if (existing.state() == AcquireState.COMPLETED) {
                return new AcquireResult(AcquireState.COMPLETED, existing.result());
            }
            return new AcquireResult(AcquireState.IN_PROGRESS, null);
        }

        @Override
        public synchronized Optional<String> completedResult(String key, String fingerprint) {
            failIfUnavailable();
            Entry entry = entries.get(key);
            if (entry == null || !entry.fingerprint().equals(fingerprint)) {
                return Optional.empty();
            }
            return entry.state() == AcquireState.COMPLETED
                    ? Optional.ofNullable(entry.result())
                    : Optional.empty();
        }

        @Override
        public synchronized boolean complete(
                String key,
                String ownerToken,
                String resultJson,
                Duration ttl
        ) {
            failIfUnavailable();
            Entry entry = entries.get(key);
            if (entry == null
                    || entry.state() != AcquireState.ACQUIRED
                    || !ownerToken.equals(entry.owner())) {
                return false;
            }
            entries.put(key, new Entry(
                    entry.fingerprint(), null, AcquireState.COMPLETED, resultJson
            ));
            return true;
        }

        @Override
        public synchronized void release(String key, String ownerToken) {
            failIfUnavailable();
            Entry entry = entries.get(key);
            if (entry != null && ownerToken.equals(entry.owner())) {
                entries.remove(key);
            }
        }

        private void failIfUnavailable() {
            if (unavailable) {
                throw new BizException(
                        ErrorCode.AUTH_IDEMPOTENCY_UNAVAILABLE.getCode(),
                        ErrorCode.AUTH_IDEMPOTENCY_UNAVAILABLE.getMessage()
                );
            }
        }
    }
}
