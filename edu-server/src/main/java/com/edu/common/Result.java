package com.edu.common;

import lombok.Data;

/** 统一响应体 */
@Data
public class Result<T> {
    private int code;      // 200成功 401未登录 403无权限 500业务异常
    private String msg;
    private T data;

    public static <T> Result<T> ok() { return build(200, "操作成功", null); }
    public static <T> Result<T> ok(T data) { return build(200, "操作成功", data); }
    public static <T> Result<T> error(String msg) { return build(500, msg, null); }
    public static <T> Result<T> build(int code, String msg, T data) {
        Result<T> r = new Result<>();
        r.code = code; r.msg = msg; r.data = data;
        return r;
    }
}
