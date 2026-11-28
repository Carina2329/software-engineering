package com.campus.contest.recommendation.stream.window;

import com.campus.contest.recommendation.stream.event.BehaviorEvent;
import org.junit.jupiter.api.Test;

import java.time.OffsetDateTime;

import static org.assertj.core.api.Assertions.assertThat;

class ViewCountAggregateTest {

    @Test
    void incrementsOneViewPerAcceptedEvent() {
        ViewCountAggregate aggregate = new ViewCountAggregate();
        Long count = aggregate.createAccumulator();
        BehaviorEvent event = new BehaviorEvent(
                "event-1",
                "101",
                "contest_detail_viewed",
                "8",
                "MATH_MODELING",
                OffsetDateTime.parse("2026-11-28T18:30:00+08:00")
        );

        count = aggregate.add(event, count);
        count = aggregate.add(event, count);

        assertThat(aggregate.getResult(count)).isEqualTo(2L);
        assertThat(aggregate.merge(2L, 3L)).isEqualTo(5L);
    }
}
