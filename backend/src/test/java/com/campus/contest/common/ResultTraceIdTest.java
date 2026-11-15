package com.campus.contest.common;

import com.campus.contest.common.trace.TraceContext;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

class ResultTraceIdTest {

    @AfterEach
    void cleanTrace() {
        TraceContext.clear();
    }

    @Test
    void serializesTraceIdWithSnakeCase() throws Exception {
        String traceId = TraceContext.startTrace();

        Result<Map<String, String>> result = Result.ok(Map.of("value", "ok"));
        String json = new ObjectMapper().writeValueAsString(result);

        assertThat(json).contains("\"trace_id\":\"" + traceId + "\"");
        assertThat(result.getTraceId()).isEqualTo(traceId);
    }
}
