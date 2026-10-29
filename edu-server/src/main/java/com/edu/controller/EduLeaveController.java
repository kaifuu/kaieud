package com.edu.controller;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.edu.common.SafeSort;
import com.edu.common.Log;
import com.edu.common.PageResult;
import com.edu.common.Result;
import com.edu.entity.EduLeave;
import com.edu.mapper.EduLeaveMapper;
import com.edu.security.RequiresPerm;
import com.edu.service.EduLeaveService;
import lombok.Data;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

/** 请假管理：申请 -> 审批 */
@RestController
@RequestMapping("/edu/leave")
@RequiredArgsConstructor
@RequiresPerm("edu:leave")
public class EduLeaveController {

    private final EduLeaveMapper leaveMapper;
    private final EduLeaveService leaveService;

    @GetMapping("/page")
    public Result<PageResult<EduLeave>> page(@RequestParam(defaultValue = "1") long page,
                                             @RequestParam(defaultValue = "10") long size,
                                             @RequestParam(required = false) Long studentId,
                                             @RequestParam(required = false) Integer status,
 @RequestParam(required = false) String sortBy,
 @RequestParam(required = false) String direction) {
        Page<EduLeave> p = leaveMapper.selectPage(new Page<>(page, size),
                new LambdaQueryWrapper<EduLeave>()
                        .eq(studentId != null, EduLeave::getStudentId, studentId)
                        .eq(status != null, EduLeave::getStatus, status)
                        .last(SafeSort.order(sortBy, direction, "id", "id", "studentId", "status", "createTime")));
        return Result.ok(PageResult.of(p.getTotal(), p.getRecords()));
    }

    @Data
    public static class ApplyDTO {
        private Long studentId;
        private Long scheduleId;
        private String reason;
    }

    @PostMapping("/apply")
    @Log("提交请假申请")
    public Result<Void> apply(@RequestBody ApplyDTO dto) {
        leaveService.apply(dto.getStudentId(), dto.getScheduleId(), dto.getReason());
        return Result.ok();
    }

    @PostMapping("/{id}/approve")
    @Log("审批请假")
    public Result<Void> approve(@PathVariable Long id, @RequestParam boolean pass) {
        leaveService.approve(id, pass);
        return Result.ok();
    }
}
