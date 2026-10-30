package com.campus.contest.common;

/**
 * 业务异常：Service 层校验失败时抛出，由 GlobalExceptionHandler 统一转成 Result。
 * 用法：throw new BizException("报名已截止，不可创建队伍");
 */
public class BizException extends RuntimeException {

    private final Integer code;
    private final Object data;

    public BizException(String message) {
        this(400, message);
    }

    public BizException(Integer code, String message) {
        this(code, message, null);
    }

    public BizException(Integer code, String message, Object data) {
        super(message);
        this.code = code;
        this.data = data;
    }

    public Integer getCode() {
        return code;
    }

    public Object getData() {
        return data;
    }
}
