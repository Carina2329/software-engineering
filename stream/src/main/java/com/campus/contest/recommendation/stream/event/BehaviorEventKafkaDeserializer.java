package com.campus.contest.recommendation.stream.event;

import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import org.apache.flink.api.common.serialization.DeserializationSchema;
import org.apache.flink.api.common.typeinfo.TypeInformation;
import org.apache.flink.connector.kafka.source.reader.deserializer.KafkaRecordDeserializationSchema;
import org.apache.flink.util.Collector;
import org.apache.kafka.clients.consumer.ConsumerRecord;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;

public class BehaviorEventKafkaDeserializer
        implements KafkaRecordDeserializationSchema<BehaviorEvent> {

    private static final long serialVersionUID = 1L;
    private static final Logger LOG = LoggerFactory.getLogger(
            BehaviorEventKafkaDeserializer.class
    );

    private transient ObjectMapper objectMapper;

    @Override
    public void deserialize(
            ConsumerRecord<byte[], byte[]> record,
            Collector<BehaviorEvent> out
    ) throws IOException {
        BehaviorEvent event = deserializeValue(record.value());
        if (event != null) {
            out.collect(event);
        }
    }

    BehaviorEvent deserializeValue(byte[] payload) throws IOException {
        if (payload == null || payload.length == 0) {
            return null;
        }
        try {
            BehaviorEvent event = mapper().readValue(payload, BehaviorEvent.class);
            normalize(event);
            if (!event.isValid()) {
                LOG.warn("Dropping invalid recommendation behavior event");
                return null;
            }
            return event;
        } catch (IOException | RuntimeException exception) {
            LOG.warn("Dropping malformed recommendation behavior event", exception);
            return null;
        }
    }

    private void normalize(BehaviorEvent event) {
        if (event == null) {
            return;
        }
        event.setEventId(trimToNull(event.getEventId()));
        event.setUserId(trimToNull(event.getUserId()));
        event.setEventType(trimToNull(event.getEventType()));
        event.setContestId(trimToNull(event.getContestId()));
        event.setCategory(trimToNull(event.getCategory()));
    }

    private String trimToNull(String value) {
        return value == null || value.isBlank() ? null : value.trim();
    }

    private ObjectMapper mapper() {
        if (objectMapper == null) {
            objectMapper = new ObjectMapper()
                    .registerModule(new JavaTimeModule())
                    .disable(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES)
                    .disable(DeserializationFeature.ADJUST_DATES_TO_CONTEXT_TIME_ZONE);
        }
        return objectMapper;
    }

    @Override
    public TypeInformation<BehaviorEvent> getProducedType() {
        return TypeInformation.of(BehaviorEvent.class);
    }

    @Override
    public void open(DeserializationSchema.InitializationContext context) throws Exception {
        KafkaRecordDeserializationSchema.super.open(context);
        objectMapper = new ObjectMapper()
                .registerModule(new JavaTimeModule())
                .disable(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES)
                .disable(DeserializationFeature.ADJUST_DATES_TO_CONTEXT_TIME_ZONE);
    }
}
