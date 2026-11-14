package com.campus.contest.module.system.service;

import com.campus.contest.common.BizException;
import com.campus.contest.common.ErrorCode;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;
import org.springframework.data.redis.connection.lettuce.LettuceConnectionFactory;
import org.springframework.data.redis.core.StringRedisTemplate;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

@EnabledIfEnvironmentVariable(named = "RUN_REDIS_TESTS", matches = "true")
class LoginRateLimiterRedisTest {

    @Test
    void concurrentLoginFailuresCannotExceedAccountLimit() throws Exception {
        LettuceConnectionFactory factory = connectionFactory(6379);
        try {
            StringRedisTemplate redis = new StringRedisTemplate(factory);
            LoginRateLimiter limiter = new LoginRateLimiter(redis);
            String account = "concurrent-login-" + UUID.randomUUID();
            String ip = "203.0.113." + (Math.abs(UUID.randomUUID().getLeastSignificantBits() % 200) + 1);
            int threads = 10;
            ExecutorService executor = Executors.newFixedThreadPool(threads);
            CountDownLatch ready = new CountDownLatch(threads);
            CountDownLatch start = new CountDownLatch(1);
            AtomicInteger allowed = new AtomicInteger();
            List<Future<?>> futures = new ArrayList<>();
            for (int i = 0; i < threads; i++) {
                futures.add(executor.submit(() -> {
                    ready.countDown();
                    await(start);
                    try {
                        limiter.checkAllowed(account, ip);
                        allowed.incrementAndGet();
                        limiter.recordFailure(account, ip);
                    } catch (BizException expected) {
                        // 超过 5 次的请求应在 checkAllowed 阶段被拒绝
                    }
                }));
            }
            ready.await(5, TimeUnit.SECONDS);
            start.countDown();
            for (Future<?> future : futures) {
                future.get(5, TimeUnit.SECONDS);
            }
            executor.shutdownNow();

            assertEquals(5, allowed.get());
            BizException locked = assertThrows(
                    BizException.class,
                    () -> limiter.checkAllowed(account, ip)
            );
            assertEquals(ErrorCode.RATE_LIMITED.getCode(), locked.getCode());
            assertTrue(redis.hasKey(
                    "auth:rate:login:account:" + VerificationTokens.sha256Hex(account)
            ) || redis.hasKey(
                    "auth:rate:login:lock:" + VerificationTokens.sha256Hex(account)
            ));
        } finally {
            factory.destroy();
        }
    }

    @Test
    void releaseWithGivenIpOnlyDecrementsThatIp() {
        LettuceConnectionFactory factory = connectionFactory(6379);
        try {
            StringRedisTemplate redis = new StringRedisTemplate(factory);
            LoginRateLimiter limiter = new LoginRateLimiter(redis);
            String account = "dual-ip-" + UUID.randomUUID();
            String ip1 = "198.51.100."
                    + (Math.abs(UUID.randomUUID().getLeastSignificantBits() % 200) + 1);
            String ip2 = "203.0.113."
                    + (Math.abs(UUID.randomUUID().getLeastSignificantBits() % 200) + 1);
            limiter.checkAllowed(account, ip1);
            limiter.checkAllowed(account, ip2);

            limiter.clearAccountFailures(account, ip1);

            String accountKey = "auth:rate:login:account:"
                    + VerificationTokens.sha256Hex(account);
            String ip1Key = "auth:rate:login:ip:" + VerificationTokens.sha256Hex(ip1);
            String ip2Key = "auth:rate:login:ip:" + VerificationTokens.sha256Hex(ip2);
            assertFalse(redis.hasKey(accountKey));
            assertFalse(redis.hasKey(ip1Key));
            assertEquals("1", redis.opsForValue().get(ip2Key));
        } finally {
            factory.destroy();
        }
    }

    @Test
    void successClearsAccountFailuresButKeepsIpHistory() {
        LettuceConnectionFactory factory = connectionFactory(6379);
        try {
            StringRedisTemplate redis = new StringRedisTemplate(factory);
            LoginRateLimiter limiter = new LoginRateLimiter(redis);
            String account = "history-" + UUID.randomUUID();
            String ip = "192.0.2."
                    + (Math.abs(UUID.randomUUID().getLeastSignificantBits() % 200) + 1);
            String accountKey = "auth:rate:login:account:"
                    + VerificationTokens.sha256Hex(account);
            String lockKey = "auth:rate:login:lock:"
                    + VerificationTokens.sha256Hex(account);
            String ipKey = "auth:rate:login:ip:" + VerificationTokens.sha256Hex(ip);

            for (int i = 0; i < 4; i++) {
                limiter.checkAllowed(account, ip);
                limiter.recordFailure(account, ip);
            }
            limiter.checkAllowed(account, ip);
            limiter.clearAccountFailures(account, ip);

            assertFalse(redis.hasKey(accountKey));
            assertFalse(redis.hasKey(lockKey));
            assertEquals("4", redis.opsForValue().get(ipKey));
        } finally {
            factory.destroy();
        }
    }

    private static void await(CountDownLatch latch) {
        try {
            if (!latch.await(5, TimeUnit.SECONDS)) {
                throw new IllegalStateException("latch timeout");
            }
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new IllegalStateException(e);
        }
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
}
