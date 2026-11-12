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
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

@EnabledIfEnvironmentVariable(named = "RUN_REDIS_TESTS", matches = "true")
class VerificationRateLimiterRedisTest {

    @Test
    void otpCooldownAllowsExactlyOneConcurrentSend() throws Exception {
        LettuceConnectionFactory factory = connectionFactory(6379);
        try {
            VerificationRateLimiter limiter = new VerificationRateLimiter(new StringRedisTemplate(factory));
            String email = "concurrent-" + UUID.randomUUID() + "@example.edu";
            int threads = 8;
            ExecutorService executor = Executors.newFixedThreadPool(threads);
            CountDownLatch ready = new CountDownLatch(threads);
            CountDownLatch start = new CountDownLatch(1);
            AtomicInteger successes = new AtomicInteger();
            List<Future<?>> futures = new ArrayList<>();
            for (int i = 0; i < threads; i++) {
                futures.add(executor.submit(() -> {
                    ready.countDown();
                    await(start);
                    try {
                        limiter.checkAndRecordOtpSend(email, "127.0.0.1");
                        successes.incrementAndGet();
                    } catch (BizException expected) {
                        // 只有一个请求应成功
                    }
                }));
            }
            ready.await(5, TimeUnit.SECONDS);
            start.countDown();
            for (Future<?> future : futures) {
                future.get(5, TimeUnit.SECONDS);
            }
            executor.shutdownNow();
            assertEquals(1, successes.get());
        } finally {
            factory.destroy();
        }
    }

    @Test
    void otpEmailHourlyLimitAndTtls() {
        LettuceConnectionFactory factory = connectionFactory(6379);
        try {
            StringRedisTemplate redis = new StringRedisTemplate(factory);
            VerificationRateLimiter limiter = new VerificationRateLimiter(redis);
            String email = "hourly-" + UUID.randomUUID() + "@example.edu";
            String normalized = email.toLowerCase();
            String cooldownKey = "auth:rate:otp:cooldown:email:"
                    + VerificationTokens.sha256Hex(normalized);
            String emailKey = "auth:rate:otp:email:"
                    + VerificationTokens.sha256Hex(normalized);

            for (int i = 0; i < 5; i++) {
                limiter.checkAndRecordOtpSend(email, "10.0.0." + i);
                assertTrue(redis.getExpire(cooldownKey) > 0);
                assertTrue(redis.getExpire(emailKey) > 0);
                redis.delete(cooldownKey);
            }

            BizException exception = assertThrows(
                    BizException.class,
                    () -> limiter.checkAndRecordOtpSend(email, "10.0.0.99")
            );
            assertEquals(ErrorCode.RATE_LIMITED.getCode(), exception.getCode());
        } finally {
            factory.destroy();
        }
    }

    @Test
    void captchaIpLimitAndTtl() {
        LettuceConnectionFactory factory = connectionFactory(6379);
        try {
            StringRedisTemplate redis = new StringRedisTemplate(factory);
            VerificationRateLimiter limiter = new VerificationRateLimiter(redis);
            String ip = "192.0.2." + (Math.abs(UUID.randomUUID().getLeastSignificantBits() % 200) + 1);
            String key = "auth:rate:captcha:ip:" + VerificationTokens.sha256Hex(ip);

            for (int i = 0; i < 30; i++) {
                limiter.checkCaptchaGeneration(ip);
            }
            assertTrue(redis.getExpire(key) > 0);

            BizException exception = assertThrows(
                    BizException.class,
                    () -> limiter.checkCaptchaGeneration(ip)
            );
            assertEquals(ErrorCode.RATE_LIMITED.getCode(), exception.getCode());
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
