package com.campus.contest.common;

import com.campus.contest.common.trace.TraceContext;
import com.campus.contest.common.trace.TraceIdFilter;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;

import jakarta.servlet.http.HttpServletResponse;

import static org.assertj.core.api.Assertions.assertThat;

class TraceIdFilterTest {

    @AfterEach
    void cleanTrace() {
        TraceContext.clear();
    }

    @Test
    void addsTraceIdToResponseAndClearsContextAfterRequest() throws Exception {
        TraceIdFilter filter = new TraceIdFilter();
        MockHttpServletRequest request = new MockHttpServletRequest();
        MockHttpServletResponse response = new MockHttpServletResponse();

        filter.doFilter(request, response, (servletRequest, servletResponse) -> {
            assertThat(TraceContext.currentTraceId()).isNotBlank();
            assertThat(((HttpServletResponse) servletResponse).getHeader(TraceContext.HEADER_NAME))
                    .isNotBlank();
        });

        assertThat(TraceContext.currentTraceId()).isNull();
        assertThat(response.getHeader(TraceContext.HEADER_NAME)).hasSize(32);
    }
}
