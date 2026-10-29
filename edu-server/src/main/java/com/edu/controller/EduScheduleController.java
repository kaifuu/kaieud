package com.edu.controller;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.edu.common.SafeSort;
import com.edu.common.Log;
import com.edu.common.PageResult;
import com.edu.common.Result;
import com.edu.entity.EduCourse;
import com.edu.entity.EduEnroll;
import com.edu.entity.EduSchedule;
import com.edu.entity.EduStudent;
import com.edu.entity.EduTeacher;
import com.edu.mapper.EduCourseMapper;
import com.edu.mapper.EduScheduleMapper;
import com.edu.mapper.EduStudentMapper;
import com.edu.mapper.EduTeacherMapper;
import com.edu.security.RequiresPerm;
import com.edu.service.EduScheduleService;
import lombok.Data;
import lombok.RequiredArgsConstructor;
import org.springframework.util.StringUtils;
import org.springframework.web.bind.annotation.*;

import java.time.LocalDateTime;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/** 排课管理：新增/调时/取消/完成/补课 */
@RestController
@RequestMapping("/edu/schedule")
@RequiredArgsConstructor
@RequiresPerm("edu:schedule")
public class EduScheduleController {

    private final EduScheduleMapper scheduleMapper;
    private final EduScheduleService scheduleService;
    private final EduCourseMapper courseMapper;
    private final com.edu.mapper.EduConsumeMapper consumeMapper;
    private final EduStudentMapper studentMapper;
    private final EduTeacherMapper teacherMapper;
    private final com.edu.mapper.EduEnrollMapper enrollMapper;

    @GetMapping("/page")
    public Result<PageResult<EduSchedule>> page(@RequestParam(defaultValue = "1") long page,
                                                @RequestParam(defaultValue = "10") long size,
                                                @RequestParam(required = false) Long teacherId,
                                                @RequestParam(required = false) Long clazzId,
                                                @RequestParam(required = false) Long studentId,
                                                @RequestParam(required = false) Long orgId,
                                                @RequestParam(required = false) Integer status,
                                                @RequestParam(required = false) String phase,
                                                @RequestParam(required = false) String begin,
                                                @RequestParam(required = false) String end,
 @RequestParam(required = false) String sortBy,
 @RequestParam(required = false) String direction) {
        LambdaQueryWrapper<EduSchedule> qw = new LambdaQueryWrapper<EduSchedule>()
                .eq(teacherId != null, EduSchedule::getTeacherId, teacherId)
                .eq(clazzId != null, EduSchedule::getClazzId, clazzId)
                .eq(studentId != null, EduSchedule::getStudentId, studentId)
                .eq(orgId != null, EduSchedule::getOrgId, orgId)
                .eq(status != null, EduSchedule::getStatus, status)
                .last(SafeSort.order(sortBy, direction, "startTime", "id", "startTime", "endTime", "status"));
        // 阶段过滤：pending待上课(未开始) / ongoing上课中(进行时间段内) / over已下课(已过时点未确认) / done已上课
        if (StringUtils.hasText(phase)) {
            String now = java.time.LocalDateTime.now()
                    .format(java.time.format.DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss"));
            switch (phase) {
                case "tobe":     // 待排课：全部已录入待执行的课次
                    qw.eq(EduSchedule::getStatus, 0); break;
                case "today":    // 待上课：今天要上的课次
                    qw.eq(EduSchedule::getStatus, 0)
                      .apply("start_time >= {0} AND start_time <= {1}",
                             java.time.LocalDate.now().atStartOfDay()
                             .format(java.time.format.DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss")),
                             java.time.LocalDate.now().atTime(java.time.LocalTime.MAX)
                             .format(java.time.format.DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss")));
                    break;
                case "ongoing":  // 上课中：当前正在进行的课次
                    qw.eq(EduSchedule::getStatus, 0)
                      .apply("start_time <= {0} AND end_time >= {0}", now);
                    break;
                case "done":     // 已下课
                    qw.eq(EduSchedule::getStatus, 1); break;
                default: break;
            }
        }
        if (begin != null && end != null) {
            qw.ge(EduSchedule::getStartTime, begin + " 00:00:00")
              .le(EduSchedule::getStartTime, end + " 23:59:59");
        }
        Page<EduSchedule> p = scheduleMapper.selectPage(new Page<>(page, size), qw);
        return Result.ok(PageResult.of(p.getTotal(), p.getRecords()));
    }

    /** 新增排课 */
    @PostMapping
    @Log("新增排课")
    public Result<EduSchedule> create(@RequestBody EduSchedule schedule) {
        return Result.ok(scheduleService.create(schedule));
    }

    /** 各阶段排课数量（TAB徽标用） */
    @GetMapping("/phase-counts")
    public Result<Map<String, Object>> phaseCounts(
            @RequestParam(required = false) Long teacherId,
            @RequestParam(required = false) Long clazzId,
            @RequestParam(required = false) Long orgId) {
        String now = java.time.LocalDateTime.now()
                .format(java.time.format.DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss"));
        Map<String, Object> m = new HashMap<>();
        m.put("tobe", scheduleMapper.selectCount(new LambdaQueryWrapper<EduSchedule>()
                .eq(teacherId != null, EduSchedule::getTeacherId, teacherId)
                .eq(clazzId != null, EduSchedule::getClazzId, clazzId)
                .eq(orgId != null, EduSchedule::getOrgId, orgId)
                .eq(EduSchedule::getStatus, 0)));
        m.put("today", scheduleMapper.selectCount(new LambdaQueryWrapper<EduSchedule>()
                .eq(teacherId != null, EduSchedule::getTeacherId, teacherId)
                .eq(clazzId != null, EduSchedule::getClazzId, clazzId)
                .eq(orgId != null, EduSchedule::getOrgId, orgId)
                .eq(EduSchedule::getStatus, 0)
                .apply("start_time >= {0} AND start_time <= {1}",
                        java.time.LocalDate.now().atStartOfDay()
                        .format(java.time.format.DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss")),
                        java.time.LocalDate.now().atTime(java.time.LocalTime.MAX)
                        .format(java.time.format.DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss")))));
        m.put("ongoing", scheduleMapper.selectCount(new LambdaQueryWrapper<EduSchedule>()
                .eq(teacherId != null, EduSchedule::getTeacherId, teacherId)
                .eq(clazzId != null, EduSchedule::getClazzId, clazzId)
                .eq(orgId != null, EduSchedule::getOrgId, orgId)
                .eq(EduSchedule::getStatus, 0)
                .apply("start_time <= {0} AND end_time >= {0}", now)));
        m.put("done", scheduleMapper.selectCount(new LambdaQueryWrapper<EduSchedule>()
                .eq(teacherId != null, EduSchedule::getTeacherId, teacherId)
                .eq(clazzId != null, EduSchedule::getClazzId, clazzId)
                .eq(orgId != null, EduSchedule::getOrgId, orgId)
                .eq(EduSchedule::getStatus, 1)));
        return Result.ok(m);
    }

    /**
     * 排课历史：所有教师排课与消课操作记录（合并、倒序），只读。
     * 包含：排课/取消排课/上课完成/消课/撤销消课/补课 等操作痕迹。
     */
    @GetMapping("/history")
    public Result<List<Map<String, Object>>> history(
            @RequestParam(required = false) Long teacherId,
            @RequestParam(required = false) Long orgId,
            @RequestParam(required = false) String begin,
            @RequestParam(required = false) String end) {
        LambdaQueryWrapper<EduSchedule> qw = new LambdaQueryWrapper<EduSchedule>()
                .eq(teacherId != null, EduSchedule::getTeacherId, teacherId)
                .eq(orgId != null, EduSchedule::getOrgId, orgId)
                .orderByDesc(EduSchedule::getCreateTime);
        if (StringUtils.hasText(begin)) qw.ge(EduSchedule::getCreateTime, begin + " 00:00:00");
        if (StringUtils.hasText(end)) qw.le(EduSchedule::getCreateTime, end + " 23:59:59");
        List<EduSchedule> schedules = scheduleMapper.selectList(qw);

        Map<Long, EduTeacher> teachers = new HashMap<>();
        for (EduTeacher t : teacherMapper.selectList(null)) teachers.put(t.getId(), t);
        Map<Long, EduCourse> courses = new HashMap<>();
        for (EduCourse c : courseMapper.selectList(null)) courses.put(c.getId(), c);

        List<Map<String, Object>> result = new java.util.ArrayList<>();
        for (EduSchedule sch : schedules) {
            Map<String, Object> item = new HashMap<>();
            item.put("opType", "排课");
            item.put("time", sch.getStartTime());
            item.put("opTime", sch.getCreateTime());
            EduTeacher t = teachers.get(sch.getTeacherId());
            item.put("teacherName", t == null ? "-" : t.getName());
            com.edu.entity.EduCourse course = courses.get(sch.getCourseId());
            item.put("courseName", course == null ? "-" : course.getName());
            item.put("classroom", sch.getClassroom());
            String st;
            if (sch.getStatus() != null && sch.getStatus() == 1) st = "已完成";
            else st = "已取消";
            item.put("scheduleStatus", st);
            item.put("operator", "-");
            result.add(item);
        }

        // 消课记录
        LambdaQueryWrapper<com.edu.entity.EduConsume> cw = new LambdaQueryWrapper<com.edu.entity.EduConsume>()
                .orderByDesc(com.edu.entity.EduConsume::getCreateTime);
        if (teacherId != null) cw.eq(com.edu.entity.EduConsume::getTeacherId, teacherId);
        if (orgId != null) cw.eq(com.edu.entity.EduConsume::getOrgId, orgId);
        if (StringUtils.hasText(begin)) cw.ge(com.edu.entity.EduConsume::getCreateTime, begin + " 00:00:00");
        if (StringUtils.hasText(end)) cw.le(com.edu.entity.EduConsume::getCreateTime, end + " 23:59:59");
        for (com.edu.entity.EduConsume c : consumeMapper.selectList(cw)) {
            Map<String, Object> item = new HashMap<>();
            item.put("opType", c.getCanceled() != null && c.getCanceled() == 1 ? "撤销消课" : "消课");
            item.put("time", c.getCreateTime());
            com.edu.entity.EduTeacher t = teachers.get(c.getTeacherId());
            item.put("teacherName", t == null ? "-" : t.getName());
            com.edu.entity.EduCourse course = courses.get(c.getCourseId());
            item.put("courseName", course == null ? "-" : course.getName());
            item.put("classroom", "-");
            com.edu.entity.EduStudent st = studentMapper.selectById(c.getStudentId());
            item.put("studentName", st == null ? "-" : st.getName());
            item.put("hours", c.getHours() == null ? java.math.BigDecimal.ZERO : c.getHours());
            item.put("operator", c.getOperator());
            String audit = c.getAuditStatus() == null || c.getAuditStatus() == 1 ? "已审核"
                    : (c.getAuditStatus() == 0 ? "待审核" : "已驳回");
            item.put("auditStatus", audit);
            result.add(item);
        }

        result.sort((a, b) -> String.valueOf(b.get("time")).compareTo(String.valueOf(a.get("time"))));
        return Result.ok(result);
    }

    /** 日历点日期：展示当日课次详情（教师/课程/班型/学员基础信息/课程与教师介绍） */
    @GetMapping("/day-detail")
    public Result<List<Map<String, Object>>> dayDetail(
            @RequestParam String date,
            @RequestParam(required = false) Long teacherId,
            @RequestParam(required = false) Long orgId) {
        LambdaQueryWrapper<EduSchedule> qw = new LambdaQueryWrapper<EduSchedule>()
                .ge(EduSchedule::getStartTime, date + " 00:00:00")
                .le(EduSchedule::getStartTime, date + " 23:59:59")
                .ne(EduSchedule::getStatus, 2)
                .orderByAsc(EduSchedule::getStartTime);
        if (teacherId != null) qw.eq(EduSchedule::getTeacherId, teacherId);
        if (orgId != null) qw.eq(EduSchedule::getOrgId, orgId);
        List<EduSchedule> list = scheduleMapper.selectList(qw);
        if (list.isEmpty()) return Result.ok(new java.util.ArrayList<>());

        // 批量装载教师/课程信息
        Map<Long, EduTeacher> teachers = new HashMap<>();
        for (EduTeacher t : teacherMapper.selectList(null)) teachers.put(t.getId(), t);
        Map<Long, EduCourse> courses = new HashMap<>();
        for (EduCourse c : courseMapper.selectList(null)) courses.put(c.getId(), c);

        List<Map<String, Object>> result = new java.util.ArrayList<>();
        for (EduSchedule sch : list) {
            Map<String, Object> item = new HashMap<>();
            item.put("id", sch.getId());
            item.put("time", sch.getStartTime().toLocalTime().toString().substring(0, 5)
                    + " - " + sch.getEndTime().toLocalTime().toString().substring(0, 5));
            item.put("classroom", sch.getClassroom());
            item.put("status", sch.getStatus());

            EduTeacher teacher = teachers.get(sch.getTeacherId());
            if (teacher != null) {
                item.put("teacherName", teacher.getName());
                item.put("teacherTitle", teacher.getTitle());
                item.put("teacherIntro", teacher.getIntro());
                item.put("teacherPhone", teacher.getPhone());
            }
            EduCourse course = courses.get(sch.getCourseId());
            if (course != null) {
                item.put("courseName", course.getName());
                item.put("courseSubject", course.getSubject());
                item.put("courseIntro", course.getRemark());
            }
            Integer classType = course != null ? course.getClassType() : null;
            item.put("classType", classType == null ? "班课"
                    : (classType == 1 ? "一对一" : classType == 2 ? "小班课" : "大班课"));

            // 学员基础信息：班课取班内在读学员，一对一取指定学员
            List<Map<String, Object>> students = new java.util.ArrayList<>();
            if (sch.getClazzId() != null) {
                List<com.edu.entity.EduEnroll> enrolls = enrollMapper.selectList(
                        new LambdaQueryWrapper<com.edu.entity.EduEnroll>()
                                .eq(com.edu.entity.EduEnroll::getClazzId, sch.getClazzId())
                                .eq(com.edu.entity.EduEnroll::getStatus, 1));
                for (com.edu.entity.EduEnroll e : enrolls) {
                    EduStudent st = studentMapper.selectById(e.getStudentId());
                    if (st == null) continue;
                    Map<String, Object> sm = new HashMap<>();
                    sm.put("name", st.getName());
                    sm.put("gender", st.getGender() != null && st.getGender() == 2 ? "女" : "男");
                    sm.put("grade", st.getGrade());
                    sm.put("school", st.getSchool());
                    sm.put("parentPhone", st.getParentPhone());
                    students.add(sm);
                }
            } else if (sch.getStudentId() != null) {
                EduStudent st = studentMapper.selectById(sch.getStudentId());
                if (st != null) {
                    Map<String, Object> sm = new HashMap<>();
                    sm.put("name", st.getName());
                    sm.put("gender", st.getGender() != null && st.getGender() == 2 ? "女" : "男");
                    sm.put("grade", st.getGrade());
                    sm.put("school", st.getSchool());
                    sm.put("parentPhone", st.getParentPhone());
                    students.add(sm);
                }
            }
            item.put("students", students);
            result.add(item);
        }
        return Result.ok(result);
    }

    @Data
    public static class AdjustDTO {
        private LocalDateTime startTime;
        private LocalDateTime endTime;
        private String reason;
    }

    /** 调整上课时间 */
    @PostMapping("/{id}/adjust")
    @Log("调整上课时间")
    public Result<Void> adjust(@PathVariable Long id, @RequestBody AdjustDTO dto) {
        scheduleService.adjustTime(id, dto.getStartTime(), dto.getEndTime(), dto.getReason());
        return Result.ok();
    }

    /** 取消课次 */
    @PostMapping("/{id}/cancel")
    @Log("取消课次")
    public Result<Void> cancel(@PathVariable Long id, @RequestBody AdjustDTO dto) {
        scheduleService.cancel(id, dto.getReason());
        return Result.ok();
    }

    /** 完成课次（扣课时） */
    @PostMapping("/{id}/complete")
    @Log("完成课次")
    public Result<Void> complete(@PathVariable Long id) {
        scheduleService.complete(id);
        return Result.ok();
    }

    /** 补课：为已取消课次安排新课次 */
    @PostMapping("/{id}/compensate")
    @Log("安排补课")
    public Result<EduSchedule> compensate(@PathVariable Long id, @RequestBody AdjustDTO dto) {
        return Result.ok(scheduleService.compensate(id, dto.getStartTime(), dto.getEndTime()));
    }
}
