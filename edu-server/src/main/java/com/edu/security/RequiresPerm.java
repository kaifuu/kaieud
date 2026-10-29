package com.edu.security;

import java.lang.annotation.*;

/** 接口权限标识，命中用户权限集合或超级管理员方可访问 */
@Target({ElementType.METHOD, ElementType.TYPE})
@Retention(RetentionPolicy.RUNTIME)
@Documented
public @interface RequiresPerm {
    String value();
}
