package com.edu.controller;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.edu.common.Log;
import com.edu.common.Result;
import com.edu.dto.MenuNode;
import com.edu.entity.SysMenu;
import com.edu.mapper.SysMenuMapper;
import com.edu.security.RequiresPerm;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

import java.util.List;

/** 菜单管理 */
@RestController
@RequestMapping("/sys/menu")
@RequiredArgsConstructor
@RequiresPerm("sys:menu")
public class SysMenuController {

    private final SysMenuMapper menuMapper;

    @GetMapping("/tree")
    public Result<List<MenuNode>> tree() {
        List<SysMenu> all = menuMapper.selectList(
                new LambdaQueryWrapper<SysMenu>().orderByAsc(SysMenu::getSort));
        return Result.ok(build(all, 0L));
    }

    static List<MenuNode> build(List<SysMenu> menus, Long parentId) {
        List<MenuNode> nodes = new java.util.ArrayList<>();
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
        menuMapper.insert(menu);
        return Result.ok();
    }

    @PutMapping
    @Log("修改菜单")
    public Result<Void> update(@RequestBody SysMenu menu) {
        menuMapper.updateById(menu);
        return Result.ok();
    }

    @DeleteMapping("/{id}")
    @Log("删除菜单")
    public Result<Void> delete(@PathVariable Long id) {
        menuMapper.deleteById(id);
        menuMapper.delete(new LambdaQueryWrapper<SysMenu>().eq(SysMenu::getParentId, id));
        return Result.ok();
    }
}
