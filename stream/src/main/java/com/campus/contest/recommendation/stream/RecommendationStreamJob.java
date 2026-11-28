package com.campus.contest.recommendation.stream;

import com.campus.contest.recommendation.stream.config.StreamSettings;
import com.campus.contest.recommendation.stream.event.BehaviorEvent;
import com.campus.contest.recommendation.stream.event.BehaviorEventKafkaDeserializer;
import com.campus.contest.recommendation.stream.function.EventDeduplicationProcessFunction;
import com.campus.contest.recommendation.stream.sink.NoOpSink;
import com.campus.contest.recommendation.stream.window.PreferenceRedisWindowFunction;
import com.campus.contest.recommendation.stream.window.PreferenceWindowResult;
import com.campus.contest.recommendation.stream.window.UserCategoryKey;
import com.campus.contest.recommendation.stream.window.ViewCountAggregate;

import org.apache.flink.api.common.eventtime.WatermarkStrategy;
import org.apache.flink.connector.kafka.source.KafkaSource;
import org.apache.flink.connector.kafka.source.enumerator.initializer.OffsetsInitializer;
import org.apache.flink.streaming.api.CheckpointingMode;
import org.apache.flink.streaming.api.datastream.DataStream;
import org.apache.flink.streaming.api.datastream.SingleOutputStreamOperator;
import org.apache.flink.streaming.api.environment.CheckpointConfig;
import org.apache.flink.streaming.api.environment.StreamExecutionEnvironment;
import org.apache.flink.streaming.api.windowing.assigners.TumblingEventTimeWindows;
import org.apache.flink.streaming.api.windowing.time.Time;
import org.apache.flink.util.OutputTag;
import org.apache.kafka.clients.consumer.OffsetResetStrategy;

import java.time.Duration;

public final class RecommendationStreamJob {

    public static final String JOB_NAME = "recommendation-stream";
    public static final OutputTag<BehaviorEvent> LATE_VIEW_EVENTS =
            new OutputTag<BehaviorEvent>("late-contest-detail-views") {
                private static final long serialVersionUID = 1L;
            };

    private RecommendationStreamJob() {
    }

    public static void main(String[] args) throws Exception {
        StreamSettings settings = StreamSettings.fromSystemEnvironment(args);
        StreamExecutionEnvironment environment =
                StreamExecutionEnvironment.getExecutionEnvironment();
        configureEnvironment(environment, settings);
        buildPipeline(environment, settings);
        environment.execute(JOB_NAME);
    }

    static void buildPipeline(
            StreamExecutionEnvironment environment,
            StreamSettings settings
    ) {
        KafkaSource<BehaviorEvent> source = KafkaSource.<BehaviorEvent>builder()
                .setBootstrapServers(settings.getKafkaBootstrapServers())
                .setTopics(settings.getKafkaTopic())
                .setGroupId(settings.getKafkaGroupId())
                .setStartingOffsets(
                        OffsetsInitializer.committedOffsets(OffsetResetStrategy.EARLIEST)
                )
                .setProperty("enable.auto.commit", "false")
                .setProperty("partition.discovery.interval.ms", "30000")
                .setDeserializer(new BehaviorEventKafkaDeserializer())
                .build();

        WatermarkStrategy<BehaviorEvent> watermarks = WatermarkStrategy
                .<BehaviorEvent>forBoundedOutOfOrderness(
                        Duration.ofMinutes(settings.getWatermarkMinutes())
                )
                .withIdleness(Duration.ofSeconds(30))
                .withTimestampAssigner(
                        (event, previousTimestamp) -> event.occurredAtMillis()
                );

        DataStream<BehaviorEvent> events = environment
                .fromSource(source, watermarks, "kafka-contest-behavior")
                .name("kafka-contest-behavior")
                .uid("kafka-contest-behavior");

        SingleOutputStreamOperator<BehaviorEvent> deduplicated = events
                .keyBy(BehaviorEvent::getEventId)
                .process(new EventDeduplicationProcessFunction(settings.getRedis()))
                .name("redis-event-deduplication")
                .uid("redis-event-deduplication");

        SingleOutputStreamOperator<PreferenceWindowResult> preferences = deduplicated
                .keyBy(event -> new UserCategoryKey(event.getUserId(), event.getCategory()))
                .window(
                        TumblingEventTimeWindows.of(
                                Time.minutes(settings.getWindowMinutes())
                        )
                )
                .sideOutputLateData(LATE_VIEW_EVENTS)
                .aggregate(
                        new ViewCountAggregate(),
                        new PreferenceRedisWindowFunction(
                                settings.getRedis(),
                                settings.getRetentionDays()
                        )
                )
                .name("five-minute-preference-window")
                .uid("five-minute-preference-window");

        preferences
                .addSink(new NoOpSink<PreferenceWindowResult>())
                .name("discard-window-result")
                .uid("discard-window-result");
    }

    private static void configureEnvironment(
            StreamExecutionEnvironment environment,
            StreamSettings settings
    ) {
        environment.setParallelism(settings.getParallelism());
        environment.enableCheckpointing(
                settings.getCheckpointIntervalMs(),
                CheckpointingMode.AT_LEAST_ONCE
        );
        CheckpointConfig checkpointConfig = environment.getCheckpointConfig();
        checkpointConfig.setMinPauseBetweenCheckpoints(5000L);
        checkpointConfig.setCheckpointTimeout(60000L);
        checkpointConfig.setMaxConcurrentCheckpoints(1);
        checkpointConfig.setExternalizedCheckpointCleanup(
                CheckpointConfig.ExternalizedCheckpointCleanup.RETAIN_ON_CANCELLATION
        );
    }
}
