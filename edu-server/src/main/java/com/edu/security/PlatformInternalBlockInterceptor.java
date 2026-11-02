package com.edu.security;

import com.edu.common.exception.BizException;
import org.springframework.stereotype.Component;
import org.springframework.web.servlet.HandlerInterceptor;

import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;

/**
 * 平台运营与企业内部数据的边界拦截器：
 * 平台管理员（未切换租户视角时）禁止对企业内部的 用户/角色/组织/菜单 执行写操作——
 * 企业的组织/用户/角色/菜单由该企业的 admin 自行管理，平台只管租户。
 * 需要代企业排障时：平台管理员带 X-Target-Tenant 切换到该企业视角后即可正常操作。
 */
@Component
public class PlatformInternalBlockInterceptor implements HandlerInterceptor {

    @Override
    public boolean preHandle(HttpServletRequest request, HttpServletResponse response, Object handler) {
        String method = request.getMethod();
        // 只拦写操作（GET 保留给平台做只读诊断）
        if (!"POST".equalsIgnoreCase(method) && !"PUT".equalsIgnoreCase(method)
                && !"DELETE".equalsIgnoreCase(method)) return true;

        String uri = request.getRequestURI();
        boolean internal = uri.startsWith("/sys/user/") || uri.equals("/sys/user")
                || uri.startsWith("/sys/role/") || uri.equals("/sys/role")
                || uri.startsWith("/sys/org/") || uri.equals("/sys/org")
                || uri.startsWith("/sys/menu/") || uri.equals("/sys/menu");
        if (!internal) return true;
        // 平台改自己头像放行（个人信息抽屉）
        if (uri.contains("/avatar-upload")) return true;

        LoginUser lu = AuthContext.get();
        if (lu != null && lu.isPlatformAdmin())
            throw new BizException("403", "平台运营不直接操作企业内部数据，请由该企业管理员（admin）操作");
        return true;
    }
}
