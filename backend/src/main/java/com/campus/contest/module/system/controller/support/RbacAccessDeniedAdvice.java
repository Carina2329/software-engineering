package com.campus.contest.module.system.controller.support;

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
 * {@code @PreAuthorize} 拒绝访问时的统一响应。
 *
 * 背景：公共 GlobalExceptionHandler 里的 {@code @ExceptionHandler(Exception.class)}
 * 会先命中方法级鉴权抛出的 AccessDeniedException，把它变成 500，
 * 导致「非管理员访问管理接口返回 403」无法成立。
 *
 * 本类只覆盖 system 与 work 两个 Controller 包，不修改公共异常处理器：
 * 1. 作用范围仅限本分支新增/修改的接口；
 * 2. 返回码仍是全局统一的 Result + 30003；
 * 3. 后续若公共 GlobalExceptionHandler 补上 AccessDeniedException 处理，可直接删除本类。
 */
@RestControllerAdvice(basePackages = {
        "com.campus.contest.module.system.controller",
        "com.campus.contest.module.work.controller"
})
@Order(Ordered.HIGHEST_PRECEDENCE)
@Slf4j
public class RbacAccessDeniedAdvice {

    @ExceptionHandler(AccessDeniedException.class)
    public ResponseEntity<Result<Void>> handleAccessDenied(AccessDeniedException exception) {
        log.warn("方法级权限校验未通过: {}", exception.getMessage());
        return ResponseEntity
                .status(ErrorCode.FUNCTION_FORBIDDEN.getHttpStatus())
                .body(Result.fail(ErrorCode.FUNCTION_FORBIDDEN));
    }
}
