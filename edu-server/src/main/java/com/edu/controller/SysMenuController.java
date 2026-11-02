package com.edu.controller;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.edu.common.Log;
import com.edu.common.Result;
import com.edu.dto.MenuNode;
import com.edu.entity.SysMenu;
import com.edu.mapper.SysMenuMapper;
import com.edu.security.AuthContext;
import com.edu.security.LoginUser;
import com.edu.security.RequiresPerm;
import lombok.RequiredArgsConstructor;
import org.springframework.util.StringUtils;
import org.springframework.web.bind.annotation.*;

import java.util.ArrayList;
import java.util.List;

/**
 * 菜单管理（租户隔离）：平台账号维护平台模板菜单，租户账号维护本租户菜单；
 * 新增即归属当前租户；修改/删除校验归属，防止跨租户操作。
 */
@RestController
@RequestMapping("/sys/menu")
@RequiredArgsConstructor
@RequiresPerm("sys:menu")
public class SysMenuController {

    private final SysMenuMapper menuMapper;

    /**
     * 当前数据范围：租户用户由拦截器自动隔离（只看本租户菜单）；
     * 平台管理员按 tenantId 参数管理指定租户的菜单（缺省平台模板 tenant_id=0）
     */
    private LambdaQueryWrapper<SysMenu> scope(Long tenantId) {
        LambdaQueryWrapper<SysMenu> qw = new LambdaQueryWrapper<>();
        if (AuthContext.get().isPlatformAdmin())
            qw.eq(SysMenu::getTenantId, tenantId == null ? 0L : tenantId);
        return qw;
    }

    /** 校验菜单归属：平台管理员可管理任意租户菜单（含模板）；租户仅限本租户 */
    private SysMenu owned(Long id) {
        SysMenu m = menuMapper.selectById(id);
        if (m == null) throw new com.edu.common.exception.BizException("菜单不存在");
        LoginUser lu = AuthContext.get();
        if (!lu.isPlatformAdmin() && !lu.getTenantId().equals(m.getTenantId()))
            throw new com.edu.common.exception.BizException("不能操作其他租户的菜单");
        return m;
    }

    /** 菜单树：平台管理员可传 tenantId 查看指定租户的菜单（缺省平台模板）；租户自动隔离 */
    @GetMapping("/tree")
    public Result<List<MenuNode>> tree(@RequestParam(required = false) Long tenantId) {
        List<SysMenu> all = menuMapper.selectList(
                scope(tenantId).orderByAsc(SysMenu::getSort));
        return Result.ok(build(all, 0L));
    }

    static List<MenuNode> build(List<SysMenu> menus, Long parentId) {
        List<MenuNode> nodes = new ArrayList<>();
        for (SysMenu m : menus) {
            if (parentId.equals(m.getParentId())) {
                MenuNode node = new MenuNode();
                org.springframework.beans.BeanUtils.copyProperties(m, node);
                node.setChildren(build(menus, m.getId()));
                nodes.add(node);
            }
        }
        return nodes;
    }

    @PostMapping
    @Log("新增菜单")
    public Result<Void> save(@RequestBody SysMenu menu) {
        // 归属当前登录租户（租户管理员维护本租户菜单；不接受前端指定）
        Long tid = AuthContext.get().getTenantId();
        menu.setTenantId(tid == null ? 0L : tid);
        menuMapper.insert(menu);
        return Result.ok();
    }

    @PutMapping
    @Log("修改菜单")
    public Result<Void> update(@RequestBody SysMenu menu) {
        if (menu.getId() == null) return Result.error("缺少菜单ID");
        if (!StringUtils.hasText(menu.getName())) return Result.error("菜单名称不能为空");
        owned(menu.getId());
        // 防环：上级不能是自身或自己的后代（任意层级）
        if (menu.getParentId() != null) {
            if (menu.getParentId().equals(menu.getId())) return Result.error("上级菜单不能是自身");
            if (descendantIds(menu.getId()).contains(menu.getParentId()))
                return Result.error("上级菜单不能是自己的下级");
        }
        menu.setTenantId(null); // 归属不可改
        menuMapper.updateById(menu);
        return Result.ok();
    }

    /** 删除菜单：递归删除整棵子树，仅限本租户范围内 */
    @DeleteMapping("/{id}")
    @Log("删除菜单")
    public Result<Void> delete(@PathVariable Long id) {
        owned(id);
        List<Long> ids = descendantIds(id);
        ids.add(id);
        menuMapper.deleteBatchIds(ids);
        return Result.ok();
    }

    /** 收集某菜单的全部后代ID（限定该菜单所属租户范围内，任意层级） */
    private List<Long> descendantIds(Long rootId) {
        SysMenu root = menuMapper.selectById(rootId);
        Long scopeTenant = root == null ? 0L : root.getTenantId();
        List<Long> ids = new ArrayList<>();
        List<Long> frontier = new ArrayList<>();
        frontier.add(rootId);
        boolean changed = true;
        while (changed) {
            changed = false;
            for (SysMenu m : menuMapper.selectList(scope(scopeTenant))) {
                if (frontier.contains(m.getParentId()) && !ids.contains(m.getId())) {
                    ids.add(m.getId());
                    frontier.add(m.getId());
                    changed = true;
                }
            }
        }
        return ids;
    }
}
