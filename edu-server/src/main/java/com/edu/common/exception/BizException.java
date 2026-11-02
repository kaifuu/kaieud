package com.edu.common.exception;

import lombok.Getter;

/** 业务异常：可带业务码（如 401/403，全局异常处理器按码返回 HTTP 状态） */
@Getter
public class BizException extends RuntimeException {
    private final String code;

    public BizException(String msg) { this(null, msg); }

    public BizException(String code, String msg) {
        super(msg);
        this.code = code;
    }
}
