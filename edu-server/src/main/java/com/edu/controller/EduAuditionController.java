package com.edu.controller;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.edu.common.Log;
import com.edu.common.PageResult;
import com.edu.common.Result;
import com.edu.common.SafeSort;
import com.edu.entity.EduAudition;
import com.edu.mapper.EduAuditionMapper;
import com.edu.security.AuthContext;
import com.edu.security.RequiresPerm;
import lombok.RequiredArgsConstructor;
import org.springframework.util.StringUtils;
import org.springframework.web.bind.annotation.*;

import java.util.List;

/** 试听管理：插班试听（关联已成班课）/ 开班试听（全新录入） */
@RestController
@RequestMapping("/edu/audition")
@RequiredArgsConstructor
@RequiresPerm("edu:audition")
public class EduAuditionController {

    private final EduAuditionMapper auditionMapper;

    /** 列表：类型/关键字（名称、课程、教师）筛选 */
    @GetMapping("/page")
    public Result<PageResult<EduAudition>> page(@RequestParam(defaultValue = "1") long page,
                                                @RequestParam(defaultValue = "10") long size,
                                                @RequestParam(required = false) Integer type,
                                                @RequestParam(required = false) String keyword,
                                                @RequestParam(required = false) String sortBy,
                                                @RequestParam(required = false) String direction) {
        Page<EduAudition> p = auditionMapper.selectPage(new Page<>(page, size),
                new LambdaQueryWrapper<EduAudition>()
                        .eq(type != null, EduAudition::getType, type)
                        .and(StringUtils.hasText(keyword), w -> w
                                .like(EduAudition::getName, keyword)
                                .or().like(EduAudition::getCourseName, keyword)
                                .or().like(EduAudition::getTeacherName, keyword))
                        .last(SafeSort.order(sortBy, direction, "id",
                                "id", "name", "type", "auditionDate", "plannedCount", "createTime")));
        return Result.ok(PageResult.of(p.getTotal(), p.getRecords()));
    }

    /** 全量下拉用 */
    @GetMapping("/list")
    public Result<List<EduAudition>> list() {
        return Result.ok(auditionMapper.selectList(
                new LambdaQueryWrapper<EduAudition>().orderByDesc(EduAudition::getId)));
    }

    @PostMapping
    @Log("新增试听")
    public Result<Void> save(@RequestBody EduAudition a) {
        if (!StringUtils.hasText(a.getName())) return Result.error("名称为必填项");
        a.setId(null);
        a.setTenantId(AuthContext.get().getTenantId());
        a.setOperator(AuthContext.get().getRealName());
        auditionMapper.insert(a);
        return Result.ok();
    }

    @PutMapping
    @Log("修改试听")
    public Result<Void> update(@RequestBody EduAudition a) {
        if (a.getId() == null) return Result.error("缺少ID");
        if (!StringUtils.hasText(a.getName())) return Result.error("名称为必填项");
        auditionMapper.updateById(a);
        return Result.ok();
    }

    @DeleteMapping("/{id}")
    @Log("删除试听")
    public Result<Void> delete(@PathVariable Long id) {
        auditionMapper.deleteById(id);
        return Result.ok();
    }
}
