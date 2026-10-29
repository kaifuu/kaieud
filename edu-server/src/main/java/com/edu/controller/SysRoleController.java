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

/** 角色管理 */
@RestController
@RequestMapping("/sys/role")
@RequiredArgsConstructor
@RequiresPerm("sys:role")
public class SysRoleController {

    private final SysRoleMapper roleMapper;
    private final SysRoleMenuMapper roleMenuMapper;

    @GetMapping("/page")
    public Result<PageResult<SysRole>> page(@RequestParam(defaultValue = "1") long page,
                                            @RequestParam(defaultValue = "10") long size,
                                            @RequestParam(required = false) String keyword,
 @RequestParam(required = false) String sortBy,
 @RequestParam(required = false) String direction) {
        LambdaQueryWrapper<SysRole> qw = new LambdaQueryWrapper<SysRole>()
                .like(StringUtils.hasText(keyword), SysRole::getName, keyword)
                .last(SafeSort.order(sortBy, direction, "id", "id", "name", "code", "createTime"));
        Page<SysRole> p = roleMapper.selectPage(new Page<>(page, size), qw);
        return Result.ok(PageResult.of(p.getTotal(), p.getRecords()));
    }

    @GetMapping("/list")
    public Result<List<SysRole>> list() {
        return Result.ok(roleMapper.selectList(null));
    }

    @PostMapping
    @Log("新增角色")
    public Result<Void> save(@RequestBody SysRole role) {
        role.setTenantId(com.edu.security.AuthContext.get().getTenantId());
        roleMapper.insert(role);
        return Result.ok();
    }

    @PutMapping
    @Log("修改角色")
    public Result<Void> update(@RequestBody SysRole role) {
        roleMapper.updateById(role);
        return Result.ok();
    }

    @DeleteMapping("/{id}")
    @Log("删除角色")
    public Result<Void> delete(@PathVariable Long id) {
        if (id == 1L) return Result.error("内置角色不允许删除");
        roleMapper.deleteById(id);
        roleMenuMapper.delete(new LambdaQueryWrapper<SysRoleMenu>().eq(SysRoleMenu::getRoleId, id));
        return Result.ok();
    }

    /** 分配菜单权限 */
    @PostMapping("/{id}/menus")
    @Log("角色分配菜单")
    public Result<Void> assignMenus(@PathVariable Long id, @RequestBody List<Long> menuIds) {
        roleMenuMapper.delete(new LambdaQueryWrapper<SysRoleMenu>().eq(SysRoleMenu::getRoleId, id));
        for (Long menuId : menuIds) {
            SysRoleMenu rm = new SysRoleMenu();
            rm.setRoleId(id);
            rm.setMenuId(menuId);
            roleMenuMapper.insert(rm);
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
