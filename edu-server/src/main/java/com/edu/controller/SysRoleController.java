package com.edu.controller;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.edu.common.SafeSort;
import com.edu.common.Log;
import com.edu.common.PageResult;
import com.edu.common.Result;
import com.edu.entity.SysRole;
import com.edu.entity.SysRoleMenu;
import com.edu.mapper.SysRoleMapper;
import com.edu.mapper.SysRoleMenuMapper;
import com.edu.security.RequiresPerm;
import lombok.RequiredArgsConstructor;
import org.springframework.util.StringUtils;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.stream.Collectors;

/** 角色管理（租户隔离）：平台看平台角色，租户只看/只建本租户角色 */
@RestController
@RequestMapping("/sys/role")
@RequiredArgsConstructor
@RequiresPerm("sys:role")
public class SysRoleController {

    private final SysRoleMapper roleMapper;
    private final SysRoleMenuMapper roleMenuMapper;

    /** 当前数据范围：平台账号看全部角色（跨租户管理视图），租户由拦截器自动隔离 */
    private LambdaQueryWrapper<SysRole> scope() {
        return new LambdaQueryWrapper<>();
    }

    /** 校验角色归属：平台可操作任意，租户仅限本租户 */
    private SysRole owned(Long id) {
        SysRole r = roleMapper.selectById(id);
        if (r == null) throw new com.edu.common.exception.BizException("角色不存在");
        Long tid = com.edu.security.AuthContext.get().getTenantId();
        if (tid != null && tid != 0 && !tid.equals(r.getTenantId()))
            throw new com.edu.common.exception.BizException("不能操作其他租户的角色");
        return r;
    }

    @GetMapping("/page")
    public Result<PageResult<SysRole>> page(@RequestParam(defaultValue = "1") long page,
                                            @RequestParam(defaultValue = "10") long size,
                                            @RequestParam(required = false) String keyword,
 @RequestParam(required = false) String sortBy,
 @RequestParam(required = false) String direction) {
        Page<SysRole> p = roleMapper.selectPage(new Page<>(page, size), scope()
                .like(StringUtils.hasText(keyword), SysRole::getName, keyword)
                .last(SafeSort.order(sortBy, direction, "id", "id", "name", "code", "createTime")));
        return Result.ok(PageResult.of(p.getTotal(), p.getRecords()));
    }

    @GetMapping("/list")
    public Result<List<SysRole>> list() {
        return Result.ok(roleMapper.selectList(scope()));
    }

    @PostMapping
    @Log("新增角色")
    public Result<Void> save(@RequestBody SysRole role) {
        // 归属当前登录租户（不接受前端指定）
        Long tid = com.edu.security.AuthContext.get().getTenantId();
        role.setTenantId(tid == null ? 0L : tid);
        roleMapper.insert(role);
        return Result.ok();
    }

    @PutMapping
    @Log("修改角色")
    public Result<Void> update(@RequestBody SysRole role) {
        owned(role.getId());
        role.setTenantId(null); // 归属不可改
        roleMapper.updateById(role);
        return Result.ok();
    }

    @DeleteMapping("/{id}")
    @Log("删除角色")
    public Result<Void> delete(@PathVariable Long id) {
        owned(id);
        if (id == 1L) return Result.error("内置角色不允许删除");
        roleMapper.deleteById(id);
        roleMenuMapper.delete(new LambdaQueryWrapper<SysRoleMenu>().eq(SysRoleMenu::getRoleId, id));
        return Result.ok();
    }

    /** 分配菜单权限（仅本租户角色，菜单只能勾选本租户菜单树） */
    @PostMapping("/{id}/menus")
    @Log("角色分配菜单")
    public Result<Void> assignMenus(@PathVariable Long id, @RequestBody List<Long> menuIds) {
        owned(id);
        roleMenuMapper.delete(new LambdaQueryWrapper<SysRoleMenu>().eq(SysRoleMenu::getRoleId, id));
        if (menuIds != null) {
            for (Long menuId : menuIds) {
                SysRoleMenu rm = new SysRoleMenu();
                rm.setRoleId(id);
                rm.setMenuId(menuId);
                roleMenuMapper.insert(rm);
            }
        }
        return Result.ok();
    }

    /** 角色已有菜单ID */
    @GetMapping("/{id}/menus")
    public Result<List<Long>> menus(@PathVariable Long id) {
        return Result.ok(roleMenuMapper.selectList(
                        new LambdaQueryWrapper<SysRoleMenu>().eq(SysRoleMenu::getRoleId, id))
                .stream().map(SysRoleMenu::getMenuId).collect(Collectors.toList()));
    }
}
