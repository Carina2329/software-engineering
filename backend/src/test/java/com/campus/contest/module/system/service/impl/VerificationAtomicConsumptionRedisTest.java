package com.campus.contest.module.system.service.impl;

import com.campus.contest.common.BizException;
import com.campus.contest.common.ErrorCode;
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
import java.time.Instant;
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
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
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
            RedisEmailOtpChallengeStore store = new RedisEmailOtpChallengeStore(redis);
            EmailOtpServiceImpl otp = new EmailOtpServiceImpl(
                    store,
                    mock(VerificationRateLimiter.class),
                    mock(AuthUserLookupService.class),
                    mock(CaptchaService.class),
                    mock(JavaMailSender.class)
            );
            String email = "student@example.edu";
            String code = "123456";
            RedisEmailOtpChallengeStore.CreatedChallenge created = store.createOrReplace(
                    email,
                    EmailOtpScene.REGISTER,
                    VerificationTokens.sha256Hex(code),
                    Duration.ofMinutes(5)
            );

            assertEquals(1, concurrentSuccesses(
                    () -> otp.verify(created.challengeId(), email, code, EmailOtpScene.REGISTER)
            ));
        } finally {
            factory.destroy();
        }
    }

    @Test
    void resendInvalidatesPreviousChallenge() {
        LettuceConnectionFactory factory = connectionFactory(6379);
        try {
            StringRedisTemplate redis = new StringRedisTemplate(factory);
            RedisEmailOtpChallengeStore store = new RedisEmailOtpChallengeStore(redis);
            String email = "student@example.edu";
            String code = "123456";
            RedisEmailOtpChallengeStore.CreatedChallenge first = store.createOrReplace(
                    email, EmailOtpScene.REGISTER, VerificationTokens.sha256Hex(code), Duration.ofMinutes(5)
            );
            RedisEmailOtpChallengeStore.CreatedChallenge second = store.createOrReplace(
                    email, EmailOtpScene.REGISTER, VerificationTokens.sha256Hex(code), Duration.ofMinutes(5)
            );

            BizException stale = assertThrows(
                    BizException.class,
                    () -> store.verifyAndConsume(
                            first.challengeId(), email, VerificationTokens.sha256Hex(code), EmailOtpScene.REGISTER
                    )
            );
            assertEquals(ErrorCode.INVALID_PARAM.getCode(), stale.getCode());
            assertTrue(store.isCurrentAndActive(second.challengeId(), email, EmailOtpScene.REGISTER, Instant.now()));
        } finally {
            factory.destroy();
        }
    }

    @Test
    void rollbackOfOlderRequestDoesNotDeleteLaterChallenge() {
        LettuceConnectionFactory factory = connectionFactory(6379);
        try {
            StringRedisTemplate redis = new StringRedisTemplate(factory);
            RedisEmailOtpChallengeStore store = new RedisEmailOtpChallengeStore(redis);
            String email = "student@example.edu";
            String code = "123456";
            RedisEmailOtpChallengeStore.CreatedChallenge first = store.createOrReplace(
                    email, EmailOtpScene.LOGIN, VerificationTokens.sha256Hex(code), Duration.ofMinutes(5)
            );
            RedisEmailOtpChallengeStore.CreatedChallenge second = store.createOrReplace(
                    email, EmailOtpScene.LOGIN, VerificationTokens.sha256Hex(code), Duration.ofMinutes(5)
            );

            store.rollbackFailedDispatch(first.challengeId(), email, EmailOtpScene.LOGIN);

            assertFalse(store.isCurrentAndActive(first.challengeId(), email, EmailOtpScene.LOGIN, Instant.now()));
            assertTrue(store.isCurrentAndActive(second.challengeId(), email, EmailOtpScene.LOGIN, Instant.now()));
            store.verifyAndConsume(
                    second.challengeId(), email, VerificationTokens.sha256Hex(code), EmailOtpScene.LOGIN
            );
        } finally {
            factory.destroy();
        }
    }

    @Test
    void fifthWrongAttemptInvalidatesChallenge() {
        LettuceConnectionFactory factory = connectionFactory(6379);
        try {
            StringRedisTemplate redis = new StringRedisTemplate(factory);
            RedisEmailOtpChallengeStore store = new RedisEmailOtpChallengeStore(redis);
            String email = "student@example.edu";
            String code = "123456";
            RedisEmailOtpChallengeStore.CreatedChallenge challenge = store.createOrReplace(
                    email, EmailOtpScene.REGISTER, VerificationTokens.sha256Hex(code), Duration.ofMinutes(5)
            );

            for (int i = 0; i < 4; i++) {
                BizException wrong = assertThrows(
                        BizException.class,
                        () -> store.verifyAndConsume(
                                challenge.challengeId(),
                                email,
                                VerificationTokens.sha256Hex("000000"),
                                EmailOtpScene.REGISTER
                        )
                );
                assertEquals(ErrorCode.INVALID_PARAM.getCode(), wrong.getCode());
            }
            BizException exhausted = assertThrows(
                    BizException.class,
                    () -> store.verifyAndConsume(
                            challenge.challengeId(),
                            email,
                            VerificationTokens.sha256Hex("000000"),
                            EmailOtpScene.REGISTER
                    )
            );
            assertEquals(ErrorCode.RATE_LIMITED.getCode(), exhausted.getCode());

            BizException afterInvalidation = assertThrows(
                    BizException.class,
                    () -> store.verifyAndConsume(
                            challenge.challengeId(), email, VerificationTokens.sha256Hex(code), EmailOtpScene.REGISTER
                    )
            );
            assertEquals(ErrorCode.INVALID_PARAM.getCode(), afterInvalidation.getCode());
        } finally {
            factory.destroy();
        }
    }

    @Test
    void currentChallengeExpires() {
        LettuceConnectionFactory factory = connectionFactory(6379);
        try {
            StringRedisTemplate redis = new StringRedisTemplate(factory);
            RedisEmailOtpChallengeStore store = new RedisEmailOtpChallengeStore(redis);
            String email = "student@example.edu";
            RedisEmailOtpChallengeStore.CreatedChallenge challenge = store.createOrReplace(
                    email,
                    EmailOtpScene.REGISTER,
                    VerificationTokens.sha256Hex("123456"),
                    Duration.ofMinutes(5)
            );

            assertTrue(store.isCurrentAndActive(challenge.challengeId(), email, EmailOtpScene.REGISTER, Instant.now()));
            assertFalse(store.isCurrentAndActive(
                    challenge.challengeId(),
                    email,
                    EmailOtpScene.REGISTER,
                    Instant.now().plusSeconds(600)
            ));
        } finally {
            factory.destroy();
        }
    }

    @Test
    void redisFailureReturns50003() {
        LettuceConnectionFactory factory = connectionFactory(6390);
        try {
            StringRedisTemplate redis = new StringRedisTemplate(factory);
            RedisEmailOtpChallengeStore store = new RedisEmailOtpChallengeStore(redis);

            BizException createFailure = assertThrows(
                    BizException.class,
                    () -> store.createOrReplace(
                            "student@example.edu",
                            EmailOtpScene.REGISTER,
                            VerificationTokens.sha256Hex("123456"),
                            Duration.ofMinutes(5)
                    )
            );
            assertEquals(ErrorCode.AUTH_IDEMPOTENCY_UNAVAILABLE.getCode(), createFailure.getCode());

            BizException checkFailure = assertThrows(
                    BizException.class,
                    () -> store.isCurrentAndActive(
                            "missing", "student@example.edu", EmailOtpScene.REGISTER, Instant.now()
                    )
            );
            assertEquals(ErrorCode.AUTH_IDEMPOTENCY_UNAVAILABLE.getCode(), checkFailure.getCode());

            BizException rollbackFailure = assertThrows(
                    BizException.class,
                    () -> store.rollbackFailedDispatch(
                            "missing", "student@example.edu", EmailOtpScene.REGISTER
                    )
            );
            assertEquals(ErrorCode.AUTH_IDEMPOTENCY_UNAVAILABLE.getCode(), rollbackFailure.getCode());
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
