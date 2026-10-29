package com.edu.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.edu.common.exception.BizException;
import com.edu.dto.LoginVO;
import com.edu.dto.MenuNode;
import com.edu.entity.*;
import com.edu.mapper.*;
import com.edu.security.JwtUtil;
import com.edu.security.LoginUser;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.util.DigestUtils;

import java.time.LocalDateTime;
import java.util.*;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class AuthService {

    public static final String SALT = "edu";

    private final SysUserMapper userMapper;
    private final SysUserRoleMapper userRoleMapper;
    private final SysRoleMapper roleMapper;
    private final SysRoleMenuMapper roleMenuMapper;
    private final SysMenuMapper menuMapper;
    private final JwtUtil jwtUtil;
    private final CaptchaService captchaService;

    public LoginVO login(String username, String password, String captchaKey, String captchaCode) {
        // 服务端验证码可选：前端使用本地Canvas验证码时不传key，直接跳过
        if (captchaKey != null && !captchaKey.isEmpty()) {
            captchaService.verify(captchaKey, captchaCode);
        }
        SysUser user = userMapper.selectOne(
                new LambdaQueryWrapper<SysUser>().eq(SysUser::getUsername, username));
        String md5 = DigestUtils.md5DigestAsHex((password + SALT).getBytes());
        if (user == null || !md5.equals(user.getPassword())) throw new BizException("用户名或密码错误");
        if (user.getStatus() != 1) throw new BizException("账号已被停用");

        user.setLastLoginTime(LocalDateTime.now());
        userMapper.updateById(user);

        LoginVO vo = new LoginVO();
        vo.setToken(jwtUtil.createToken(user.getId()));
        vo.setUser(buildLoginUser(user));
        vo.setPerms(vo.getUser().getPerms());
        vo.setMenus(buildMenus(user.getId(), user.getUserType()));
        return vo;
    }

    /** 当前用户可见菜单树 */
    public List<MenuNode> buildMenus(Long userId, Integer userType) {
        List<SysMenu> menus;
        if (userType != null && userType == 1) {
            menus = menuMapper.selectList(new LambdaQueryWrapper<SysMenu>()
                    .in(SysMenu::getType, "M", "C").orderByAsc(SysMenu::getSort));
        } else {
            List<Long> roleIds = userRoleMapper.selectList(
                            new LambdaQueryWrapper<SysUserRole>().eq(SysUserRole::getUserId, userId))
                    .stream().map(SysUserRole::getRoleId).collect(Collectors.toList());
            if (roleIds.isEmpty()) return Collections.emptyList();
            List<Long> menuIds = roleMenuMapper.selectList(
                            new LambdaQueryWrapper<SysRoleMenu>().in(SysRoleMenu::getRoleId, roleIds))
                    .stream().map(SysRoleMenu::getMenuId).collect(Collectors.toList());
            if (menuIds.isEmpty()) return Collections.emptyList();
            menus = menuMapper.selectList(new LambdaQueryWrapper<SysMenu>()
                    .in(SysMenu::getId, menuIds)
                    .in(SysMenu::getType, "M", "C")
                    .orderByAsc(SysMenu::getSort));
        }
        return toTree(menus, 0L);
    }

    private List<MenuNode> toTree(List<SysMenu> menus, Long parentId) {
        List<MenuNode> nodes = new ArrayList<>();
        for (SysMenu m : menus) {
            if (parentId.equals(m.getParentId())) {
                MenuNode node = new MenuNode();
                org.springframework.beans.BeanUtils.copyProperties(m, node);
                node.setChildren(toTree(menus, m.getId()));
                nodes.add(node);
            }
        }
        return nodes;
    }

    /** 供拦截器复用的用户构建逻辑见 AuthInterceptor，此处登录时同步返回权限集合 */
    private LoginUser buildLoginUser(SysUser user) {
        LoginUser lu = new LoginUser();
        lu.setId(user.getId());
        lu.setTenantId(user.getTenantId());
        lu.setOrgId(user.getOrgId());
        lu.setUsername(user.getUsername());
        lu.setRealName(user.getRealName());
        lu.setUserType(user.getUserType());
        List<Long> roleIds = userRoleMapper.selectList(
                        new LambdaQueryWrapper<SysUserRole>().eq(SysUserRole::getUserId, user.getId()))
                .stream().map(SysUserRole::getRoleId).collect(Collectors.toList());
        Set<String> perms = new HashSet<>();
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
        lu.setPerms(perms);
        return lu;
    }
}
