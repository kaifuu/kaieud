package com.edu.common.exception;

import com.edu.common.Result;
import lombok.extern.slf4j.Slf4j;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

@Slf4j
@RestControllerAdvice
public class GlobalExceptionHandler {

    @ExceptionHandler(BizException.class)
    public Result<Void> biz(BizException e) {
        // 拦截器用 "401"/"403" 作为未登录/无权限信号
        if ("401".equals(e.getMessage())) return Result.build(401, "未登录或登录已过期", null);
        if ("403".equals(e.getMessage())) return Result.build(403, "没有操作权限", null);
        return Result.error(e.getMessage());
    }

    @ExceptionHandler(Exception.class)
    public Result<Void> other(Exception e) {
        log.error("系统异常", e);
        return Result.error("系统异常：" + e.getMessage());
    }
}
