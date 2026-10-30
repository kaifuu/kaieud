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

    @Override
    public boolean preHandle(HttpServletRequest request, HttpServletResponse response, Object handler) {
        if (!(handler instanceof HandlerMethod)) return true;

        String token = request.getHeader("Authorization");
        if (token != null && token.startsWith("Bearer ")) token = token.substring(7);
        Long userId = token == null ? null : jwtUtil.parseToken(token);
        if (userId == null) throw new BizException("401");

        LoginUser lu = loadUser(userId);
        AuthContext.set(lu);

        HandlerMethod hm = (HandlerMethod) handler;
        RequiresPerm rp = hm.getMethodAnnotation(RequiresPerm.class);
        if (rp == null) rp = hm.getBeanType().getAnnotation(RequiresPerm.class);
        if (rp != null && !lu.hasPerm(rp.value())) throw new BizException("403");
        return true;
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
