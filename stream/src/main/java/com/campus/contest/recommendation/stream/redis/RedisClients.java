package com.campus.contest.recommendation.stream.redis;

import com.campus.contest.recommendation.stream.config.RedisConnectionSettings;
import redis.clients.jedis.JedisPool;
import redis.clients.jedis.JedisPoolConfig;

public final class RedisClients {

    private RedisClients() {
    }

    public static JedisPool createPool(RedisConnectionSettings settings) {
        JedisPoolConfig poolConfig = new JedisPoolConfig();
        poolConfig.setMaxTotal(8);
        poolConfig.setMaxIdle(8);
        poolConfig.setMinIdle(1);
        poolConfig.setTestOnBorrow(true);
        return new JedisPool(
                poolConfig,
                settings.getHost(),
                settings.getPort(),
                3000,
                settings.getPassword(),
                settings.getDatabase()
        );
    }
}
