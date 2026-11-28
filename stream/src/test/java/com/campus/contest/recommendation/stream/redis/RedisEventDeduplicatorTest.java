package com.campus.contest.recommendation.stream.redis;

import org.junit.jupiter.api.Test;
import redis.clients.jedis.Jedis;
import redis.clients.jedis.JedisPool;
import redis.clients.jedis.params.SetParams;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class RedisEventDeduplicatorTest {

    @Test
    void claimsEventOnlyWhenSetNxSucceeds() {
        JedisPool pool = mock(JedisPool.class);
        Jedis jedis = mock(Jedis.class);
        when(pool.getResource()).thenReturn(jedis);
        when(jedis.set(
                eq("rec:flink:dedup:event-1"),
                eq("1"),
                any(SetParams.class)
        )).thenReturn("OK");

        assertThat(new RedisEventDeduplicator(pool).tryClaim("event-1")).isTrue();

        verify(jedis).close();
    }

    @Test
    void treatsNullSetNxResultAsDuplicate() {
        JedisPool pool = mock(JedisPool.class);
        Jedis jedis = mock(Jedis.class);
        when(pool.getResource()).thenReturn(jedis);
        when(jedis.set(
                eq("rec:flink:dedup:event-1"),
                eq("1"),
                any(SetParams.class)
        )).thenReturn(null);

        assertThat(new RedisEventDeduplicator(pool).tryClaim("event-1")).isFalse();

        verify(jedis).close();
    }
}
