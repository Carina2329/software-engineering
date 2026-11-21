package com.campus.contest.module.dashboard.controller.support;

import com.campus.contest.common.ErrorCode;
import com.campus.contest.common.Result;
import lombok.extern.slf4j.Slf4j;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

@RestControllerAdvice(basePackages = "com.campus.contest.module.dashboard.controller")
@Order(Ordered.HIGHEST_PRECEDENCE)
@Slf4j
public class DashboardAccessDeniedAdvice {

    @ExceptionHandler(AccessDeniedException.class)
    public ResponseEntity<Result<Void>> handleAccessDenied(AccessDeniedException exception) {
        log.warn("数字大屏方法级权限校验未通过: {}", exception.getMessage());
        return ResponseEntity
                .status(ErrorCode.FUNCTION_FORBIDDEN.getHttpStatus())
                .body(Result.fail(ErrorCode.FUNCTION_FORBIDDEN));
    }
}
