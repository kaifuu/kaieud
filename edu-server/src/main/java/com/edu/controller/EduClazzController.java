package com.edu.controller;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.edu.common.SafeSort;
import com.edu.common.Log;
import com.edu.common.PageResult;
import com.edu.common.Result;
import com.edu.entity.EduClazz;
import com.edu.entity.EduEnroll;
import com.edu.entity.EduTeacher;
import com.edu.mapper.EduClazzMapper;
import com.edu.mapper.EduEnrollMapper;
import com.edu.mapper.EduTeacherMapper;
import com.edu.security.AuthContext;
import com.edu.security.RequiresPerm;
import lombok.RequiredArgsConstructor;
import org.springframework.util.StringUtils;
import org.springframework.web.bind.annotation.*;

/** 班级管理 + 班主任管理(指定/更换班主任) */
@RestController
@RequestMapping("/edu/clazz")
@RequiredArgsConstructor
@RequiresPerm("edu:clazz")
public class EduClazzController {

    private final EduClazzMapper clazzMapper;
    private final EduTeacherMapper teacherMapper;
    private final EduEnrollMapper enrollMapper;

    @GetMapping("/page")
    public Result<PageResult<EduClazz>> page(@RequestParam(defaultValue = "1") long page,
                                             @RequestParam(defaultValue = "10") long size,
                                             @RequestParam(required = false) String keyword,
                                             @RequestParam(required = false) Long courseId,
                                             @RequestParam(required = false) Integer status,
 @RequestParam(required = false) String sortBy,
 @RequestParam(required = false) String direction) {
        Page<EduClazz> p = clazzMapper.selectPage(new Page<>(page, size),
                new LambdaQueryWrapper<EduClazz>()
                        .like(StringUtils.hasText(keyword), EduClazz::getName, keyword)
                        .eq(courseId != null, EduClazz::getCourseId, courseId)
                        .eq(status != null, EduClazz::getStatus, status)
                        .last(SafeSort.order(sortBy, direction, "id", "id", "name", "capacity", "startDate", "status", "createTime")));
        return Result.ok(PageResult.of(p.getTotal(), p.getRecords()));
    }

    @GetMapping("/list")
    public Result<?> list() {
        return Result.ok(clazzMapper.selectList(
                new LambdaQueryWrapper<EduClazz>().ne(EduClazz::getStatus, 3)));
    }

    @PostMapping
    @Log("新增班级")
    public Result<Void> save(@RequestBody EduClazz clazz) {
        clazz.setTenantId(AuthContext.get().getTenantId());
        clazzMapper.insert(clazz);
        return Result.ok();
    }

    @PutMapping
    @Log("修改班级")
    public Result<Void> update(@RequestBody EduClazz clazz) {
        clazzMapper.updateById(clazz);
        return Result.ok();
    }

    @DeleteMapping("/{id}")
    @Log("删除班级")
    public Result<Void> delete(@PathVariable Long id) {
        Long cnt = enrollMapper.selectCount(new LambdaQueryWrapper<EduEnroll>()
                .eq(EduEnroll::getClazzId, id).eq(EduEnroll::getStatus, 1));
        if (cnt > 0) return Result.error("班级仍有在读学生，不允许删除");
        clazzMapper.deleteById(id);
        return Result.ok();
    }

}
