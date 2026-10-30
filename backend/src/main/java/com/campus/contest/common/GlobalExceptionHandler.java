package com.campus.contest.common;

import lombok.extern.slf4j.Slf4j;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

/**
 * 全局异常处理：任何未捕获异常统一转成 Result 格式返回，前端无需处理多种错误结构。
 */
@Slf4j
@RestControllerAdvice
public class GlobalExceptionHandler {

    @ExceptionHandler(BizException.class)
    public Result<Object> handleBiz(BizException e) {
        Result<Object> result = Result.fail(e.getCode(), e.getMessage());
        result.setData(e.getData());
        return result;
    }

    @ExceptionHandler(Exception.class)
    public Result<Void> handleOther(Exception e) {
        log.error("未预期的异常", e);
        return Result.fail(500, "服务器开小差了，请稍后再试");
    }
}
