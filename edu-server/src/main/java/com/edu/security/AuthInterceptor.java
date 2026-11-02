package com.edu.security;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.edu.common.exception.BizException;
import com.edu.entity.*;
import com.edu.mapper.*;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import org.springframework.web.method.HandlerMethod;
import org.springframework.web.servlet.HandlerInterceptor;

import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import java.util.ArrayList;
import java.util.List;
import java.util.stream.Collectors;

/**
 * 登录+权限拦截：校验JWT -> 加载用户/角色/权限 -> 注入AuthContext -> 校验@RequiresPerm
 */
@Component
@RequiredArgsConstructor
public class AuthInterceptor implements HandlerInterceptor {

    private final JwtUtil jwtUtil;
    private final SysUserMapper userMapper;
    private final SysUserRoleMapper userRoleMapper;
    private final SysRoleMapper roleMapper;
    private final SysRoleMenuMapper roleMenuMapper;
    private final SysMenuMapper menuMapper;
    private final EduTeacherMapper teacherMapper;
    private final EduStudentMapper studentMapper;
    private final SysTenantMapper tenantMapper;

    @Override
    public boolean preHandle(HttpServletRequest request, HttpServletResponse response, Object handler) {
        if (!(handler instanceof HandlerMethod)) return true;

        String token = request.getHeader("Authorization");
        if (token != null && token.startsWith("Bearer ")) token = token.substring(7);
        Long userId = token == null ? null : jwtUtil.parseToken(token);
        if (userId == null) throw new BizException("401");

        LoginUser lu = loadUser(userId);

        // ---- 租户状态实时校验：禁用/过期的租户，其所有用户立即踢下线（防旧token残留）----
        if (lu.getTenantId() != null && lu.getTenantId() > 0) {
            SysTenant tenant = tenantMapper.selectById(lu.getTenantId());
            if (tenant == null || !tenant.usable())
                throw new BizException("401", "租户已停用或已过期，请联系平台");
        }

        // ---- 平台管理员切换目标租户视角：X-Target-Tenant 仅平台身份生效（租户用户传了也忽略）----
        String target = request.getHeader("X-Target-Tenant");
        if (target != null && lu.isPlatformAdmin()) {
            try { lu.setTenantId(Long.valueOf(target.trim())); } catch (NumberFormatException ignore) { }
        }

        try {
            HandlerMethod hm = (HandlerMethod) handler;
            RequiresPerm rp = hm.getMethodAnnotation(RequiresPerm.class);
            if (rp == null) rp = hm.getBeanType().getAnnotation(RequiresPerm.class);
            if (rp != null && !lu.hasPerm(rp.value())) throw new BizException("403");
            // 上下文只在成功路径写入：preHandle 抛异常时 Spring 不会回调本拦截器的
            // afterCompletion，提前 set 会把租户信息泄漏到线程池（污染后续请求，含登录）
            AuthContext.set(lu);
            return true;
        } catch (Exception e) {
            AuthContext.clear();
            throw e;
        }
    }

    @Override
    public void afterCompletion(HttpServletRequest req, HttpServletResponse resp, Object h, Exception e) {
        AuthContext.clear();
    }

    private LoginUser loadUser(Long userId) {
        SysUser user = userMapper.selectById(userId);
        if (user == null || user.getStatus() != 1) throw new BizException("401");

        LoginUser lu = new LoginUser();
        lu.setId(user.getId());
        lu.setTenantId(user.getTenantId());
        lu.setOrgId(user.getOrgId());
        lu.setUsername(user.getUsername());
        lu.setRealName(user.getRealName());
        lu.setUserType(user.getUserType());

        // 角色 -> 菜单 -> 权限标识
        List<Long> roleIds = userRoleMapper.selectList(
                        new LambdaQueryWrapper<SysUserRole>().eq(SysUserRole::getUserId, userId))
                .stream().map(SysUserRole::getRoleId).collect(Collectors.toList());
        List<String> perms = new ArrayList<>();
        if (!roleIds.isEmpty()) {
            List<Long> menuIds = roleMenuMapper.selectList(
                            new LambdaQueryWrapper<SysRoleMenu>().in(SysRoleMenu::getRoleId, roleIds))
                    .stream().map(SysRoleMenu::getMenuId).collect(Collectors.toList());
            if (!menuIds.isEmpty()) {
                menuMapper.selectBatchIds(menuIds).forEach(m -> {
                    if (m.getPerm() != null) perms.add(m.getPerm());
                });
            }
        }
        lu.setPerms(perps(perms));

        // 教师档案/学生档案关联：按档案行存在性解析，与用户类型解耦
        // （教务/校长等账号可通过用户管理"兼任教师"建档，登录即挂 teacherId）
        EduTeacher t = teacherMapper.selectOne(
                new LambdaQueryWrapper<EduTeacher>().eq(EduTeacher::getUserId, userId).last("LIMIT 1"));
        if (t != null) lu.setTeacherId(t.getId());
        EduStudent s = studentMapper.selectOne(
                new LambdaQueryWrapper<EduStudent>().eq(EduStudent::getUserId, userId).last("LIMIT 1"));
        if (s != null) lu.setStudentId(s.getId());
        return lu;
    }

    private java.util.Set<String> perps(List<String> list) {
        return new java.util.HashSet<>(list);
    }
}
