package com.campus.contest.common;

import com.campus.contest.common.trace.TraceContext;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.multipart.support.MissingServletRequestPartException;

import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

class GlobalExceptionHandlerTest {

    @AfterEach
    void cleanTrace() {
        TraceContext.clear();
    }

    @Test
    void mapsAuthDependencyCodeToHttp503AndKeepsTraceId() {
        String traceId = TraceContext.startTrace();
        GlobalExceptionHandler handler = new GlobalExceptionHandler();

        ResponseEntity<Result<Object>> response = handler.handleBiz(
                new BizException(
                        ErrorCode.AUTH_IDEMPOTENCY_UNAVAILABLE.getCode(),
                        ErrorCode.AUTH_IDEMPOTENCY_UNAVAILABLE.getMessage(),
                        Map.of("retryable", true)
                )
        );

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.SERVICE_UNAVAILABLE);
        assertThat(response.getBody()).isNotNull();
        assertThat(response.getBody().getCode()).isEqualTo(50003);
        assertThat(response.getBody().getTraceId()).isEqualTo(traceId);
    }

    @Test
    void keepsLegacyHttpStyleBizCodesWorking() {
        GlobalExceptionHandler handler = new GlobalExceptionHandler();

        ResponseEntity<Result<Object>> response = handler.handleBiz(
                new BizException(422, "校验失败")
        );

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.UNPROCESSABLE_ENTITY);
        assertThat(response.getBody()).isNotNull();
        assertThat(response.getBody().getCode()).isEqualTo(422);
    }

    @Test
    void mapsMissingMultipartPartToExistingInvalidParameterCode() {
        GlobalExceptionHandler handler = new GlobalExceptionHandler();

        ResponseEntity<Result<Object>> response = handler.handleMissingOrInvalidParameter(
                new MissingServletRequestPartException("files")
        );

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
        assertThat(response.getBody()).isNotNull();
        assertThat(response.getBody().getCode()).isEqualTo(ErrorCode.INVALID_PARAM.getCode());
    }
}
