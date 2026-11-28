package com.campus.contest.recommendation.stream.function;

import com.campus.contest.recommendation.stream.config.RedisConnectionSettings;
import com.campus.contest.recommendation.stream.event.BehaviorEvent;
import com.campus.contest.recommendation.stream.redis.RedisClients;
import com.campus.contest.recommendation.stream.redis.RedisEventDeduplicator;

import org.apache.flink.configuration.Configuration;
import org.apache.flink.streaming.api.functions.KeyedProcessFunction;
import org.apache.flink.util.Collector;
import redis.clients.jedis.JedisPool;

public class EventDeduplicationProcessFunction
        extends KeyedProcessFunction<String, BehaviorEvent, BehaviorEvent> {

    private static final long serialVersionUID = 1L;

    private final RedisConnectionSettings redisSettings;

    private transient JedisPool pool;
    private transient RedisEventDeduplicator deduplicator;

    public EventDeduplicationProcessFunction(RedisConnectionSettings redisSettings) {
        this.redisSettings = redisSettings;
    }

    @Override
    public void open(Configuration parameters) {
        pool = RedisClients.createPool(redisSettings);
        deduplicator = new RedisEventDeduplicator(pool);
        getRuntimeContext()
                .getMetricGroup()
                .counter("recommendation_events_received");
        getRuntimeContext()
                .getMetricGroup()
                .counter("recommendation_events_deduplicated");
    }

    @Override
    public void processElement(
            BehaviorEvent value,
            Context context,
            Collector<BehaviorEvent> out
    ) {
        getRuntimeContext()
                .getMetricGroup()
                .counter("recommendation_events_received")
                .inc();
        if (deduplicator.tryClaim(value.getEventId())) {
            out.collect(value);
            return;
        }
        getRuntimeContext()
                .getMetricGroup()
                .counter("recommendation_events_deduplicated")
                .inc();
    }

    @Override
    public void close() {
        if (pool != null) {
            pool.close();
        }
    }
}
