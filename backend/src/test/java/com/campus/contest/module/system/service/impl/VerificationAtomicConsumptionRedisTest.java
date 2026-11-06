package com.campus.contest.module.system.service.impl;

import com.campus.contest.common.BizException;
import com.campus.contest.module.system.enums.CaptchaScene;
import com.campus.contest.module.system.enums.EmailOtpScene;
import com.campus.contest.module.system.service.AuthUserLookupService;
import com.campus.contest.module.system.service.CaptchaService;
import com.campus.contest.module.system.service.VerificationRateLimiter;
import com.campus.contest.module.system.service.VerificationTokens;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;
import org.springframework.data.redis.connection.lettuce.LettuceConnectionFactory;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.mail.javamail.JavaMailSender;

import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.Mockito.mock;

@EnabledIfEnvironmentVariable(named = "RUN_REDIS_TESTS", matches = "true")
class VerificationAtomicConsumptionRedisTest {

    @Test
    void captchaAllowsExactlyOneConcurrentSuccess() throws Exception {
        LettuceConnectionFactory factory = connectionFactory(6379);
        try {
            StringRedisTemplate redis = new StringRedisTemplate(factory);
            CaptchaServiceImpl captcha = new CaptchaServiceImpl(redis, mock(VerificationRateLimiter.class));
            String captchaId = "captcha-" + UUID.randomUUID();
            String code = "ABCD";
            redis.opsForHash().putAll("auth:captcha:" + captchaId, Map.of(
                    "scene", CaptchaScene.LOGIN.name(),
                    "answerHash", VerificationTokens.sha256Hex(code),
                    "attempts", "0"
            ));
            redis.expire("auth:captcha:" + captchaId, Duration.ofMinutes(2));

            assertEquals(1, concurrentSuccesses(() -> captcha.verify(captchaId, code, CaptchaScene.LOGIN)));
        } finally {
            factory.destroy();
        }
    }

    @Test
    void emailOtpAllowsExactlyOneConcurrentSuccess() throws Exception {
        LettuceConnectionFactory factory = connectionFactory(6379);
        try {
            StringRedisTemplate redis = new StringRedisTemplate(factory);
            EmailOtpServiceImpl otp = new EmailOtpServiceImpl(
                    redis,
                    mock(VerificationRateLimiter.class),
                    mock(AuthUserLookupService.class),
                    mock(CaptchaService.class),
                    mock(JavaMailSender.class)
            );
            String challengeId = "challenge-" + UUID.randomUUID();
            String email = "student@example.edu";
            String code = "123456";
            redis.opsForHash().putAll("auth:challenge:" + challengeId, Map.of(
                    "email", email,
                    "scene", EmailOtpScene.REGISTER.name(),
                    "codeHash", VerificationTokens.sha256Hex(code),
                    "attempts", "0"
            ));
            redis.expire("auth:challenge:" + challengeId, Duration.ofMinutes(5));

            assertEquals(1, concurrentSuccesses(
                    () -> otp.verify(challengeId, email, code, EmailOtpScene.REGISTER)
            ));
        } finally {
            factory.destroy();
        }
    }

    private int concurrentSuccesses(Runnable action) throws Exception {
        int threads = 8;
        ExecutorService executor = Executors.newFixedThreadPool(threads);
        CountDownLatch ready = new CountDownLatch(threads);
        CountDownLatch start = new CountDownLatch(1);
        AtomicInteger successes = new AtomicInteger();
        List<Future<?>> futures = new ArrayList<>();
        try {
            for (int i = 0; i < threads; i++) {
                futures.add(executor.submit(() -> {
                    ready.countDown();
                    await(start);
                    try {
                        action.run();
                        successes.incrementAndGet();
                    } catch (BizException ignored) {
                        // 其余请求应失败
                    }
                }));
            }
            ready.await(5, TimeUnit.SECONDS);
            start.countDown();
            for (Future<?> future : futures) {
                future.get(5, TimeUnit.SECONDS);
            }
            return successes.get();
        } finally {
            executor.shutdownNow();
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
