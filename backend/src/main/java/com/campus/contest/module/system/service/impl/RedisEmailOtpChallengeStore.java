package com.campus.contest.module.system.service.impl;

import com.campus.contest.common.BizException;
import com.campus.contest.common.ErrorCode;
import com.campus.contest.module.system.enums.EmailOtpScene;
import com.campus.contest.module.system.service.EmailOtpChallengeStore;
import com.campus.contest.module.system.service.VerificationTokens;
import lombok.RequiredArgsConstructor;
import org.springframework.dao.DataAccessException;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.script.DefaultRedisScript;
import org.springframework.data.redis.core.script.RedisScript;
import org.springframework.stereotype.Service;

import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.Locale;

/**
 * 邮件 OTP challenge 的唯一 Redis 实现：原子创建/替换索引、原子校验消费、原子失败回滚。
 *
 * <p>Key 约定：
 * <ul>
 *   <li>{@code auth:challenge:{challengeId}}：challenge 数据，TTL 与 OTP 一致；</li>
 *   <li>{@code auth:challenge:current:{scene}:{emailHash}}：当前 challenge 索引；
 *       emailHash 为规范化邮箱（trim + 小写）的 SHA-256，不保存明文邮箱。</li>
 * </ul>
 */
@Service
@RequiredArgsConstructor
public class RedisEmailOtpChallengeStore implements EmailOtpChallengeStore {

    static final String CHALLENGE_KEY_PREFIX = "auth:challenge:";
    static final String CURRENT_INDEX_PREFIX = "auth:challenge:current:";
    static final int MAX_ATTEMPTS = 5;

    /**
     * 原子创建并替换当前 challenge：
     * 写入新 challenge 和 TTL，替换当前索引，并让旧 challenge 立即失效。
     */
    private static final RedisScript<Long> CREATE_SCRIPT = new DefaultRedisScript<>(
            """
            local previous = redis.call('GET', KEYS[2])
            redis.call('HSET', KEYS[1],
              'email', ARGV[1],
              'scene', ARGV[2],
              'codeHash', ARGV[3],
              'attempts', '0',
              'challengeId', ARGV[5],
              'expiresAt', ARGV[6])
            redis.call('PEXPIRE', KEYS[1], ARGV[4])
            redis.call('SET', KEYS[2], ARGV[5], 'PX', ARGV[4])
            if previous and previous ~= ARGV[5] then
              redis.call('DEL', ARGV[7] .. previous)
            end
            return 1
            """,
            Long.class
    );

    /**
     * 原子校验并消费：1=成功，0=不存在，2=邮箱或场景不匹配，3=已超次数，4=错误次数达上限，5=错误未达上限。
     */
    private static final RedisScript<Long> VERIFY_SCRIPT = new DefaultRedisScript<>(
            """
            local stored = redis.call('HGETALL', KEYS[1])
            if #stored == 0 then
              return 0
            end
            local fields = {}
            for i = 1, #stored, 2 do
              fields[stored[i]] = stored[i + 1]
            end
            if fields['email'] ~= ARGV[1] or fields['scene'] ~= ARGV[2] then
              redis.call('DEL', KEYS[1])
              return 2
            end
            local attempts = tonumber(fields['attempts'] or '0')
            if attempts >= tonumber(ARGV[4]) then
              redis.call('DEL', KEYS[1])
              return 3
            end
            if fields['codeHash'] == ARGV[3] then
              redis.call('DEL', KEYS[1])
              return 1
            end
            attempts = attempts + 1
            if attempts >= tonumber(ARGV[4]) then
              redis.call('DEL', KEYS[1])
              return 4
            end
            redis.call('HSET', KEYS[1], 'attempts', tostring(attempts))
            return 5
            """,
            Long.class
    );

    /**
     * 供 C 的消费者只读判断：索引仍指向本 challenge、challenge 存在、邮箱/场景匹配且未过期。
     */
    private static final RedisScript<Long> CURRENT_SCRIPT = new DefaultRedisScript<>(
            """
            local current = redis.call('GET', KEYS[2])
            if not current or current ~= ARGV[1] then
              return 0
            end
            if redis.call('EXISTS', KEYS[1]) == 0 then
              return 0
            end
            local storedEmail = redis.call('HGET', KEYS[1], 'email')
            local storedScene = redis.call('HGET', KEYS[1], 'scene')
            local expiresAt = tonumber(redis.call('HGET', KEYS[1], 'expiresAt') or '0')
            if storedEmail ~= ARGV[2] or storedScene ~= ARGV[3] then
              return 0
            end
            if tonumber(ARGV[4]) >= expiresAt then
              return 0
            end
            return 1
            """,
            Long.class
    );

    /**
     * 原子失败回滚：始终只删本请求 challenge；仅当索引仍指向本 challenge 时才删索引。
     */
    private static final RedisScript<Long> ROLLBACK_SCRIPT = new DefaultRedisScript<>(
            """
            local current = redis.call('GET', KEYS[2])
            if current == ARGV[1] then
              redis.call('DEL', KEYS[2])
            end
            redis.call('DEL', KEYS[1])
            return 1
            """,
            Long.class
    );

    private final StringRedisTemplate redisTemplate;

    /**
     * 供 EmailOtpServiceImpl 使用的原子创建/替换能力；不在冻结的跨模块接口中暴露。
     */
    public CreatedChallenge createOrReplace(
            String email,
            EmailOtpScene scene,
            String codeHash,
            Duration ttl
    ) {
        String normalizedEmail = normalizeEmail(email);
        String challengeId = VerificationTokens.nextId();
        Instant expiresAt = Instant.now().plus(ttl);
        try {
            Long result = redisTemplate.execute(
                    CREATE_SCRIPT,
                    List.of(challengeKey(challengeId), indexKey(normalizedEmail, scene)),
                    normalizedEmail,
                    scene.name(),
                    codeHash,
                    String.valueOf(ttl.toMillis()),
                    challengeId,
                    String.valueOf(expiresAt.toEpochMilli()),
                    CHALLENGE_KEY_PREFIX
            );
            if (result == null || result != 1L) {
                throw unavailable();
            }
        } catch (DataAccessException e) {
            throw unavailable();
        }
        return new CreatedChallenge(challengeId, expiresAt);
    }

    /**
     * 供 EmailOtpServiceImpl 使用的原子校验并消费能力；不在冻结的跨模块接口中暴露。
     */
    public void verifyAndConsume(
            String challengeId,
            String email,
            String codeHash,
            EmailOtpScene scene
    ) {
        String normalizedEmail = normalizeEmail(email);
        try {
            Long result = redisTemplate.execute(
                    VERIFY_SCRIPT,
                    List.of(challengeKey(challengeId)),
                    normalizedEmail,
                    scene.name(),
                    codeHash,
                    String.valueOf(MAX_ATTEMPTS)
            );
            if (result == null) {
                throw unavailable();
            }
            if (result == 1L) {
                return;
            }
            if (result == 4L) {
                throw tooManyAttempts();
            }
            throw invalidOtp();
        } catch (DataAccessException e) {
            throw unavailable();
        }
    }

    @Override
    public boolean isCurrentAndActive(
            String challengeId,
            String email,
            EmailOtpScene scene,
            Instant now
    ) {
        if (challengeId == null || challengeId.isBlank()
                || email == null || scene == null || now == null) {
            return false;
        }
        String normalizedEmail = normalizeEmail(email);
        try {
            Long result = redisTemplate.execute(
                    CURRENT_SCRIPT,
                    List.of(challengeKey(challengeId), indexKey(normalizedEmail, scene)),
                    challengeId,
                    normalizedEmail,
                    scene.name(),
                    String.valueOf(now.toEpochMilli())
            );
            return result != null && result == 1L;
        } catch (DataAccessException e) {
            throw unavailable();
        }
    }

    @Override
    public void rollbackFailedDispatch(
            String challengeId,
            String email,
            EmailOtpScene scene
    ) {
        if (challengeId == null || challengeId.isBlank() || email == null || scene == null) {
            return;
        }
        String normalizedEmail = normalizeEmail(email);
        try {
            redisTemplate.execute(
                    ROLLBACK_SCRIPT,
                    List.of(challengeKey(challengeId), indexKey(normalizedEmail, scene)),
                    challengeId
            );
        } catch (DataAccessException e) {
            throw unavailable();
        }
    }

    String challengeKey(String challengeId) {
        return CHALLENGE_KEY_PREFIX + challengeId;
    }

    String indexKey(String email, EmailOtpScene scene) {
        return CURRENT_INDEX_PREFIX
                + scene.name()
                + ":"
                + VerificationTokens.sha256Hex(normalizeEmail(email));
    }

    private String normalizeEmail(String email) {
        return email == null ? "" : email.trim().toLowerCase(Locale.ROOT);
    }

    private BizException invalidOtp() {
        return new BizException(
                ErrorCode.INVALID_PARAM.getCode(),
                "邮箱验证码不正确或已失效"
        );
    }

    private BizException tooManyAttempts() {
        return new BizException(
                ErrorCode.RATE_LIMITED.getCode(),
                "邮箱验证码错误次数过多，请重新获取"
        );
    }

    private BizException unavailable() {
        return new BizException(
                ErrorCode.AUTH_IDEMPOTENCY_UNAVAILABLE.getCode(),
                ErrorCode.AUTH_IDEMPOTENCY_UNAVAILABLE.getMessage()
        );
    }

    public record CreatedChallenge(String challengeId, Instant expiresAt) {
    }
}
