package com.campus.contest.common.idempotency;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.data.redis.core.StringRedisTemplate;

/**
 * 测试环境复用生产 Redis 幂等实现，避免 HTTP 联调退化成内存假实现。
 */
@Configuration(proxyBeanMethods = false)
public class TestRedisIdempotencyConfig {

    @Bean
    public IdempotencyStore idempotencyStore(StringRedisTemplate redisTemplate) {
        return new RedisIdempotencyStore(redisTemplate);
    }

    @Bean
    public IdempotencyService idempotencyService(
            IdempotencyStore idempotencyStore,
            ObjectMapper objectMapper
    ) {
        return new RedisIdempotencyService(idempotencyStore, objectMapper);
    }
}
