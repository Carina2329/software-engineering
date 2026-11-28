package com.campus.contest.recommendation.stream.window;

import com.campus.contest.recommendation.stream.event.BehaviorEvent;
import org.apache.flink.api.common.functions.AggregateFunction;

public class ViewCountAggregate implements AggregateFunction<BehaviorEvent, Long, Long> {

    private static final long serialVersionUID = 1L;

    @Override
    public Long createAccumulator() {
        return 0L;
    }

    @Override
    public Long add(BehaviorEvent value, Long accumulator) {
        return accumulator + 1L;
    }

    @Override
    public Long getResult(Long accumulator) {
        return accumulator;
    }

    @Override
    public Long merge(Long left, Long right) {
        return left + right;
    }
}
