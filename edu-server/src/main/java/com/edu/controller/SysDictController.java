package com.edu.controller;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.edu.common.Log;
import com.edu.common.Result;
import com.edu.entity.SysDict;
import com.edu.entity.SysDictType;
import com.edu.mapper.SysDictMapper;
import com.edu.mapper.SysDictTypeMapper;
import com.edu.security.AuthContext;
import com.edu.security.RequiresPerm;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;
import org.springframework.util.StringUtils;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

/** 系统字典：班型/课程分类/缴费方式/学员状态等（配置后全局页面生效）。
 * 字典属平台级数据（全平台共享），仅平台运营可维护；企业侧只读。 */
@RestController
@RequestMapping("/sys/dict")
@RequiredArgsConstructor
public class SysDictController {

    private final SysDictMapper dictMapper;
    private final SysDictTypeMapper dictTypeMapper;

    /** 写操作仅企业：字典由各企业自行维护，平台运营不再插手（tenant 0 模板冻结，仅作新企业初始化源） */
    private boolean isPlatform() {
        Long tid = AuthContext.get().getTenantId();
        return tid == null || tid == 0;
    }

    /** 类型中文名映射（字典管理左侧类型列表显示名，持久化在 sys_dict_type） */
    @GetMapping("/type-names")
    public Result<Map<String, String>> typeNames() {
        Map<String, String> m = new HashMap<>();
        for (SysDictType t : dictTypeMapper.selectList(null))
            m.put(t.getDictType(), t.getName());
        return Result.ok(m);
    }

    /** 类型中文名落库（无则补建，有则更新） */
    private void upsertTypeName(String type, String name) {
        if (!StringUtils.hasText(type) || !StringUtils.hasText(name)) return;
        SysDictType t = dictTypeMapper.selectOne(new LambdaQueryWrapper<SysDictType>()
                .eq(SysDictType::getDictType, type).last("LIMIT 1"));
        if (t == null) {
            t = new SysDictType();
            t.setDictType(type);
            t.setName(name);
            dictTypeMapper.insert(t);
        } else if (!name.equals(t.getName())) {
            t.setName(name);
            dictTypeMapper.updateById(t);
        }
    }

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
        if (isPlatform()) return Result.error("系统字典由各企业自行维护");
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
        if (isPlatform()) return Result.error("系统字典由各企业自行维护");
        dictMapper.deleteById(id);
        return Result.ok();
    }

    /** 新增字典类型（创建一条占位记录，保证类型出现在分组中；中文名落 sys_dict_type） */
    @PostMapping("/type")
    @Log("新增字典类型")
    public Result<Void> saveType(@RequestBody Map<String, String> body) {
        if (isPlatform()) return Result.error("系统字典由各企业自行维护");
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
        // 中文名持久化（此前只存前端内存，刷新即丢）
        upsertTypeName(type, body.get("label"));
        return Result.ok();
    }

    /** 重命名字典类型（批量更新 dict_type，中文名一并落 sys_dict_type） */
    @PutMapping("/type")
    @Log("重命名字典类型")
    public Result<Void> renameType(@RequestBody Map<String, String> body) {
        if (isPlatform()) return Result.error("系统字典由各企业自行维护");
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
        // 类型标识变更时同步改 sys_dict_type；中文名取表单值，未填则沿用原名
        SysDictType existing = dictTypeMapper.selectOne(new LambdaQueryWrapper<SysDictType>()
                .eq(SysDictType::getDictType, oldType).last("LIMIT 1"));
        String name = StringUtils.hasText(body.get("name")) ? body.get("name")
                : (existing == null ? null : existing.getName());
        if (existing != null) dictTypeMapper.deleteById(existing.getId());
        upsertTypeName(newType, name);
        return Result.ok();
    }

    /** 删除字典类型（连带删除其下所有字典项与类型中文名） */
    @DeleteMapping("/type/{type}")
    @Log("删除字典类型")
    public Result<Void> deleteType(@PathVariable String type) {
        if (isPlatform()) return Result.error("系统字典由各企业自行维护");
        dictMapper.delete(new LambdaQueryWrapper<SysDict>().eq(SysDict::getDictType, type));
        dictTypeMapper.delete(new LambdaQueryWrapper<SysDictType>()
                .eq(SysDictType::getDictType, type));
        return Result.ok();
    }
}
