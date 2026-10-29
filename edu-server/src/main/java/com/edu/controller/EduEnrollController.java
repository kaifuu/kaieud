package com.edu.controller;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.edu.common.SafeSort;
import com.edu.common.Log;
import com.edu.common.PageResult;
import com.edu.common.Result;
import com.edu.entity.EduEnroll;
import com.edu.mapper.EduEnrollMapper;
import com.edu.security.RequiresPerm;
import com.edu.service.EduEnrollService;
import lombok.Data;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

/** 报名管理：订阅/退订班课、指派老师、补课时 */
@RestController
@RequestMapping("/edu/enroll")
@RequiredArgsConstructor
@RequiresPerm("edu:enroll")
public class EduEnrollController {

    private final EduEnrollMapper enrollMapper;
    private final EduEnrollService enrollService;

    @GetMapping("/page")
    public Result<PageResult<EduEnroll>> page(@RequestParam(defaultValue = "1") long page,
                                              @RequestParam(defaultValue = "10") long size,
                                              @RequestParam(required = false) Long studentId,
                                              @RequestParam(required = false) Long teacherId,
                                              @RequestParam(required = false) Integer status,
 @RequestParam(required = false) String sortBy,
 @RequestParam(required = false) String direction) {
        Page<EduEnroll> p = enrollMapper.selectPage(new Page<>(page, size),
                new LambdaQueryWrapper<EduEnroll>()
                        .eq(studentId != null, EduEnroll::getStudentId, studentId)
                        .eq(teacherId != null, EduEnroll::getTeacherId, teacherId)
                        .eq(status != null, EduEnroll::getStatus, status)
                        .last(SafeSort.order(sortBy, direction, "id", "id", "price", "discount", "totalAmount", "usedHours", "status", "createTime")));
        return Result.ok(PageResult.of(p.getTotal(), p.getRecords()));
    }

    @Data
    public static class SubscribeDTO {
        private Long studentId;
        private Long courseId;
        private Long clazzId;
        private Long teacherId;
    }

    /** 订阅班课/课程 */
    @PostMapping("/subscribe")
    @Log("订阅班课")
    public Result<EduEnroll> subscribe(@RequestBody SubscribeDTO dto) {
        return Result.ok(enrollService.subscribe(dto.getStudentId(), dto.getCourseId(),
                dto.getClazzId(), dto.getTeacherId()));
    }

    /** 取消订阅 */
    @PostMapping("/{id}/unsubscribe")
    @Log("取消订阅班课")
    public Result<Void> unsubscribe(@PathVariable Long id) {
        enrollService.unsubscribe(id);
        return Result.ok();
    }

    /** 为学生指派/更换上课老师 */
    @Data
    public static class AssignDTO {
        private Long teacherId;
    }

    @PostMapping("/{id}/assign-teacher")
    @Log("指派授课老师")
    public Result<Void> assignTeacher(@PathVariable Long id, @RequestBody AssignDTO dto) {
        enrollService.assignTeacher(id, dto.getTeacherId());
        return Result.ok();
    }

    /** 补充因请假取消的课时次数 */
    @Data
    public static class CompensateHoursDTO {
        private Integer hours;
    }

    @PostMapping("/{id}/compensate-hours")
    @Log("补充课时次数")
    public Result<Void> compensateHours(@PathVariable Long id, @RequestBody CompensateHoursDTO dto) {
        enrollService.compensateHours(id, dto.getHours() == null ? 1 : dto.getHours());
        return Result.ok();
    }
}
