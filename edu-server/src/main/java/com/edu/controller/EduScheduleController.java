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
    private final com.edu.mapper.EduClazzMapper clazzMapper;
    private final com.edu.mapper.EduScheduleLogMapper opLogMapper;

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
        // 待排课：不是课次记录，而是「在读教学任务中还有未排课时」的合成任务行
        if ("tobe".equals(phase)) {
            List<EduSchedule> tasks = buildTobeTasks(teacherId, clazzId, studentId, orgId, begin, end);
            int from = (int) Math.min((page - 1) * size, tasks.size());
            int to = (int) Math.min(page * size, tasks.size());
            return Result.ok(PageResult.of(tasks.size(), new java.util.ArrayList<>(tasks.subList(from, to))));
        }
        LambdaQueryWrapper<EduSchedule> qw = new LambdaQueryWrapper<EduSchedule>()
                .eq(teacherId != null, EduSchedule::getTeacherId, teacherId)
                .eq(clazzId != null, EduSchedule::getClazzId, clazzId)
                .eq(studentId != null, EduSchedule::getStudentId, studentId)
                .eq(orgId != null, EduSchedule::getOrgId, orgId)
                .eq(status != null, EduSchedule::getStatus, status)
                .last(SafeSort.order(sortBy, direction, "startTime", "id", "startTime", "endTime", "status"));
        // 阶段过滤（按当前系统时间推导，四个TAB互斥）：
        // today待上课：已排课且 start_time > now；ongoing上课中：start <= now <= end；
        // done已下课：end_time < now（含已完成与未登记完成，不含已取消）
        if (StringUtils.hasText(phase)) {
            String now = java.time.LocalDateTime.now()
                    .format(java.time.format.DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss"));
            switch (phase) {
                case "today":    // 待上课：已被安排、尚未开始
                    qw.eq(EduSchedule::getStatus, 0)
                      .apply("start_time > {0}", now);
                    break;
                case "ongoing":  // 上课中：时间段与当前系统时间重合
                    qw.eq(EduSchedule::getStatus, 0)
                      .apply("start_time <= {0} AND end_time >= {0}", now);
                    break;
                case "done":     // 已下课：时间段已结束
                    qw.ne(EduSchedule::getStatus, 2)
                      .apply("end_time < {0}", now);
                    break;
                default: break;
            }
        }
        if (begin != null && end != null) {
            qw.ge(EduSchedule::getStartTime, begin + " 00:00:00")
              .le(EduSchedule::getStartTime, end + " 23:59:59");
        }
        Page<EduSchedule> p = scheduleMapper.selectPage(new Page<>(page, size), qw);
        // 行级阶段标注（状态列展示用）
        LocalDateTime now = LocalDateTime.now();
        for (EduSchedule s : p.getRecords()) s.setPhase(derivePhase(s, now));
        return Result.ok(PageResult.of(p.getTotal(), p.getRecords()));
    }

    /** 按课次时间与系统时间推导阶段（1已到课/3,4请假/5,6,7旷课均为已归档） */
    private String derivePhase(EduSchedule s, LocalDateTime now) {
        if (s.getStatus() != null && s.getStatus() == 2) return "canceled";
        if (s.getStatus() != null && s.getStatus() != 0) return "done";
        if (s.getStartTime() != null && s.getEndTime() != null
                && !now.isBefore(s.getStartTime()) && !now.isAfter(s.getEndTime())) return "ongoing";
        return now.isBefore(s.getStartTime()) ? "today" : "done";
    }

    /**
     * 待排课任务：在读教学任务（报名/班级）中计划课时尚未排完的部分。
     * 班课按「班级+教师」一个任务，计划课时取课程总课时；一对一按「学生+课程+教师」，计划课时取报名课时。
     * 已排课次（未取消）按班级 / 学生+课程 维度计数抵扣。
     */
    private List<EduSchedule> buildTobeTasks(Long teacherId, Long clazzId, Long studentId, Long orgId,
                                             String begin, String end) {
        List<EduSchedule> tasks = new java.util.ArrayList<>();
        List<EduEnroll> enrolls = enrollMapper.selectList(new LambdaQueryWrapper<EduEnroll>()
                .eq(EduEnroll::getStatus, 1)
                .eq(teacherId != null, EduEnroll::getTeacherId, teacherId)
                .eq(clazzId != null, EduEnroll::getClazzId, clazzId)
                .eq(studentId != null, EduEnroll::getStudentId, studentId));
        if (enrolls.isEmpty()) return tasks;

        Map<Long, EduCourse> courses = new HashMap<>();
        for (EduCourse c : courseMapper.selectList(null)) courses.put(c.getId(), c);
        Map<Long, com.edu.entity.EduClazz> clazzes = new HashMap<>();
        for (com.edu.entity.EduClazz c : clazzMapper.selectList(null)) clazzes.put(c.getId(), c);
        Map<Long, EduStudent> students = new HashMap<>();
        if (orgId != null) for (EduStudent st : studentMapper.selectList(null)) students.put(st.getId(), st);

        // 已排课次（未取消）计数：班课按班级、一对一按 学生+课程
        Map<String, Integer> scheduled = new HashMap<>();
        for (EduSchedule s : scheduleMapper.selectList(new LambdaQueryWrapper<EduSchedule>()
                .ne(EduSchedule::getStatus, 2))) {
            String key = s.getClazzId() != null ? "C" + s.getClazzId()
                    : "S" + s.getStudentId() + "-" + s.getCourseId();
            scheduled.merge(key, 1, Integer::sum);
        }

        java.util.Set<String> seen = new java.util.HashSet<>();
        for (EduEnroll e : enrolls) {
            if (orgId != null) { // 校区过滤：按学员归属校区
                EduStudent st = students.get(e.getStudentId());
                if (st == null || !orgId.equals(st.getOrgId())) continue;
            }
            String taskKey; int planned; String timeText = null;
            java.time.LocalDate taskStart = null, taskEnd = null;
            if (e.getClazzId() != null) { // 班课任务
                com.edu.entity.EduClazz c = clazzes.get(e.getClazzId());
                if (c == null) continue;
                taskKey = "C" + e.getClazzId() + "-T" + e.getTeacherId();
                EduCourse course = courses.get(e.getCourseId());
                planned = course != null && course.getTotalHours() != null ? course.getTotalHours() : 0;
                taskStart = c.getStartDate();
                taskEnd = c.getEndDate();
                timeText = (taskStart == null ? "" : taskStart.toString())
                        + " ~ " + (taskEnd == null ? "未定" : taskEnd.toString());
            } else { // 一对一任务
                taskKey = "S" + e.getStudentId() + "-" + e.getCourseId() + "-T" + e.getTeacherId();
                planned = e.getTotalHours() == null ? 0 : e.getTotalHours();
                taskStart = e.getCreateTime() == null ? null : e.getCreateTime().toLocalDate();
            }
            if (!seen.add(taskKey)) continue;
            String cntKey = e.getClazzId() != null ? "C" + e.getClazzId()
                    : "S" + e.getStudentId() + "-" + e.getCourseId();
            int remaining = planned - scheduled.getOrDefault(cntKey, 0);
            if (remaining <= 0) continue;
            // 时间范围筛选：任务周期与筛选范围有交集才展示（一对一无固定结课日期，视为持续有效）
            if (begin != null && end != null) {
                java.time.LocalDate b = java.time.LocalDate.parse(begin);
                java.time.LocalDate en = java.time.LocalDate.parse(end);
                boolean overlap = (taskStart == null || !taskStart.isAfter(en))
                        && (taskEnd == null || !taskEnd.isBefore(b));
                if (!overlap) continue;
            }
            EduSchedule t = new EduSchedule();
            t.setTeacherId(e.getTeacherId());
            t.setClazzId(e.getClazzId());
            t.setStudentId(e.getClazzId() == null ? e.getStudentId() : null);
            t.setCourseId(e.getCourseId());
            t.setRemainingHours(remaining);
            t.setTimeText(timeText);
            t.setPhase("tobe");
            tasks.add(t);
        }
        tasks.sort((a, b2) -> Long.compare(
                a.getTeacherId() == null ? Long.MAX_VALUE : a.getTeacherId(),
                b2.getTeacherId() == null ? Long.MAX_VALUE : b2.getTeacherId()));
        return tasks;
    }

    /** 新增排课 */
    @PostMapping
    @Log("新增排课")
    public Result<EduSchedule> create(@RequestBody EduSchedule schedule) {
        return Result.ok(scheduleService.create(schedule));
    }

    /** 各阶段排课数量（TAB徽标用，口径与列表阶段过滤一致） */
    @GetMapping("/phase-counts")
    public Result<Map<String, Object>> phaseCounts(
            @RequestParam(required = false) Long teacherId,
            @RequestParam(required = false) Long clazzId,
            @RequestParam(required = false) Long orgId,
            @RequestParam(required = false) String begin,
            @RequestParam(required = false) String end) {
        String now = java.time.LocalDateTime.now()
                .format(java.time.format.DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss"));
        Map<String, Object> m = new HashMap<>();
        m.put("tobe", buildTobeTasks(teacherId, clazzId, null, orgId, begin, end).size());
        m.put("today", scheduleMapper.selectCount(new LambdaQueryWrapper<EduSchedule>()
                .eq(teacherId != null, EduSchedule::getTeacherId, teacherId)
                .eq(clazzId != null, EduSchedule::getClazzId, clazzId)
                .eq(orgId != null, EduSchedule::getOrgId, orgId)
                .eq(EduSchedule::getStatus, 0)
                .apply("start_time > {0}", now)
                .ge(begin != null && end != null, EduSchedule::getStartTime, begin + " 00:00:00")
                .le(begin != null && end != null, EduSchedule::getStartTime, end + " 23:59:59")));
        m.put("ongoing", scheduleMapper.selectCount(new LambdaQueryWrapper<EduSchedule>()
                .eq(teacherId != null, EduSchedule::getTeacherId, teacherId)
                .eq(clazzId != null, EduSchedule::getClazzId, clazzId)
                .eq(orgId != null, EduSchedule::getOrgId, orgId)
                .eq(EduSchedule::getStatus, 0)
                .apply("start_time <= {0} AND end_time >= {0}", now)
                .ge(begin != null && end != null, EduSchedule::getStartTime, begin + " 00:00:00")
                .le(begin != null && end != null, EduSchedule::getStartTime, end + " 23:59:59")));
        m.put("done", scheduleMapper.selectCount(new LambdaQueryWrapper<EduSchedule>()
                .eq(teacherId != null, EduSchedule::getTeacherId, teacherId)
                .eq(clazzId != null, EduSchedule::getClazzId, clazzId)
                .eq(orgId != null, EduSchedule::getOrgId, orgId)
                .ne(EduSchedule::getStatus, 2)
                .apply("end_time < {0}", now)
                .ge(begin != null && end != null, EduSchedule::getStartTime, begin + " 00:00:00")
                .le(begin != null && end != null, EduSchedule::getStartTime, end + " 23:59:59")));
        return Result.ok(m);
    }

    /**
     * 排课历史：所有教师排课与消课操作记录（合并），只读，支持分页与排序。
     * 排课/调课/取消排课/补课来自排课操作日志 edu_schedule_log（每笔操作一条，含操作人登录账号）；
     * 消课/撤销消课来自 edu_consume。排序字段白名单：opTime 操作时间 / time 上课时间，默认 opTime 倒序。
     */
    @GetMapping("/history")
    public Result<PageResult<Map<String, Object>>> history(
            @RequestParam(defaultValue = "1") long page,
            @RequestParam(defaultValue = "10") long size,
            @RequestParam(required = false) Long teacherId,
            @RequestParam(required = false) Long orgId,
            @RequestParam(required = false) String begin,
            @RequestParam(required = false) String end,
            @RequestParam(required = false) String sortBy,
            @RequestParam(required = false) String direction,
            @RequestParam(required = false) String opType,
            @RequestParam(required = false) String recordStatus) {
        java.time.format.DateTimeFormatter fmt =
                java.time.format.DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss");

        Map<Long, EduTeacher> teachers = new HashMap<>();
        for (EduTeacher t : teacherMapper.selectList(null)) teachers.put(t.getId(), t);
        Map<Long, EduCourse> courses = new HashMap<>();
        for (EduCourse c : courseMapper.selectList(null)) courses.put(c.getId(), c);

        List<Map<String, Object>> result = new java.util.ArrayList<>();
        // 排课类操作：读排课操作日志（操作人=当时登录账号的用户名）
        LambdaQueryWrapper<com.edu.entity.EduScheduleLog> lw = new LambdaQueryWrapper<>();
        if (StringUtils.hasText(begin)) lw.ge(com.edu.entity.EduScheduleLog::getOpTime, begin + " 00:00:00");
        if (StringUtils.hasText(end)) lw.le(com.edu.entity.EduScheduleLog::getOpTime, end + " 23:59:59");
        List<com.edu.entity.EduScheduleLog> logs = opLogMapper.selectList(lw);
        if (!logs.isEmpty()) {
            java.util.Set<Long> schIds = new java.util.HashSet<>();
            for (com.edu.entity.EduScheduleLog lg : logs) schIds.add(lg.getScheduleId());
            Map<Long, EduSchedule> schMap = new HashMap<>();
            for (EduSchedule s : scheduleMapper.selectBatchIds(schIds)) schMap.put(s.getId(), s);
            for (com.edu.entity.EduScheduleLog lg : logs) {
                EduSchedule sch = schMap.get(lg.getScheduleId());
                Map<String, Object> item = new HashMap<>();
                item.put("opType", lg.getOpType());
                item.put("opTime", lg.getOpTime() == null ? null : lg.getOpTime().format(fmt));
                item.put("operator", lg.getOperator());
                if (sch == null) {
                    // 已删除课次：用日志快照展示（删除时回填），无快照的孤儿日志跳过
                    if (!StringUtils.hasText(lg.getClassTime())) continue;
                    item.put("time", lg.getClassTime());
                    item.put("teacherName", lg.getTeacherName() == null ? "-" : lg.getTeacherName());
                    item.put("courseName", lg.getCourseName() == null ? "-" : lg.getCourseName());
                    item.put("classroom", lg.getClassroom());
                    item.put("recordStatus", "已删除");
                } else {
                    if (teacherId != null && !teacherId.equals(sch.getTeacherId())) continue;
                    if (orgId != null && !orgId.equals(sch.getOrgId())) continue;
                    item.put("time", sch.getStartTime() == null ? null : sch.getStartTime().format(fmt));
                    EduTeacher t = teachers.get(sch.getTeacherId());
                    item.put("teacherName", t == null ? "-" : t.getName());
                    com.edu.entity.EduCourse course = courses.get(sch.getCourseId());
                    item.put("courseName", course == null ? "-" : course.getName());
                    item.put("classroom", sch.getClassroom());
                    item.put("recordStatus", sch.getStatus() == null ? "-"
                            : new String[]{"待上课", "已到课", "已取消", "教师请假", "学生请假",
                            "旷课·待审批", "旷课·已免扣", "旷课·已扣课"}[sch.getStatus()]);
                }
                result.add(item);
            }
        }

        // 消课类操作
        LambdaQueryWrapper<com.edu.entity.EduConsume> cw = new LambdaQueryWrapper<>();
        if (teacherId != null) cw.eq(com.edu.entity.EduConsume::getTeacherId, teacherId);
        if (orgId != null) cw.eq(com.edu.entity.EduConsume::getOrgId, orgId);
        if (StringUtils.hasText(begin)) cw.ge(com.edu.entity.EduConsume::getCreateTime, begin + " 00:00:00");
        if (StringUtils.hasText(end)) cw.le(com.edu.entity.EduConsume::getCreateTime, end + " 23:59:59");
        for (com.edu.entity.EduConsume c : consumeMapper.selectList(cw)) {
            Map<String, Object> item = new HashMap<>();
            item.put("opType", c.getCanceled() != null && c.getCanceled() == 1 ? "撤销消课" : "消课");
            item.put("opTime", c.getCreateTime() == null ? null : c.getCreateTime().format(fmt));
            item.put("time", null);
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
            item.put("recordStatus", audit);
            result.add(item);
        }

        // 操作类型/记录状态筛选（内存过滤，取值为历史行的展示文案）
        if (StringUtils.hasText(opType)) result.removeIf(m -> !opType.equals(m.get("opType")));
        if (StringUtils.hasText(recordStatus)) result.removeIf(m -> !recordStatus.equals(m.get("recordStatus")));

        // 排序（白名单：opTime/time，字符串时间字典序即时间序），默认操作时间倒序
        String key = "time".equals(sortBy) ? "time" : "opTime";
        java.util.Comparator<Map<String, Object>> cmp = java.util.Comparator.comparing(
                m -> (String) m.get(key), java.util.Comparator.nullsLast(String::compareTo));
        if (!"asc".equalsIgnoreCase(direction)) cmp = cmp.reversed();
        result.sort(cmp);

        // 内存分页
        int from = (int) Math.min((page - 1) * size, result.size());
        int to = (int) Math.min(page * size, result.size());
        return Result.ok(PageResult.of(result.size(), new java.util.ArrayList<>(result.subList(from, to))));
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
        /** 换教室（可选）：不传则沿用原教室 */
        private Long classroomId;
    }

    /** 调整上课时间（可同时更换教室；目标时段教室已占用则拒绝并提示空闲教室） */
    @PostMapping("/{id}/adjust")
    @Log("调整上课时间")
    public Result<Void> adjust(@PathVariable Long id, @RequestBody AdjustDTO dto) {
        scheduleService.adjustTime(id, dto.getStartTime(), dto.getEndTime(), dto.getReason(), dto.getClassroomId());
        return Result.ok();
    }

    /** 取消课次 */
    @PostMapping("/{id}/cancel")
    @Log("取消课次")
    public Result<Void> cancel(@PathVariable Long id, @RequestBody AdjustDTO dto) {
        scheduleService.cancel(id, dto.getReason());
        return Result.ok();
    }

    /** 删除课次（日历已排课区域清除）：确认后软删除，排课历史留痕 */
    @PostMapping("/{id}/delete")
    @Log("删除课次")
    public Result<Void> delete(@PathVariable Long id,
                               @RequestBody(required = false) AdjustDTO dto) {
        scheduleService.delete(id, dto == null ? null : dto.getReason());
        return Result.ok();
    }

    /** 完成课次（旧入口，等价到课扣1节，保持兼容） */
    @PostMapping("/{id}/complete")
    @Log("完成课次")
    public Result<Void> complete(@PathVariable Long id) {
        scheduleService.complete(id);
        return Result.ok();
    }

    /** 到课登记信息：可扣节数上限（排课时长÷每节时长折算）等，弹窗展示用 */
    @GetMapping("/{id}/attend-info")
    public Result<Map<String, Object>> attendInfo(@PathVariable Long id) {
        EduSchedule s = scheduleMapper.selectById(id);
        if (s == null) return Result.error("课次不存在");
        Map<String, Object> m = new HashMap<>();
        m.put("maxHours", scheduleService.maxAttendHours(s));
        m.put("minutes", scheduleService.scheduleMinutes(s));
        m.put("durationMinutes", scheduleService.perLessonMinutes(s));
        return Result.ok(m);
    }

    /** 到课登记：按所选节数扣课（1~折算上限；扣减少于上限须填写扣课说明） */
    @PostMapping("/{id}/attend")
    @Log("到课登记")
    public Result<Integer> attend(@PathVariable Long id, @RequestBody AttendDTO dto) {
        return Result.ok(scheduleService.attend(id,
                dto == null ? null : dto.getHours(), dto == null ? null : dto.getRemark()));
    }

    /** 请假登记：type=3教师请假 / type=4学生请假，均不扣课 */
    @PostMapping("/{id}/leave")
    @Log("请假登记")
    public Result<Void> leave(@PathVariable Long id, @RequestParam int type) {
        scheduleService.leave(id, type);
        return Result.ok();
    }

    /** 旷课登记：生成旷课申请走审批（批准免扣课，72小时内未批准系统自动扣课） */
    @PostMapping("/{id}/absent")
    @Log("旷课登记")
    public Result<Void> absent(@PathVariable Long id, @RequestBody AdjustDTO dto) {
        scheduleService.absent(id, dto == null ? null : dto.getReason());
        return Result.ok();
    }

    @Data
    public static class AttendDTO {
        private Integer hours;
        /** 扣课说明：扣减节数少于本课次应扣节数时必填 */
        private String remark;
    }

    /** 补课：为已取消课次安排新课次（可指定新教室，默认沿用原教室） */
    @PostMapping("/{id}/compensate")
    @Log("安排补课")
    public Result<EduSchedule> compensate(@PathVariable Long id, @RequestBody AdjustDTO dto) {
        return Result.ok(scheduleService.compensate(id, dto.getStartTime(), dto.getEndTime(), dto.getClassroomId()));
    }
}
