package com.edu.common;

import java.lang.annotation.*;

/** 标记需要记录操作日志的接口 */
@Target(ElementType.METHOD)
@Retention(RetentionPolicy.RUNTIME)
@Documented
public @interface Log {
    /** 操作内容，如"新增用户" */
    String value();

    /** 日志类型：0操作日志 1登录日志 */
    int type() default 0;
}
