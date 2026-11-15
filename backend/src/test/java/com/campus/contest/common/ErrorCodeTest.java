package com.campus.contest.common;

import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;

import java.util.Arrays;
import java.util.Set;
import java.util.stream.Collectors;

import static org.assertj.core.api.Assertions.assertThat;

class ErrorCodeTest {

    @Test
    void keepsExistingCodesAndAddsAuthDependencyCode() {
        Set<Integer> codes = Arrays.stream(ErrorCode.values())
                .map(ErrorCode::getCode)
                .collect(Collectors.toSet());

        assertThat(codes).contains(
                0, 10001, 10002, 10003,
                20001, 20002, 20003, 20004, 20005, 20006, 20007, 20008, 20009,
                30001, 30002, 30003, 30004,
                40001, 40002, 40404, 40416,
                50000, 50001, 50002, 50003
        );
        assertThat(ErrorCode.AI_SERVICE_UNAVAILABLE.getCode()).isEqualTo(50001);
        assertThat(ErrorCode.OBJECT_STORAGE_UNAVAILABLE.getCode()).isEqualTo(50002);
        assertThat(ErrorCode.AUTH_IDEMPOTENCY_UNAVAILABLE.getCode()).isEqualTo(50003);
        assertThat(ErrorCode.AUTH_IDEMPOTENCY_UNAVAILABLE.getHttpStatus())
                .isEqualTo(HttpStatus.SERVICE_UNAVAILABLE);
        assertThat(ErrorCode.AUTH_IDEMPOTENCY_UNAVAILABLE.isRetryable()).isTrue();
    }

    @Test
    void mapsExistingCodesToHttpStatuses() {
        assertThat(ErrorCode.resolveHttpStatus(10001)).isEqualTo(HttpStatus.BAD_REQUEST);
        assertThat(ErrorCode.resolveHttpStatus(20006)).isEqualTo(HttpStatus.BAD_REQUEST);
        assertThat(ErrorCode.resolveHttpStatus(20001)).isEqualTo(HttpStatus.CONFLICT);
        assertThat(ErrorCode.resolveHttpStatus(30002)).isEqualTo(HttpStatus.UNAUTHORIZED);
        assertThat(ErrorCode.resolveHttpStatus(30004)).isEqualTo(HttpStatus.FORBIDDEN);
        assertThat(ErrorCode.resolveHttpStatus(40001)).isEqualTo(HttpStatus.TOO_MANY_REQUESTS);
        assertThat(ErrorCode.resolveHttpStatus(40002)).isEqualTo(HttpStatus.CONFLICT);
        assertThat(ErrorCode.resolveHttpStatus(40416))
                .isEqualTo(HttpStatus.REQUESTED_RANGE_NOT_SATISFIABLE);
        assertThat(ErrorCode.resolveHttpStatus(50003)).isEqualTo(HttpStatus.SERVICE_UNAVAILABLE);
    }
}
