package com.campus.contest.recommendation.stream.redis;

import org.junit.jupiter.api.Test;
import redis.clients.jedis.Jedis;
import redis.clients.jedis.JedisPool;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.Arrays;
import java.util.Collections;

import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

class RedisPreferenceCounterWriterTest {

    private static final Instant NOW = Instant.parse("2026-11-28T12:00:00Z");

    @Test
    void atomicallyCleansRetainsMaximumAndRefreshesTtl() {
        JedisPool pool = mock(JedisPool.class);
        Jedis jedis = mock(Jedis.class);
        when(pool.getResource()).thenReturn(jedis);
        RedisPreferenceCounterWriter writer = new RedisPreferenceCounterWriter(
                pool,
                7,
                Clock.fixed(NOW, ZoneOffset.UTC)
        );

        writer.write("101", "MATH_MODELING", 1795861800L, 4L);

        verify(jedis).eval(
                eq(RedisPreferenceCounterWriter.UPDATE_SCRIPT),
                eq(Collections.singletonList(
                        "rec:user:101:cat:MATH_MODELING"
                )),
                eq(Arrays.asList(
                        "1795861800",
                        "4",
                        "1795262400",
                        "691200"
                ))
        );
        verify(jedis).close();
    }

    @Test
    void rejectsNegativeCountBeforeTakingRedisConnection() {
        JedisPool pool = mock(JedisPool.class);
        RedisPreferenceCounterWriter writer = new RedisPreferenceCounterWriter(
                pool,
                7,
                Clock.fixed(NOW, ZoneOffset.UTC)
        );

        assertThatThrownBy(() -> writer.write(
                "101",
                "MATH_MODELING",
                1795861800L,
                -1L
        )).isInstanceOf(IllegalArgumentException.class);
        verifyNoInteractions(pool);
    }
}
