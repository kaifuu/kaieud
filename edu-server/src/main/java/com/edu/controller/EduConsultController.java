package com.edu.controller;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.edu.common.SafeSort;
import com.edu.common.Log;
import com.edu.common.PageResult;
import com.edu.common.Result;
import com.edu.entity.EduConsult;
import com.edu.mapper.EduConsultMapper;
import com.edu.security.RequiresPerm;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

import java.time.LocalDateTime;

/** 咨询管理（教管/班主任查看全部，教师通过教师中心回复） */
@RestController
@RequestMapping("/edu/consult")
@RequiredArgsConstructor
@RequiresPerm("edu:consult")
public class EduConsultController {

    private final EduConsultMapper consultMapper;

    @GetMapping("/page")
    public Result<PageResult<EduConsult>> page(@RequestParam(defaultValue = "1") long page,
                                               @RequestParam(defaultValue = "10") long size,
                                               @RequestParam(required = false) Long studentId,
                                               @RequestParam(required = false) Long teacherId,
                                               @RequestParam(required = false) Integer status,
 @RequestParam(required = false) String sortBy,
 @RequestParam(required = false) String direction) {
        Page<EduConsult> p = consultMapper.selectPage(new Page<>(page, size),
                new LambdaQueryWrapper<EduConsult>()
                        .eq(studentId != null, EduConsult::getStudentId, studentId)
                        .eq(teacherId != null, EduConsult::getTeacherId, teacherId)
                        .eq(status != null, EduConsult::getStatus, status)
                        .last(SafeSort.order(sortBy, direction, "id", "id", "studentId", "teacherId", "status", "createTime")));
        return Result.ok(PageResult.of(p.getTotal(), p.getRecords()));
    }

    @DeleteMapping("/{id}")
    @Log("删除咨询")
    public Result<Void> delete(@PathVariable Long id) {
        consultMapper.deleteById(id);
        return Result.ok();
    }
}
