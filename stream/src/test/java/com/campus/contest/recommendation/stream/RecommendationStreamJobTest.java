package com.campus.contest.recommendation.stream;

import com.campus.contest.recommendation.stream.config.RedisConnectionSettings;
import com.campus.contest.recommendation.stream.config.StreamSettings;

import org.apache.flink.streaming.api.environment.StreamExecutionEnvironment;
import org.apache.flink.streaming.api.graph.StreamNode;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.stream.Collectors;

import static org.assertj.core.api.Assertions.assertThat;

class RecommendationStreamJobTest {

    @Test
    void buildsKafkaDeduplicationWindowAndRedisPipeline() {
        StreamExecutionEnvironment environment =
                StreamExecutionEnvironment.getExecutionEnvironment();
        StreamSettings settings = new StreamSettings(
                "localhost:9092",
                "contest-behavior",
                "recommendation-stream-test",
                new RedisConnectionSettings("localhost", 6379, null, 0),
                5,
                1,
                7,
                30000L,
                1
        );

        RecommendationStreamJob.buildPipeline(environment, settings);

        List<String> names = environment.getStreamGraph()
                .getStreamNodes()
                .stream()
                .map(StreamNode::getOperatorName)
                .collect(Collectors.toList());

        assertThat(names).contains(
                "Source: kafka-contest-behavior",
                "redis-event-deduplication",
                "five-minute-preference-window",
                "Sink: discard-window-result"
        );
    }
}
