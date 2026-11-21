package com.campus.contest.module.contest.controller.support;

import com.campus.contest.common.ErrorCode;
import com.campus.contest.common.Result;
import lombok.extern.slf4j.Slf4j;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

/**
 * 将竞赛 Controller 方法级鉴权失败稳定转换为 HTTP 403 + 30003。
 *
 * 公共 GlobalExceptionHandler 的兜底 Exception 处理会把方法级 AccessDeniedException
 * 变成 500。本类仅作用于竞赛模块，不修改公共异常处理，也不复制权限判断。
 */
@RestControllerAdvice(basePackages = "com.campus.contest.module.contest.controller")
@Order(Ordered.HIGHEST_PRECEDENCE)
@Slf4j
public class ContestAccessDeniedAdvice {

    @ExceptionHandler(AccessDeniedException.class)
    public ResponseEntity<Result<Void>> handleAccessDenied(AccessDeniedException exception) {
        log.warn("竞赛模块方法级权限校验未通过: {}", exception.getMessage());
        return ResponseEntity
                .status(ErrorCode.FUNCTION_FORBIDDEN.getHttpStatus())
                .body(Result.fail(ErrorCode.FUNCTION_FORBIDDEN));
    }
}
