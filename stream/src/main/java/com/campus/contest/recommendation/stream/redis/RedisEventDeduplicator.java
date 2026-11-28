package com.campus.contest.recommendation.stream.redis;

import redis.clients.jedis.Jedis;
import redis.clients.jedis.JedisPool;
import redis.clients.jedis.params.SetParams;

public final class RedisEventDeduplicator {

    private static final long DEDUP_TTL_SECONDS =
            (long) RedisRecommendationKeys.DEDUP_TTL_DAYS * 24L * 60L * 60L;

    private final JedisPool pool;

    public RedisEventDeduplicator(JedisPool pool) {
        this.pool = pool;
    }

    /**
     * @return true only when this is the first consumer to claim the event id.
     */
    public boolean tryClaim(String eventId) {
        try (Jedis jedis = pool.getResource()) {
            String result = jedis.set(
                    RedisRecommendationKeys.dedup(eventId),
                    "1",
                    SetParams.setParams().nx().ex(DEDUP_TTL_SECONDS)
            );
            return "OK".equalsIgnoreCase(result);
        }
    }
}
