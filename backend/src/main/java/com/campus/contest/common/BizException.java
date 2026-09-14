package com.campus.contest.common;

/**
 * 业务异常：Service 层校验失败时抛出，由 GlobalExceptionHandler 统一转成 Result。
 * 用法：throw new BizException("报名已截止，不可创建队伍");
 */
public class BizException extends RuntimeException {

    private final Integer code;

    public BizException(String message) {
        this(400, message);
    }

    public BizException(Integer code, String message) {
        super(message);
        this.code = code;
    }

    public Integer getCode() {
        return code;
    }
}
