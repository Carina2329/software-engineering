package com.campus.contest.recommendation.stream.config;

import org.junit.jupiter.api.Test;

import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class StreamSettingsTest {

    @Test
    void commandLineOverridesEnvironmentAndDefaultsAreStable() {
        StreamSettings settings = StreamSettings.fromArgs(
                new String[]{
                        "--kafka-bootstrap-servers", "kafka:29092",
                        "--redis-port=6380"
                },
                Map.of(
                        "KAFKA_BOOTSTRAP_SERVERS", "environment-broker:9092",
                        "REDIS_HOST", "redis",
                        "RECOMMENDATION_KAFKA_TOPIC", "contract-topic"
                )
        );

        assertThat(settings.getKafkaBootstrapServers()).isEqualTo("kafka:29092");
        assertThat(settings.getKafkaTopic()).isEqualTo("contract-topic");
        assertThat(settings.getKafkaGroupId()).isEqualTo("recommendation-stream");
        assertThat(settings.getRedis().getHost()).isEqualTo("redis");
        assertThat(settings.getRedis().getPort()).isEqualTo(6380);
        assertThat(settings.getWindowMinutes()).isEqualTo(5);
        assertThat(settings.getWatermarkMinutes()).isEqualTo(1);
        assertThat(settings.getRetentionDays()).isEqualTo(7);
        assertThat(settings.getCheckpointIntervalMs()).isEqualTo(30000L);
        assertThat(settings.getParallelism()).isEqualTo(1);
    }

    @Test
    void rejectsNonPositiveWindow() {
        assertThatThrownBy(() -> StreamSettings.fromArgs(
                new String[]{"--window-minutes=0"},
                Map.of()
        )).isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("window minutes");
    }

    @Test
    void rejectsUnknownArguments() {
        assertThatThrownBy(() -> StreamSettings.fromArgs(
                new String[]{"positional-value"},
                Map.of()
        )).isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("unsupported argument");
    }
}
