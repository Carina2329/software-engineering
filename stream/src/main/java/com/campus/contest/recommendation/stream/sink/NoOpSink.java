package com.campus.contest.recommendation.stream.sink;

import org.apache.flink.streaming.api.functions.sink.RichSinkFunction;

public class NoOpSink<T> extends RichSinkFunction<T> {

    private static final long serialVersionUID = 1L;

    @Override
    public void invoke(T value, Context context) {
        // Redis writes are performed by the window function.
    }
}
