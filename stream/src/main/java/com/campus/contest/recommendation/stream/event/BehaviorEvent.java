package com.campus.contest.recommendation.stream.event;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.databind.annotation.JsonDeserialize;

import java.io.Serializable;
import java.time.OffsetDateTime;

@JsonIgnoreProperties(ignoreUnknown = true)
public class BehaviorEvent implements Serializable {

    private static final long serialVersionUID = 1L;

    public static final String CONTEST_DETAIL_VIEWED = "contest_detail_viewed";

    @JsonProperty("event_id")
    private String eventId;

    @JsonProperty("user_id")
    private String userId;

    @JsonProperty("event_type")
    private String eventType;

    @JsonProperty("contest_id")
    private String contestId;

    @JsonProperty("category")
    private String category;

    @JsonProperty("occurred_at")
    @JsonDeserialize(using = BusinessDateTimeDeserializer.class)
    private OffsetDateTime occurredAt;

    public BehaviorEvent() {
    }

    public BehaviorEvent(
            String eventId,
            String userId,
            String eventType,
            String contestId,
            String category,
            OffsetDateTime occurredAt
    ) {
        this.eventId = eventId;
        this.userId = userId;
        this.eventType = eventType;
        this.contestId = contestId;
        this.category = category;
        this.occurredAt = occurredAt;
    }

    public boolean isValid() {
        return hasText(eventId)
                && hasText(userId)
                && CONTEST_DETAIL_VIEWED.equals(eventType)
                && hasText(contestId)
                && hasText(category)
                && occurredAt != null;
    }

    public long occurredAtMillis() {
        return occurredAt.toInstant().toEpochMilli();
    }

    private boolean hasText(String value) {
        return value != null && !value.isBlank();
    }

    public String getEventId() {
        return eventId;
    }

    public void setEventId(String eventId) {
        this.eventId = eventId;
    }

    public String getUserId() {
        return userId;
    }

    public void setUserId(String userId) {
        this.userId = userId;
    }

    public String getEventType() {
        return eventType;
    }

    public void setEventType(String eventType) {
        this.eventType = eventType;
    }

    public String getContestId() {
        return contestId;
    }

    public void setContestId(String contestId) {
        this.contestId = contestId;
    }

    public String getCategory() {
        return category;
    }

    public void setCategory(String category) {
        this.category = category;
    }

    public OffsetDateTime getOccurredAt() {
        return occurredAt;
    }

    public void setOccurredAt(OffsetDateTime occurredAt) {
        this.occurredAt = occurredAt;
    }
}
