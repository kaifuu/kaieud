package com.edu.controller;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.edu.common.Log;
import com.edu.common.PageResult;
import com.edu.common.Result;
import com.edu.entity.EduAbsence;
import com.edu.entity.EduClazz;
import com.edu.entity.EduCourse;
import com.edu.entity.EduStudent;
import com.edu.entity.EduTeacher;
import com.edu.mapper.EduAbsenceMapper;
import com.edu.mapper.EduClazzMapper;
import com.edu.mapper.EduCourseMapper;
import com.edu.mapper.EduStudentMapper;
import com.edu.mapper.EduTeacherMapper;
import com.edu.security.RequiresPerm;
import com.edu.service.EduAbsenceService;
import lombok.Data;
import lombok.RequiredArgsConstructor;
import org.springframework.util.StringUtils;
import org.springframework.web.bind.annotation.*;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

/** 旷课审批：批准免扣课 / 驳回立即扣课；未审批超72小时系统自动扣课 */
@RestController
@RequestMapping("/edu/absence")
@RequiredArgsConstructor
@RequiresPerm("edu:absence")
public class EduAbsenceController {

    private final EduAbsenceMapper absenceMapper;
    private final EduAbsenceService absenceService;
    private final EduTeacherMapper teacherMapper;
    private final EduCourseMapper courseMapper;
    private final EduStudentMapper studentMapper;
    private final EduClazzMapper clazzMapper;

    /** 旷课申请分页列表 */
    @GetMapping("/page")
    public Result<PageResult<Map<String, Object>>> page(
            @RequestParam(defaultValue = "1") long page,
            @RequestParam(defaultValue = "10") long size,
            @RequestParam(required = false) Integer status,
            @RequestParam(required = false) Long teacherId,
            @RequestParam(required = false) String keyword,
            @RequestParam(required = false) String begin,
            @RequestParam(required = false) String end) {
        LambdaQueryWrapper<EduAbsence> qw = new LambdaQueryWrapper<EduAbsence>()
                .eq(status != null, EduAbsence::getStatus, status)
                .eq(teacherId != null, EduAbsence::getTeacherId, teacherId)
                .orderByDesc(EduAbsence::getApplyTime);
        if (StringUtils.hasText(begin)) qw.ge(EduAbsence::getApplyTime, begin + " 00:00:00");
        if (StringUtils.hasText(end)) qw.le(EduAbsence::getApplyTime, end + " 23:59:59");
        if (StringUtils.hasText(keyword)) {
            List<Long> ids = studentMapper.selectList(new LambdaQueryWrapper<EduStudent>()
                            .like(EduStudent::getName, keyword))
                    .stream().map(EduStudent::getId).collect(java.util.stream.Collectors.toList());
            if (ids.isEmpty()) return Result.ok(PageResult.of(0, new java.util.ArrayList<>()));
            qw.in(EduAbsence::getStudentId, ids);
        }
        Page<EduAbsence> p = absenceMapper.selectPage(new Page<>(page, size), qw);
        List<EduAbsence> records = p.getRecords();

        Map<Long, EduTeacher> teachers = new HashMap<>();
        for (EduTeacher t : teacherMapper.selectList(null)) teachers.put(t.getId(), t);
        Map<Long, EduCourse> courses = new HashMap<>();
        for (EduCourse c : courseMapper.selectList(null)) courses.put(c.getId(), c);
        Map<Long, EduClazz> clazzes = new HashMap<>();
        for (EduClazz c : clazzMapper.selectList(null)) clazzes.put(c.getId(), c);
        Map<Long, EduStudent> students = new HashMap<>();
        if (!records.isEmpty()) for (EduStudent st : studentMapper.selectList(null)) students.put(st.getId(), st);

        java.time.format.DateTimeFormatter fmt =
                java.time.format.DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss");
        List<Map<String, Object>> rows = new java.util.ArrayList<>();
        for (EduAbsence a : records) {
            Map<String, Object> m = new HashMap<>();
            m.put("id", a.getId());
            m.put("scheduleId", a.getScheduleId());
            m.put("classTime", a.getClassTime() == null ? null : a.getClassTime().format(fmt));
            m.put("applyTime", a.getApplyTime() == null ? null : a.getApplyTime().format(fmt));
            m.put("deadline", a.getDeadline() == null ? null : a.getDeadline().format(fmt));
            m.put("status", a.getStatus());
            m.put("reason", a.getReason());
            m.put("approver", a.getApprover());
            m.put("approveTime", a.getApproveTime() == null ? null : a.getApproveTime().format(fmt));
            m.put("approveRemark", a.getApproveRemark());
            m.put("deductTime", a.getDeductTime() == null ? null : a.getDeductTime().format(fmt));
            EduTeacher t = teachers.get(a.getTeacherId());
            m.put("teacherName", t == null ? "-" : t.getName());
            EduCourse c = courses.get(a.getCourseId());
            m.put("courseName", c == null ? "-" : c.getName());
            EduStudent st = students.get(a.getStudentId());
            m.put("studentName", st == null ? null : st.getName());
            EduClazz cz = clazzes.get(a.getClazzId());
            m.put("clazzName", cz == null ? null : cz.getName());
            rows.add(m);
        }
        return Result.ok(PageResult.of(p.getTotal(), rows));
    }

    /** 批准：免扣课 */
    @PostMapping("/{id}/approve")
    @Log("旷课申请-批准")
    public Result<Void> approve(@PathVariable Long id, @RequestBody RemarkDTO dto) {
        absenceService.approve(id, dto == null ? null : dto.getRemark());
        return Result.ok();
    }

    /** 驳回：立即扣课 */
    @PostMapping("/{id}/reject")
    @Log("旷课申请-驳回")
    public Result<Void> reject(@PathVariable Long id, @RequestBody RemarkDTO dto) {
        absenceService.reject(id, dto == null ? null : dto.getRemark());
        return Result.ok();
    }

    @Data
    public static class RemarkDTO {
        private String remark;
    }
}
