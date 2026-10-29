package com.edu.controller;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.edu.common.SafeSort;
import com.edu.common.Log;
import com.edu.common.PageResult;
import com.edu.common.Result;
import com.edu.entity.SysLog;
import com.edu.mapper.SysLogMapper;
import com.edu.security.RequiresPerm;
import lombok.RequiredArgsConstructor;
import org.springframework.util.StringUtils;
import org.springframework.web.bind.annotation.*;

/** 日志管理 */
@RestController
@RequestMapping("/sys/log")
@RequiredArgsConstructor
@RequiresPerm("sys:log")
public class SysLogController {

    private final SysLogMapper logMapper;

    @GetMapping("/page")
    public Result<PageResult<SysLog>> page(@RequestParam(defaultValue = "1") long page,
                                           @RequestParam(defaultValue = "10") long size,
                                           @RequestParam(required = false) String keyword,
                                           @RequestParam(required = false) Integer status,
                                           @RequestParam(required = false) Integer logType,
 @RequestParam(required = false) String sortBy,
 @RequestParam(required = false) String direction) {
        LambdaQueryWrapper<SysLog> qw = new LambdaQueryWrapper<SysLog>()
                .and(StringUtils.hasText(keyword), w -> w
                        .like(SysLog::getOperation, keyword)
                        .or().like(SysLog::getUsername, keyword))
                .eq(status != null, SysLog::getStatus, status)
                .eq(logType != null, SysLog::getLogType, logType)
                .last(SafeSort.order(sortBy, direction, "createTime", "id", "username", "operation", "createTime", "duration"));
        Page<SysLog> p = logMapper.selectPage(new Page<>(page, size), qw);
        return Result.ok(PageResult.of(p.getTotal(), p.getRecords()));
    }

    @DeleteMapping("/{id}")
    @Log("删除日志")
    public Result<Void> delete(@PathVariable Long id) {
        logMapper.deleteById(id);
        return Result.ok();
    }
}
