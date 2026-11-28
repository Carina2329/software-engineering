package com.campus.contest.recommendation.stream.function;

import com.campus.contest.recommendation.stream.config.RedisConnectionSettings;
import com.campus.contest.recommendation.stream.event.BehaviorEvent;
import com.campus.contest.recommendation.stream.redis.RedisClients;
import com.campus.contest.recommendation.stream.redis.RedisEventDeduplicator;

import org.apache.flink.configuration.Configuration;
import org.apache.flink.metrics.Counter;
import org.apache.flink.streaming.api.functions.KeyedProcessFunction;
import org.apache.flink.util.Collector;
import redis.clients.jedis.JedisPool;

public class EventDeduplicationProcessFunction
        extends KeyedProcessFunction<String, BehaviorEvent, BehaviorEvent> {

    private static final long serialVersionUID = 1L;

    private final RedisConnectionSettings redisSettings;

    private transient JedisPool pool;
    private transient RedisEventDeduplicator deduplicator;
    private transient Counter receivedCounter;
    private transient Counter deduplicatedCounter;

    public EventDeduplicationProcessFunction(RedisConnectionSettings redisSettings) {
        this.redisSettings = redisSettings;
    }

    @Override
    public void open(Configuration parameters) {
        pool = RedisClients.createPool(redisSettings);
        deduplicator = new RedisEventDeduplicator(pool);
        receivedCounter = getRuntimeContext()
                .getMetricGroup()
                .counter("recommendation_events_received");
        deduplicatedCounter = getRuntimeContext()
                .getMetricGroup()
                .counter("recommendation_events_deduplicated");
    }

    @Override
    public void processElement(
            BehaviorEvent value,
            Context context,
            Collector<BehaviorEvent> out
    ) {
        receivedCounter.inc();
        if (deduplicator.tryClaim(value.getEventId())) {
            out.collect(value);
            return;
        }
        deduplicatedCounter.inc();
    }

    @Override
    public void close() {
        if (pool != null) {
            pool.close();
        }
    }
}
