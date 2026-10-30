package com.edu.controller;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.edu.common.PageResult;
import com.edu.common.Result;
import com.edu.entity.EduConsume;
import com.edu.entity.EduCourse;
import com.edu.entity.EduSchedule;
import com.edu.entity.EduStudent;
import com.edu.entity.EduTeacher;
import com.edu.mapper.EduConsumeMapper;
import com.edu.mapper.EduCourseMapper;
import com.edu.mapper.EduScheduleMapper;
import com.edu.mapper.EduStudentMapper;
import com.edu.mapper.EduTeacherMapper;
import com.edu.security.RequiresPerm;
import lombok.RequiredArgsConstructor;
import org.springframework.util.StringUtils;
import org.springframework.web.bind.annotation.*;

import java.math.BigDecimal;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/** 课时管理：扣课记录台账（消课/签到消课/到课/旷课 统一展示） */
@RestController
@RequestMapping("/edu/hours")
@RequiredArgsConstructor
@RequiresPerm("edu:hours")
public class EduHoursController {

    private final EduConsumeMapper consumeMapper;
    private final EduStudentMapper studentMapper;
    private final EduTeacherMapper teacherMapper;
    private final EduCourseMapper courseMapper;
    private final EduScheduleMapper scheduleMapper;

    @GetMapping("/records")
    public Result<Map<String, Object>> records(
            @RequestParam(defaultValue = "1") long page,
            @RequestParam(defaultValue = "10") long size,
            @RequestParam(required = false) String keyword,
            @RequestParam(required = false) Long teacherId,
            @RequestParam(required = false) String source,
            @RequestParam(required = false) String begin,
            @RequestParam(required = false) String end) {
        Page<EduConsume> p = consumeMapper.selectPage(new Page<>(page, size),
                baseQw(keyword, teacherId, source, begin, end));
        // 当前筛选范围合计扣课课时
        BigDecimal sumHours = BigDecimal.ZERO;
        for (EduConsume c : consumeMapper.selectList(
                baseQw(keyword, teacherId, source, begin, end).select(EduConsume::getHours))) {
            sumHours = sumHours.add(c.getHours() == null ? BigDecimal.ZERO : c.getHours());
        }
        List<EduConsume> records = p.getRecords();

        Map<Long, EduStudent> students = new HashMap<>();
        for (EduStudent st : studentMapper.selectList(null)) students.put(st.getId(), st);
        Map<Long, EduTeacher> teachers = new HashMap<>();
        for (EduTeacher t : teacherMapper.selectList(null)) teachers.put(t.getId(), t);
        Map<Long, EduCourse> courses = new HashMap<>();
        for (EduCourse c : courseMapper.selectList(null)) courses.put(c.getId(), c);

        java.time.format.DateTimeFormatter fmt =
                java.time.format.DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss");
        List<Map<String, Object>> rows = new java.util.ArrayList<>();
        for (EduConsume c : records) {
            Map<String, Object> m = new HashMap<>();
            m.put("id", c.getId());
            m.put("consumeNo", c.getConsumeNo());
            m.put("hours", c.getHours());
            m.put("beforeHours", c.getBeforeHours());
            m.put("afterHours", c.getAfterHours());
            m.put("source", c.getSource() == null ? "消课" : c.getSource());
            m.put("remark", c.getRemark());
            m.put("operator", c.getOperator());
            m.put("canceled", c.getCanceled());
            m.put("auditStatus", c.getAuditStatus());
            m.put("createTime", c.getCreateTime() == null ? null : c.getCreateTime().format(fmt));
            EduStudent st = students.get(c.getStudentId());
            m.put("studentName", st == null ? "-" : st.getName());
            EduTeacher t = teachers.get(c.getTeacherId());
            m.put("teacherName", t == null ? "-" : t.getName());
            EduCourse course = courses.get(c.getCourseId());
            m.put("courseName", course == null ? "-" : course.getName());
            String classTime = null;
            if (c.getScheduleId() != null) {
                EduSchedule sch = scheduleMapper.selectById(c.getScheduleId());
                if (sch != null && sch.getStartTime() != null) classTime = sch.getStartTime().format(fmt);
            }
            m.put("classTime", classTime);
            rows.add(m);
        }
        Map<String, Object> data = new HashMap<>();
        data.put("total", p.getTotal());
        data.put("records", rows);
        data.put("sumHours", sumHours);
        return Result.ok(data);
    }

    /** 公共筛选条件 */
    private LambdaQueryWrapper<EduConsume> baseQw(String keyword, Long teacherId, String source,
                                                  String begin, String end) {
        LambdaQueryWrapper<EduConsume> qw = new LambdaQueryWrapper<EduConsume>()
                .eq(EduConsume::getDeleted, 0)
                .eq(teacherId != null, EduConsume::getTeacherId, teacherId)
                .eq(StringUtils.hasText(source), EduConsume::getSource, source)
                .orderByDesc(EduConsume::getCreateTime);
        if (StringUtils.hasText(begin)) qw.ge(EduConsume::getCreateTime, begin + " 00:00:00");
        if (StringUtils.hasText(end)) qw.le(EduConsume::getCreateTime, end + " 23:59:59");
        if (StringUtils.hasText(keyword)) {
            List<Long> ids = studentMapper.selectList(new LambdaQueryWrapper<EduStudent>()
                            .like(EduStudent::getName, keyword))
                    .stream().map(EduStudent::getId).collect(java.util.stream.Collectors.toList());
            if (ids.isEmpty()) qw.apply("1 = 0");
            else qw.in(EduConsume::getStudentId, ids);
        }
        return qw;
    }
}
