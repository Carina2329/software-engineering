package com.campus.contest.recommendation.stream.redis;

import org.junit.jupiter.api.Test;

import java.time.Instant;

import static org.assertj.core.api.Assertions.assertThat;

class RedisRecommendationKeysTest {

    @Test
    void usesFrozenKeyPatterns() {
        assertThat(RedisRecommendationKeys.dedup("event-1"))
                .isEqualTo("rec:flink:dedup:event-1");
        assertThat(RedisRecommendationKeys.userCategory("101", "MATH_MODELING"))
                .isEqualTo("rec:user:101:cat:MATH_MODELING");
    }

    @Test
    void encodesWindowAsUtcEpochSecond() {
        assertThat(RedisRecommendationKeys.bucketField(
                Instant.parse("2026-11-28T10:30:00Z")
        )).isEqualTo("1795861800");
    }
}
