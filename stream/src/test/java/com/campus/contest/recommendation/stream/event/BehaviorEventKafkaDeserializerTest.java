package com.campus.contest.recommendation.stream.event;

import org.junit.jupiter.api.Test;

import java.nio.charset.StandardCharsets;

import static org.assertj.core.api.Assertions.assertThat;

class BehaviorEventKafkaDeserializerTest {

    private final BehaviorEventKafkaDeserializer deserializer =
            new BehaviorEventKafkaDeserializer();

    @Test
    void parsesServerCompletedSnakeCaseEvent() throws Exception {
        BehaviorEvent event = deserialize("{"
                + "\"event_id\":\" event-1 \","
                + "\"user_id\":101,"
                + "\"event_type\":\"contest_detail_viewed\","
                + "\"contest_id\":\"8\","
                + "\"category\":\" MATH_MODELING \","
                + "\"occurred_at\":\"2026-11-28T18:30:00+08:00\","
                + "\"unexpected\":\"ignored\""
                + "}");

        assertThat(event).isNotNull();
        assertThat(event.getEventId()).isEqualTo("event-1");
        assertThat(event.getUserId()).isEqualTo("101");
        assertThat(event.getContestId()).isEqualTo("8");
        assertThat(event.getCategory()).isEqualTo("MATH_MODELING");
        assertThat(event.getOccurredAt().toInstant().toString())
                .isEqualTo("2026-11-28T10:30:00Z");
    }

    @Test
    void dropsUnsupportedEventType() throws Exception {
        assertThat(deserialize(eventJson(
                "event-2",
                "101",
                "contest_clicked",
                "MATH_MODELING"
        ))).isNull();
    }

    @Test
    void dropsMalformedJsonWithoutFailingTheJob() throws Exception {
        assertThat(deserialize("{")).isNull();
    }

    @Test
    void interpretsOffsetlessServerTimeAsShanghaiTime() throws Exception {
        BehaviorEvent event = deserialize(eventJson(
                "event-local",
                "101",
                "contest_detail_viewed",
                "MATH_MODELING",
                "2026-11-28T18:30:00"
        ));

        assertThat(event).isNotNull();
        assertThat(event.getOccurredAt().toInstant().toString())
                .isEqualTo("2026-11-28T10:30:00Z");
    }

    @Test
    void dropsEventWithoutCategory() throws Exception {
        assertThat(deserialize("{"
                + "\"event_id\":\"event-3\","
                + "\"user_id\":\"101\","
                + "\"event_type\":\"contest_detail_viewed\","
                + "\"contest_id\":\"8\","
                + "\"occurred_at\":\"2026-11-28T18:30:00+08:00\""
                + "}")).isNull();
    }

    private BehaviorEvent deserialize(String json) throws Exception {
        return deserializer.deserializeValue(json.getBytes(StandardCharsets.UTF_8));
    }

    private String eventJson(
            String eventId,
            String userId,
            String eventType,
            String category
    ) {
        return eventJson(
                eventId,
                userId,
                eventType,
                category,
                "2026-11-28T18:30:00+08:00"
        );
    }

    private String eventJson(
            String eventId,
            String userId,
            String eventType,
            String category,
            String occurredAt
    ) {
        return "{"
                + "\"event_id\":\"" + eventId + "\","
                + "\"user_id\":\"" + userId + "\","
                + "\"event_type\":\"" + eventType + "\","
                + "\"contest_id\":\"8\","
                + "\"category\":\"" + category + "\","
                + "\"occurred_at\":\"" + occurredAt + "\""
                + "}";
    }
}
