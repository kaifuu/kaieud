package com.edu.security;

/** 登录用户ThreadLocal上下文 */
public class AuthContext {
    private static final ThreadLocal<LoginUser> HOLDER = new ThreadLocal<>();

    public static void set(LoginUser user) { HOLDER.set(user); }
    public static LoginUser get() { return HOLDER.get(); }
    public static void clear() { HOLDER.remove(); }
}
