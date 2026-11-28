package com.campus.contest.recommendation.stream.event;

import com.fasterxml.jackson.core.JsonParser;
import com.fasterxml.jackson.databind.DeserializationContext;
import com.fasterxml.jackson.databind.JsonDeserializer;

import java.io.IOException;
import java.time.LocalDateTime;
import java.time.OffsetDateTime;
import java.time.ZoneId;
import java.time.format.DateTimeParseException;

public class BusinessDateTimeDeserializer extends JsonDeserializer<OffsetDateTime> {

    private static final ZoneId BUSINESS_ZONE = ZoneId.of("Asia/Shanghai");

    @Override
    public OffsetDateTime deserialize(
            JsonParser parser,
            DeserializationContext context
    ) throws IOException {
        String value = parser.getValueAsString();
        if (value == null || value.isBlank()) {
            return null;
        }
        String normalized = value.trim();
        try {
            return OffsetDateTime.parse(normalized);
        } catch (DateTimeParseException ignored) {
            try {
                return LocalDateTime.parse(normalized).atZone(BUSINESS_ZONE).toOffsetDateTime();
            } catch (DateTimeParseException exception) {
                return (OffsetDateTime) context.handleWeirdStringValue(
                        OffsetDateTime.class,
                        normalized,
                        "occurred_at must be an ISO date-time"
                );
            }
        }
    }
}
