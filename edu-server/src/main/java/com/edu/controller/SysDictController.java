package com.edu.controller;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.edu.common.Log;
import com.edu.common.Result;
import com.edu.entity.SysDict;
import com.edu.mapper.SysDictMapper;
import com.edu.security.RequiresPerm;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;
import org.springframework.util.StringUtils;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

/** 系统字典：班型/课程分类/缴费方式/学员状态等（配置后全局页面生效） */
@RestController
@RequestMapping("/sys/dict")
@RequiredArgsConstructor
public class SysDictController {

    private final SysDictMapper dictMapper;

    /** 全量字典（按类型分组），各页面下拉/标签渲染共用 */
    @GetMapping("/all")
    public Result<Map<String, List<SysDict>>> all() {
        List<SysDict> list = dictMapper.selectList(
                new LambdaQueryWrapper<SysDict>().orderByAsc(SysDict::getSort));
        Map<String, List<SysDict>> result = new HashMap<>();
        for (SysDict d : list) {
            result.computeIfAbsent(d.getDictType(), k -> new java.util.ArrayList<>()).add(d);
        }
        return Result.ok(result);
    }

    /** 按类型取字典项 */
    @GetMapping("/type/{type}")
    public Result<List<SysDict>> byType(@PathVariable String type) {
        return Result.ok(dictMapper.selectList(new LambdaQueryWrapper<SysDict>()
                .eq(SysDict::getDictType, type).orderByAsc(SysDict::getSort)));
    }

    @PostMapping
    @RequiresPerm("sys:dict")
    @Log("新增/修改字典项")
    public Result<Void> save(@RequestBody SysDict dict) {
        if (dict.getDictType() == null || dict.getLabel() == null || dict.getValue() == null)
            return Result.error("类型/标签/值不能为空");
        // 同类型下值唯一
        Long dup = dictMapper.selectCount(new LambdaQueryWrapper<SysDict>()
                .eq(SysDict::getDictType, dict.getDictType())
                .eq(SysDict::getValue, dict.getValue())
                .ne(dict.getId() != null, SysDict::getId, dict.getId()));
        if (dup > 0) return Result.error("该字典值已存在");
        if (dict.getId() != null) dictMapper.updateById(dict);
        else dictMapper.insert(dict);
        return Result.ok();
    }

    @DeleteMapping("/{id}")
    @RequiresPerm("sys:dict")
    @Log("删除字典项")
    public Result<Void> delete(@PathVariable Long id) {
        dictMapper.deleteById(id);
        return Result.ok();
    }

    /** 新增字典类型（创建一条占位记录，保证类型出现在分组中） */
    @PostMapping("/type")
    @Log("新增字典类型")
    public Result<Void> saveType(@RequestBody Map<String, String> body) {
        String type = body.get("type");
        if (!StringUtils.hasText(type)) return Result.error("类型标识不能为空");
        Long cnt = dictMapper.selectCount(new LambdaQueryWrapper<SysDict>().eq(SysDict::getDictType, type));
        if (cnt > 0) return Result.error("该字典类型已存在");
        SysDict d = new SysDict();
        d.setDictType(type);
        d.setLabel(body.getOrDefault("label", type));
        d.setValue("1");
        d.setSort(0);
        dictMapper.insert(d);
        return Result.ok();
    }

    /** 重命名字典类型（批量更新 dict_type） */
    @PutMapping("/type")
    @Log("重命名字典类型")
    public Result<Void> renameType(@RequestBody Map<String, String> body) {
        String oldType = body.get("oldType");
        String newType = body.get("newType");
        if (!StringUtils.hasText(oldType) || !StringUtils.hasText(newType))
            return Result.error("类型标识不能为空");
        List<SysDict> list = dictMapper.selectList(
                new LambdaQueryWrapper<SysDict>().eq(SysDict::getDictType, oldType));
        for (SysDict d : list) {
            d.setDictType(newType);
            dictMapper.updateById(d);
        }
        return Result.ok();
    }

    /** 删除字典类型（连带删除其下所有字典项） */
    @DeleteMapping("/type/{type}")
    @Log("删除字典类型")
    public Result<Void> deleteType(@PathVariable String type) {
        dictMapper.delete(new LambdaQueryWrapper<SysDict>().eq(SysDict::getDictType, type));
        return Result.ok();
    }
}
