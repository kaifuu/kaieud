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
import org.springframework.util.StringUtils;

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
    private final SysTenantMapper tenantMapper;
    private final JwtUtil jwtUtil;
    private final CaptchaService captchaService;

    public LoginVO login(String tenantCode, String username, String password, String captchaKey, String captchaCode) {
        // 服务端验证码可选：前端使用本地Canvas验证码时不传key，直接跳过
        if (captchaKey != null && !captchaKey.isEmpty()) {
            captchaService.verify(captchaKey, captchaCode);
        }
        // ---- 企业域解析：编码为空/PLATFORM=平台运营；否则按编码定位企业租户 ----
        Long tenantId = 0L;
        if (StringUtils.hasText(tenantCode) && !"PLATFORM".equalsIgnoreCase(tenantCode.trim())) {
            SysTenant tenant = tenantMapper.selectOne(new LambdaQueryWrapper<SysTenant>()
                    .eq(SysTenant::getCode, tenantCode.trim()));
            if (tenant == null) throw new BizException("企业编码不存在");
            if (!tenant.usable()) throw new BizException("该企业已停用或已过期，请联系平台");
            tenantId = tenant.getId();
        }
        // 用户按（租户, 用户名）查找——用户名仅租户内唯一，每个企业都可以有自己的 admin
        SysUser user = userMapper.selectOne(new LambdaQueryWrapper<SysUser>()
                .eq(SysUser::getTenantId, tenantId)
                .eq(SysUser::getUsername, username));
        String md5 = DigestUtils.md5DigestAsHex((password + SALT).getBytes());
        if (user == null || !md5.equals(user.getPassword()))
            throw new BizException("用户名或密码错误（请确认企业编码正确）");
        if (user.getStatus() != 1) throw new BizException("账号已被停用");
        // 租户可用性校验：禁用/过期的租户禁止其所有用户登录（tenant_id=0 平台跳过）
        if (user.getTenantId() != null && user.getTenantId() > 0) {
            SysTenant tenant = tenantMapper.selectById(user.getTenantId());
            if (tenant == null || !tenant.usable())
                throw new BizException("租户已停用或已过期，禁止登录");
        }

        user.setLastLoginTime(LocalDateTime.now());
        userMapper.updateById(user);

        LoginVO vo = new LoginVO();
        vo.setToken(jwtUtil.createToken(user.getId()));
        vo.setUser(buildLoginUser(user));
        vo.setPerms(vo.getUser().getPerms());
        vo.setMenus(buildMenus(user.getId(), user.getUserType(), user.getTenantId()));
        return vo;
    }

    /**
     * 当前用户可见菜单树（租户数据隔离，tenant_id=0 为平台）：
     * 平台运营只看平台专属菜单（scope=1，如 租户管理/平台首页），不进任何企业内部；
     * 企业租户看本租户全套菜单（创建企业时复制的模板 + 自建）。
     * 注意：登录流程调用时尚无请求上下文（拦截器放行），显式条件必须保留。
     */
    public List<MenuNode> buildMenus(Long userId, Integer userType, Long tenantId) {
        boolean platform = tenantId == null || tenantId == 0;

        // 平台运营：平台专属菜单 + 其祖先目录
        if (platform) {
            List<SysMenu> own = menuMapper.selectList(new LambdaQueryWrapper<SysMenu>()
                    .eq(SysMenu::getTenantId, 0L).eq(SysMenu::getScope, 1)
                    .in(SysMenu::getType, "M", "C").orderByAsc(SysMenu::getSort));
            if (own.isEmpty()) return Collections.emptyList();
            // 补齐祖先目录链，保证树形完整
            java.util.Set<Long> need = new java.util.HashSet<>();
            java.util.Map<Long, SysMenu> all = new java.util.HashMap<>();
            for (SysMenu m : menuMapper.selectList(new LambdaQueryWrapper<SysMenu>()
                    .eq(SysMenu::getTenantId, 0L))) all.put(m.getId(), m);
            for (SysMenu m : own) {
                Long pid = m.getParentId();
                while (pid != null && pid != 0 && all.containsKey(pid) && need.add(pid)) {
                    pid = all.get(pid).getParentId();
                }
            }
            List<SysMenu> menus = new ArrayList<>();
            for (SysMenu m : own) menus.add(m);
            for (Long id : need) {
                SysMenu p = all.get(id);
                if ("M".equals(p.getType())) menus.add(p);
            }
            menus.sort(java.util.Comparator.comparing(SysMenu::getSort,
                    java.util.Comparator.nullsLast(java.util.Comparator.naturalOrder())));
            return toTree(menus, 0L);
        }

        // 企业租户：管理员（user_type=1）看本租户全部菜单；普通用户看角色菜单
        LambdaQueryWrapper<SysMenu> base = new LambdaQueryWrapper<SysMenu>()
                .eq(SysMenu::getTenantId, tenantId)
                .in(SysMenu::getType, "M", "C")
                .orderByAsc(SysMenu::getSort);
        List<SysMenu> menus;
        if (userType != null && userType == 1) {
            menus = menuMapper.selectList(base);
        } else {
            List<Long> roleIds = userRoleMapper.selectList(
                            new LambdaQueryWrapper<SysUserRole>().eq(SysUserRole::getUserId, userId))
                    .stream().map(SysUserRole::getRoleId).collect(Collectors.toList());
            if (roleIds.isEmpty()) return Collections.emptyList();
            List<Long> menuIds = roleMenuMapper.selectList(
                            new LambdaQueryWrapper<SysRoleMenu>().in(SysRoleMenu::getRoleId, roleIds))
                    .stream().map(SysRoleMenu::getMenuId).collect(Collectors.toList());
            if (menuIds.isEmpty()) return Collections.emptyList();
            menus = menuMapper.selectList(base.in(SysMenu::getId, menuIds));
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
