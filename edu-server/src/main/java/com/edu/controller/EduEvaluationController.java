package com.edu.controller;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.edu.common.SafeSort;
import com.edu.common.PageResult;
import com.edu.common.Result;
import com.edu.entity.EduEvaluation;
import com.edu.mapper.EduEvaluationMapper;
import com.edu.security.RequiresPerm;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

/** 评价管理（教管/班主任查看全部） */
@RestController
@RequestMapping("/edu/evaluation")
@RequiredArgsConstructor
@RequiresPerm("edu:consult")
public class EduEvaluationController {

    private final EduEvaluationMapper evaluationMapper;

    @GetMapping("/page")
    public Result<PageResult<EduEvaluation>> page(@RequestParam(defaultValue = "1") long page,
                                                  @RequestParam(defaultValue = "10") long size,
                                                  @RequestParam(required = false) Long teacherId,
                                                  @RequestParam(required = false) Long studentId,
 @RequestParam(required = false) String sortBy,
 @RequestParam(required = false) String direction) {
        Page<EduEvaluation> p = evaluationMapper.selectPage(new Page<>(page, size),
                new LambdaQueryWrapper<EduEvaluation>()
                        .eq(teacherId != null, EduEvaluation::getTeacherId, teacherId)
                        .eq(studentId != null, EduEvaluation::getStudentId, studentId)
                        .last(SafeSort.order(sortBy, direction, "id", "id", "teacherId", "score", "createTime")));
        return Result.ok(PageResult.of(p.getTotal(), p.getRecords()));
    }
}
