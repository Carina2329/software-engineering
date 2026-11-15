package com.campus.contest.common.idempotency;

import com.campus.contest.common.BizException;
import com.campus.contest.common.ErrorCode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;
import org.springframework.data.redis.connection.lettuce.LettuceConnectionFactory;
import org.springframework.data.redis.core.StringRedisTemplate;

import java.time.Duration;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

@EnabledIfEnvironmentVariable(named = "RUN_REDIS_TESTS", matches = "true")
class RedisIdempotencyStoreIntegrationTest {

    private static final IdempotencyScope USER_A =
            new IdempotencyScope(IdempotencyScope.PrincipalType.USER, "101");
    private static final IdempotencyScope USER_B =
            new IdempotencyScope(IdempotencyScope.PrincipalType.USER, "102");

    @Test
    void productionRedisStoreHandlesConcurrencyIsolationAndReplay() throws Exception {
        LettuceConnectionFactory factory = connectionFactory(6379);
        try {
            RedisIdempotencyService service = service(factory);
            String key = UUID.randomUUID().toString();
            AtomicInteger executions = new AtomicInteger();
            CountDownLatch started = new CountDownLatch(1);
            CountDownLatch finish = new CountDownLatch(1);
            ExecutorService executor = Executors.newFixedThreadPool(2);

            try {
                Future<String> first = executor.submit(() -> service.execute(
                        USER_A, "integration.create", key, Map.of("value", "same"),
                        Duration.ofSeconds(5), String.class,
                        () -> {
                            executions.incrementAndGet();
                            started.countDown();
                            await(finish);
                            return "done";
                        }
                ));
                assertThat(started.await(2, TimeUnit.SECONDS)).isTrue();

                Future<String> second = executor.submit(() -> service.execute(
                        USER_A, "integration.create", key, Map.of("value", "same"),
                        Duration.ofSeconds(5), String.class,
                        () -> {
                            executions.incrementAndGet();
                            return "duplicate";
                        }
                ));

                Thread.sleep(150);
                finish.countDown();

                assertThat(first.get(3, TimeUnit.SECONDS)).isEqualTo("done");
                assertThat(second.get(3, TimeUnit.SECONDS)).isEqualTo("done");
                assertThat(executions).hasValue(1);
            } finally {
                executor.shutdownNow();
            }

            String cached = service.execute(
                    USER_A, "integration.create", key, Map.of("value", "same"),
                    Duration.ofSeconds(5), String.class,
                    () -> "not-expected"
            );
            assertThat(cached).isEqualTo("done");

            assertThatThrownBy(() -> service.execute(
                    USER_A, "integration.create", key, Map.of("value", "different"),
                    Duration.ofSeconds(5), String.class,
                    () -> "not-expected"
            )).isInstanceOfSatisfying(BizException.class,
                    error -> assertThat(error.getCode())
                            .isEqualTo(ErrorCode.IDEMPOTENCY_CONFLICT.getCode()));

            String otherUser = service.execute(
                    USER_B, "integration.create", key, Map.of("value", "same"),
                    Duration.ofSeconds(5), String.class,
                    () -> "user-b"
            );
            assertThat(otherUser).isEqualTo("user-b");
        } finally {
            factory.destroy();
        }
    }

    @Test
    void productionRedisStoreFailsClosedWithoutCallingAction() {
        LettuceConnectionFactory factory = connectionFactory(6390);
        try {
            RedisIdempotencyService service = service(factory);
            AtomicInteger executions = new AtomicInteger();

            assertThatThrownBy(() -> service.execute(
                    USER_A,
                    "integration.create",
                    UUID.randomUUID().toString(),
                    Map.of("value", "redis-down"),
                    Duration.ofSeconds(5),
                    String.class,
                    () -> "executed-" + executions.incrementAndGet()
            )).isInstanceOfSatisfying(BizException.class,
                    error -> assertThat(error.getCode())
                            .isEqualTo(ErrorCode.AUTH_IDEMPOTENCY_UNAVAILABLE.getCode()));

            assertThat(executions).hasValue(0);
        } finally {
            factory.destroy();
        }
    }

    private RedisIdempotencyService service(LettuceConnectionFactory factory) {
        return new RedisIdempotencyService(
                new RedisIdempotencyStore(new StringRedisTemplate(factory)),
                new ObjectMapper()
        );
    }

    private LettuceConnectionFactory connectionFactory(int port) {
        LettuceConnectionFactory factory = new LettuceConnectionFactory("127.0.0.1", port);
        String password = System.getenv("REDIS_PASSWORD");
        if (password != null && !password.isBlank()) {
            factory.setPassword(password);
        }
        factory.afterPropertiesSet();
        return factory;
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
}
