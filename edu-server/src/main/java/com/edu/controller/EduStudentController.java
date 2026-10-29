package com.edu.controller;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.edu.common.SafeSort;
import com.edu.common.Log;
import com.edu.common.PageResult;
import com.edu.common.Result;
import com.edu.entity.EduStudent;
import com.edu.mapper.EduStudentMapper;
import com.edu.security.AuthContext;
import com.edu.security.RequiresPerm;
import lombok.RequiredArgsConstructor;
import org.springframework.util.StringUtils;
import org.springframework.web.bind.annotation.*;

/** 学生管理 */
@RestController
@RequestMapping("/edu/student")
@RequiredArgsConstructor
@RequiresPerm("edu:student")
public class EduStudentController {

    private final EduStudentMapper studentMapper;
    private final com.edu.mapper.EduStudentLogMapper logMapper;
    private final com.edu.service.DataScopeService dataScopeService;

    /** 学员下拉列表（合同/排课/消课等模块选择用） */
    @GetMapping("/list")
    public Result<?> list() {
        return Result.ok(studentMapper.selectList(new LambdaQueryWrapper<EduStudent>()
                .eq(EduStudent::getDeleted, 0).orderByDesc(EduStudent::getId)));
    }

    /** 学员变更记录（供用户管理详情等扩展） */
    @GetMapping("/{id}/logs")
    public Result<?> logs(@PathVariable Long id) {
        return Result.ok(logMapper.selectList(
                new LambdaQueryWrapper<com.edu.entity.EduStudentLog>()
                        .eq(com.edu.entity.EduStudentLog::getStudentId, id)
                        .orderByDesc(com.edu.entity.EduStudentLog::getId)));
    }
}
