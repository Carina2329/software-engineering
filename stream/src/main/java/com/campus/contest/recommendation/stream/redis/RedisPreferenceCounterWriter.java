package com.campus.contest.recommendation.stream.redis;

import com.campus.contest.recommendation.stream.config.RedisConnectionSettings;

import redis.clients.jedis.Jedis;
import redis.clients.jedis.JedisPool;

import java.time.Clock;
import java.util.Arrays;
import java.util.Collections;

public final class RedisPreferenceCounterWriter implements AutoCloseable {

    /*
     * Cleaning and retaining the maximum count are performed atomically so a
     * replayed subset cannot shrink a previously completed window.
     */
    static final String UPDATE_SCRIPT =
            "local fields = redis.call('HKEYS', KEYS[1]) "
                    + "local cutoff = tonumber(ARGV[3]) "
                    + "for _, field in ipairs(fields) do "
                    + "  local bucket = tonumber(field) "
                    + "  if bucket and bucket < cutoff then "
                    + "    redis.call('HDEL', KEYS[1], field) "
                    + "  end "
                    + "end "
                    + "local current = redis.call('HGET', KEYS[1], ARGV[1]) "
                    + "local currentCount = tonumber(current) or -1 "
                    + "if tonumber(ARGV[2]) > currentCount then "
                    + "  redis.call('HSET', KEYS[1], ARGV[1], ARGV[2]) "
                    + "end "
                    + "redis.call('EXPIRE', KEYS[1], ARGV[4]) "
                    + "return 1";

    private static final long DAY_SECONDS = 24L * 60L * 60L;
    private static final long PREFERENCE_TTL_SECONDS =
            (long) RedisRecommendationKeys.PREFERENCE_TTL_DAYS * DAY_SECONDS;

    private final JedisPool pool;
    private final int retentionDays;
    private final Clock clock;

    public RedisPreferenceCounterWriter(
            RedisConnectionSettings settings,
            int retentionDays,
            Clock clock
    ) {
        this(RedisClients.createPool(settings), retentionDays, clock);
    }

    RedisPreferenceCounterWriter(JedisPool pool, int retentionDays, Clock clock) {
        if (retentionDays <= 0) {
            throw new IllegalArgumentException("retention days must be > 0");
        }
        this.pool = pool;
        this.retentionDays = retentionDays;
        this.clock = clock;
    }

    public void write(
            String userId,
            String category,
            long windowStartEpochSecond,
            long viewCount
    ) {
        if (viewCount < 0) {
            throw new IllegalArgumentException("view count must be >= 0");
        }
        long cutoff = clock.instant().getEpochSecond()
                - (long) retentionDays * DAY_SECONDS;
        try (Jedis jedis = pool.getResource()) {
            jedis.eval(
                    UPDATE_SCRIPT,
                    Collections.singletonList(
                            RedisRecommendationKeys.userCategory(userId, category)
                    ),
                    Arrays.asList(
                            RedisRecommendationKeys.bucketField(windowStartEpochSecond),
                            Long.toString(viewCount),
                            Long.toString(cutoff),
                            Long.toString(PREFERENCE_TTL_SECONDS)
                    )
            );
        }
    }

    @Override
    public void close() {
        pool.close();
    }
}
