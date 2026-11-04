package com.campus.contest.common;

import org.springframework.http.HttpStatus;

import java.util.Arrays;
import java.util.Optional;

/**
 * 项目统一数字业务错误码。
 *
 * 既有错误码的含义和 HTTP 映射不得改变；认证与幂等依赖新增 50003。
 */
public enum ErrorCode {

    SUCCESS(0, "成功", HttpStatus.OK, false),

    INVALID_PARAM(10001, "参数校验失败", HttpStatus.BAD_REQUEST, false),
    INVALID_ENUM_VALUE(10002, "枚举值非法", HttpStatus.BAD_REQUEST, false),
    MALFORMED_REQUEST_BODY(10003, "请求体格式错误", HttpStatus.BAD_REQUEST, false),

    BUSINESS_CONFLICT(20001, "业务状态冲突", HttpStatus.CONFLICT, false),
    DUPLICATE_RELATION(20002, "重复的成员、账号或关联", HttpStatus.CONFLICT, false),
    TOPIC_CAPACITY_FULL(20003, "赛题容量已满", HttpStatus.CONFLICT, false),
    BUSINESS_DEADLINE_PASSED(20004, "已超过业务截止时间", HttpStatus.CONFLICT, false),
    INVALID_STATE_TRANSITION(20005, "非法状态流转", HttpStatus.CONFLICT, false),
    INVALID_RUBRIC_WEIGHT(20006, "评分权重不合法", HttpStatus.BAD_REQUEST, false),
    REVIEWER_CONFLICT(20007, "评委与队伍存在利益冲突", HttpStatus.CONFLICT, false),
    DUPLICATE_SUBMISSION(20008, "重复提交或异议", HttpStatus.CONFLICT, false),
    REVIEW_NOT_COMPLETED(20009, "评审尚未全部完成", HttpStatus.CONFLICT, true),

    UNAUTHENTICATED(30001, "未登录或令牌缺失", HttpStatus.UNAUTHORIZED, false),
    TOKEN_EXPIRED_OR_REVOKED(30002, "令牌过期或已吊销", HttpStatus.UNAUTHORIZED, false),
    FUNCTION_FORBIDDEN(30003, "功能权限不足", HttpStatus.FORBIDDEN, false),
    DATA_FORBIDDEN(30004, "数据权限或归属校验失败", HttpStatus.FORBIDDEN, false),

    RATE_LIMITED(40001, "请求频率超限", HttpStatus.TOO_MANY_REQUESTS, true),
    IDEMPOTENCY_CONFLICT(40002, "重复请求或重放被拒绝", HttpStatus.CONFLICT, true),
    RESOURCE_NOT_FOUND(40404, "资源不存在或对当前用户不可见", HttpStatus.NOT_FOUND, false),
    RANGE_NOT_SATISFIABLE(40416, "Range 范围不合法", HttpStatus.REQUESTED_RANGE_NOT_SATISFIABLE, false),

    INTERNAL_ERROR(50000, "系统内部错误", HttpStatus.INTERNAL_SERVER_ERROR, true),
    AI_SERVICE_UNAVAILABLE(50001, "AI 服务暂不可用", HttpStatus.SERVICE_UNAVAILABLE, true),
    OBJECT_STORAGE_UNAVAILABLE(50002, "对象存储暂不可用", HttpStatus.SERVICE_UNAVAILABLE, true),
    AUTH_IDEMPOTENCY_UNAVAILABLE(
            50003,
            "认证或幂等依赖暂不可用",
            HttpStatus.SERVICE_UNAVAILABLE,
            true
    );

    private final int code;
    private final String message;
    private final HttpStatus httpStatus;
    private final boolean retryable;

    ErrorCode(int code, String message, HttpStatus httpStatus, boolean retryable) {
        this.code = code;
        this.message = message;
        this.httpStatus = httpStatus;
        this.retryable = retryable;
    }

    public int getCode() {
        return code;
    }

    public String getMessage() {
        return message;
    }

    public HttpStatus getHttpStatus() {
        return httpStatus;
    }

    public boolean isRetryable() {
        return retryable;
    }

    public static Optional<ErrorCode> fromCode(int code) {
        return Arrays.stream(values()).filter(errorCode -> errorCode.code == code).findFirst();
    }

    /**
     * 兼容现有代码使用的纯 HTTP 风格业务码，并保持既有业务码映射。
     */
    public static HttpStatus resolveHttpStatus(int code) {
        return fromCode(code)
                .map(ErrorCode::getHttpStatus)
                .orElseGet(() -> switch (code) {
                    case 400 -> HttpStatus.BAD_REQUEST;
                    case 401 -> HttpStatus.UNAUTHORIZED;
                    case 403 -> HttpStatus.FORBIDDEN;
                    case 404 -> HttpStatus.NOT_FOUND;
                    case 409 -> HttpStatus.CONFLICT;
                    case 416 -> HttpStatus.REQUESTED_RANGE_NOT_SATISFIABLE;
                    case 422 -> HttpStatus.UNPROCESSABLE_ENTITY;
                    case 429 -> HttpStatus.TOO_MANY_REQUESTS;
                    case 503 -> HttpStatus.SERVICE_UNAVAILABLE;
                    default -> code >= 50000
                            ? HttpStatus.INTERNAL_SERVER_ERROR
                            : HttpStatus.BAD_REQUEST;
                });
    }
}
