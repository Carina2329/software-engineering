package com.campus.contest.common;

import lombok.Data;

/**
 * 统一返回体：全项目所有接口必须返回 Result，禁止直接返回实体或裸字符串。
 */
@Data
public class Result<T> {

    /** 0 成功，非 0 失败 */
    private Integer code;

    private String message;

    private T data;

    public static <T> Result<T> ok(T data) {
        Result<T> r = new Result<>();
        r.code = 0;
        r.message = "success";
        r.data = data;
        return r;
    }

    public static <T> Result<T> ok() {
        return ok(null);
    }

    public static <T> Result<T> fail(Integer code, String message) {
        Result<T> r = new Result<>();
        r.code = code;
        r.message = message;
        return r;
    }
}
