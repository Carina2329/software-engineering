package com.campus.contest.recommendation.stream.window;

import com.campus.contest.recommendation.stream.config.RedisConnectionSettings;
import com.campus.contest.recommendation.stream.redis.RedisPreferenceCounterWriter;

import org.apache.flink.configuration.Configuration;
import org.apache.flink.streaming.api.functions.windowing.ProcessWindowFunction;
import org.apache.flink.streaming.api.windowing.windows.TimeWindow;
import org.apache.flink.util.Collector;

import java.time.Clock;

public class PreferenceRedisWindowFunction
        extends ProcessWindowFunction<
        Long,
        PreferenceWindowResult,
        UserCategoryKey,
        TimeWindow> {

    private static final long serialVersionUID = 1L;

    private final RedisConnectionSettings redisSettings;
    private final int retentionDays;

    private transient RedisPreferenceCounterWriter writer;

    public PreferenceRedisWindowFunction(
            RedisConnectionSettings redisSettings,
            int retentionDays
    ) {
        this.redisSettings = redisSettings;
        this.retentionDays = retentionDays;
    }

    @Override
    public void open(Configuration parameters) {
        writer = new RedisPreferenceCounterWriter(
                redisSettings,
                retentionDays,
                Clock.systemUTC()
        );
        getRuntimeContext()
                .getMetricGroup()
                .counter("recommendation_preference_windows_written");
    }

    @Override
    public void process(
            UserCategoryKey key,
            Context context,
            Iterable<Long> counts,
            Collector<PreferenceWindowResult> out
    ) {
        long viewCount = counts.iterator().next();
        long windowStart = context.window().getStart() / 1000L;
        long windowEnd = context.window().getEnd() / 1000L;
        writer.write(key.getUserId(), key.getCategory(), windowStart, viewCount);
        getRuntimeContext()
                .getMetricGroup()
                .counter("recommendation_preference_windows_written")
                .inc();
        out.collect(new PreferenceWindowResult(
                key.getUserId(),
                key.getCategory(),
                windowStart,
                windowEnd,
                viewCount
        ));
    }

    @Override
    public void close() {
        if (writer != null) {
            writer.close();
        }
    }
}
