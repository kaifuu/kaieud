package com.edu.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.edu.common.exception.BizException;
import com.edu.entity.EduClazz;
import com.edu.entity.EduEnroll;
import com.edu.entity.EduSchedule;
import com.edu.mapper.EduClazzMapper;
import com.edu.mapper.EduEnrollMapper;
import com.edu.mapper.EduScheduleMapper;
import com.edu.security.AuthContext;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.List;

/** 排课业务：创建、调时、取消、完成(扣课时)、补课、冲突检测 */
@Service
@RequiredArgsConstructor
public class EduScheduleService {

    private final EduScheduleMapper scheduleMapper;
    private final EduEnrollMapper enrollMapper;
    private final EduClazzMapper clazzMapper;
    private final com.edu.mapper.EduTeacherMapper teacherMapper;
    private final com.edu.mapper.EduStudentMapper studentMapper;
    private final com.edu.mapper.EduCourseMapper courseMapper;
    private final com.edu.mapper.EduClassroomMapper classroomMapper;
    private final EduEnrollService enrollService;
    private final com.edu.mapper.EduAbsenceMapper absenceMapper;
    private final EduConsumeService consumeService;
    private final com.edu.mapper.EduScheduleLogMapper opLogMapper;
    private final com.edu.mapper.SysDictMapper dictMapper;
    private final com.edu.mapper.EduConsumeMapper consumeRecMapper;
    private final com.edu.mapper.EduLeaveMapper leaveRecMapper;
    private final com.edu.mapper.SysOrgMapper orgRecMapper;

    /** 旷课审批超时自动扣课的时限 */
    public static final int ABSENCE_DEADLINE_HOURS = 72;

    /** 新增排课，校验教师时间冲突 */
    @Transactional
    public EduSchedule create(EduSchedule s) {
        checkFutureStart(s.getStartTime());
        checkBanTime(s.getTeacherId(), s.getStartTime(), s.getEndTime());
        checkConflict(s.getTeacherId(), s.getStartTime(), s.getEndTime(), null);
        s.setTenantId(AuthContext.get().getTenantId());
        // 教室占用校验：同教室同时段不能重复排课
        checkRoomConflict(s.getClassroomId(), s.getClassroom(), s.getStartTime(), s.getEndTime(), null, false);
        // 归属校区：优先取学员所在校区，否则继承创建人校区
        com.edu.entity.EduStudent stu = s.getStudentId() != null
                ? studentMapper.selectById(s.getStudentId()) : null;
        if (stu != null && stu.getOrgId() != null) s.setOrgId(stu.getOrgId());
        else {
            com.edu.security.LoginUser lu = com.edu.security.AuthContext.get();
            if (lu != null && lu.getOrgId() != null) s.setOrgId(lu.getOrgId()); else s.setOrgId(1L);
        }
        // 课程归属：edu_schedule.course_id 非空，入参未带时按班级/学员报名记录推导
        if (s.getCourseId() == null) {
            if (s.getClazzId() != null) {
                EduClazz clazz = clazzMapper.selectById(s.getClazzId());
                if (clazz != null) s.setCourseId(clazz.getCourseId());
            } else if (s.getStudentId() != null) {
                EduEnroll e = enrollMapper.selectOne(new LambdaQueryWrapper<EduEnroll>()
                        .eq(EduEnroll::getStudentId, s.getStudentId())
                        .eq(EduEnroll::getStatus, 1)
                        .orderByDesc(EduEnroll::getCreateTime)
                        .last("LIMIT 1"));
                if (e != null) s.setCourseId(e.getCourseId());
            }
        }
        if (s.getCourseId() == null) throw new BizException("未能确定上课课程，请检查班级或报名记录");
        s.setStatus(0);
        s.setSchedType(s.getSchedType() == null ? 0 : s.getSchedType());
        scheduleMapper.insert(s);
        opLog(s, "排课", null);
        return s;
    }

    /** 调整上课时间（班主任/教管为学生或老师调时，可同时更换教室） */
    @Transactional
    public void adjustTime(Long id, LocalDateTime startTime, LocalDateTime endTime, String reason, Long classroomId) {
        EduSchedule s = mustGet(id);
        if (s.getStatus() != 0) throw new BizException("仅待上课的课次可调整时间");
        checkFutureStart(startTime);
        checkBanTime(s.getTeacherId(), startTime, endTime);
        checkConflict(s.getTeacherId(), startTime, endTime, id);
        // 教室占用校验：目标时段同教室已排课则拒绝调整（提示占用时段与空闲教室）
        Long roomId = classroomId != null ? classroomId : s.getClassroomId();
        String roomName = classroomId != null ? classroomName(classroomId) : s.getClassroom();
        boolean roomChanged = classroomId != null && !classroomId.equals(s.getClassroomId());
        checkRoomConflict(roomId, roomName, startTime, endTime, id, true);
        if (classroomId != null) {
            s.setClassroomId(classroomId);
            s.setClassroom(roomName);
        }
        s.setStartTime(startTime);
        s.setEndTime(endTime);
        s.setRemark((roomChanged ? "调整时间与教室" : "调整时间") + "：" + (reason == null ? "" : reason));
        s.setUpdateTime(LocalDateTime.now());
        scheduleMapper.updateById(s);
        opLog(s, "调课", reason);
    }

    /** 取消课次 */
    @Transactional
    public void cancel(Long id, String reason) {
        EduSchedule s = mustGet(id);
        if (s.getStatus() != 0) throw new BizException("仅待上课的课次可取消");
        s.setStatus(2);
        s.setCancelReason(reason);
        s.setUpdateTime(LocalDateTime.now());
        scheduleMapper.updateById(s);
        opLog(s, "取消排课", reason);
    }

    /**
     * 删除课次（日历已排课区域清除）：软删除，业务查询自动过滤；
     * 排课历史留痕——写入「删除排课」日志并回填课次快照（删除后历史仍可展示）。
     */
    @Transactional
    public void delete(Long id, String reason) {
        EduSchedule s = mustGet(id);
        if (s.getStatus() == null || (s.getStatus() != 0 && s.getStatus() != 2))
            throw new BizException("仅待上课/已取消的课次可删除（已登记到课/请假/旷课的课次不可删除）");
        String classTime = s.getStartTime().format(java.time.format.DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm"))
                + " ~ " + s.getEndTime().format(java.time.format.DateTimeFormatter.ofPattern("HH:mm"));
        com.edu.entity.EduTeacher t = s.getTeacherId() == null ? null : teacherMapper.selectById(s.getTeacherId());
        com.edu.entity.EduCourse c = s.getCourseId() == null ? null : courseMapper.selectById(s.getCourseId());
        String teacherName = t == null ? "-" : t.getName();
        String courseName = c == null ? "-" : c.getName();
        // 逻辑删除（@TableLogic：deleteById 转为 UPDATE deleted=1，后续查询自动过滤）
        scheduleMapper.deleteById(id);
        // 历史留痕：该课次全部日志回填快照 + 追加「删除排课」记录
        opLogMapper.update(null, new com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper<com.edu.entity.EduScheduleLog>()
                .eq(com.edu.entity.EduScheduleLog::getScheduleId, id)
                .set(com.edu.entity.EduScheduleLog::getCourseName, courseName)
                .set(com.edu.entity.EduScheduleLog::getTeacherName, teacherName)
                .set(com.edu.entity.EduScheduleLog::getClassroom, s.getClassroom() == null ? "-" : s.getClassroom())
                .set(com.edu.entity.EduScheduleLog::getClassTime, classTime));
        com.edu.entity.EduScheduleLog lg = new com.edu.entity.EduScheduleLog();
        lg.setTenantId(s.getTenantId());
        lg.setScheduleId(id);
        lg.setOpType("删除排课");
        com.edu.security.LoginUser lu = AuthContext.get();
        lg.setOperator(lu == null ? "system" : lu.getUsername());
        lg.setOpTime(LocalDateTime.now());
        lg.setRemark(reason);
        lg.setCourseName(courseName);
        lg.setTeacherName(teacherName);
        lg.setClassroom(s.getClassroom() == null ? "-" : s.getClassroom());
        lg.setClassTime(classTime);
        opLogMapper.insert(lg);
    }

    /** 完成课次（旧入口）：等价于到课登记扣1节，兼容原按钮/接口 */
    @Transactional
    public void complete(Long id) {
        attend(id, 1, "旧完成入口：按 1 节扣课");
    }

    /**
     * 到课登记：扣课节数范围 1 ~ maxAttendHours（排课时长÷课程每节时长，向下取整）。
     * 扣减少于应扣节数时必须填写扣课说明（记入消课台账）。
     * 扣课走合同消课（台账留痕+合同已消课时），并同步扣减报名课时（V1口径）。
     */
    @Transactional
    public int attend(Long id, Integer hours, String remark) {
        EduSchedule s = mustGet(id);
        if (s.getStatus() == null || s.getStatus() != 0) throw new BizException("仅未登记的课次可登记到课");
        int max = maxAttendHours(s);
        int n = hours == null ? 1 : hours;
        if (n < 1 || n > max)
            throw new BizException("扣课节数为 1~" + max + " 节（本课次时长 " + scheduleMinutes(s)
                    + " 分钟 ÷ 每节 " + perLessonMinutes(s) + " 分钟折算）");
        if (n < max && !org.springframework.util.StringUtils.hasText(remark))
            throw new BizException("本次扣课 " + n + " 节少于本课次应扣 " + max + " 节，请填写扣课说明");
        int contracts = deductHours(s, n, "到课", remark);
        s.setStatus(1);
        if (org.springframework.util.StringUtils.hasText(remark)) s.setRemark(remark);   // 上课内容留痕到课次备注
        s.setUpdateTime(LocalDateTime.now());
        scheduleMapper.updateById(s);
        return contracts;
    }

    /** 到课可扣节数上限：排课时长 ÷ 课程每节时长（合同绑定课程），向下取整，至少 1 节 */
    public int maxAttendHours(EduSchedule s) {
        int per = perLessonMinutes(s);
        long mins = scheduleMinutes(s);
        return Math.max(1, (int) (mins / per));
    }

    /** 排课时长（分钟） */
    public long scheduleMinutes(EduSchedule s) {
        if (s.getStartTime() == null || s.getEndTime() == null) return 0;
        return java.time.Duration.between(s.getStartTime(), s.getEndTime()).toMinutes();
    }

    /** 课程每节时长（分钟，缺省 60） */
    public int perLessonMinutes(EduSchedule s) {
        com.edu.entity.EduCourse c = s.getCourseId() == null ? null : courseMapper.selectById(s.getCourseId());
        return c == null || c.getDurationMinutes() == null || c.getDurationMinutes() <= 0 ? 60 : c.getDurationMinutes();
    }

    /** 请假登记：3教师请假 / 4学生请假，均不扣课（排课历史留痕） */
    @Transactional
    public void leave(Long id, int type) {
        if (type != 3 && type != 4) throw new BizException("请假类型不合法");
        EduSchedule s = mustGet(id);
        if (s.getStatus() == null || s.getStatus() != 0) throw new BizException("仅未登记的课次可登记请假");
        s.setStatus(type);
        s.setRemark(type == 3 ? "教师请假（不扣课）" : "学生请假（不扣课）");
        s.setUpdateTime(LocalDateTime.now());
        scheduleMapper.updateById(s);
        opLog(s, type == 3 ? "教师请假" : "学生请假", "不扣课");
    }

    /** 旷课登记：生成旷课申请（待审批），审批通过免扣课，72小时内未批准自动扣课 */
    @Transactional
    public void absent(Long id, String reason) {
        EduSchedule s = mustGet(id);
        if (s.getStatus() == null || s.getStatus() != 0) throw new BizException("仅未登记的课次可登记旷课");
        s.setStatus(5);
        s.setUpdateTime(LocalDateTime.now());
        scheduleMapper.updateById(s);

        com.edu.entity.EduAbsence a = new com.edu.entity.EduAbsence();
        a.setTenantId(s.getTenantId());
        a.setOrgId(s.getOrgId());
        a.setScheduleId(s.getId());
        a.setStudentId(s.getStudentId());
        a.setTeacherId(s.getTeacherId());
        a.setCourseId(s.getCourseId());
        a.setClazzId(s.getClazzId());
        a.setClassTime(s.getStartTime());
        a.setReason(reason);
        a.setStatus(0);
        a.setApplyTime(LocalDateTime.now());
        a.setDeadline(LocalDateTime.now().plusHours(ABSENCE_DEADLINE_HOURS));
        absenceMapper.insert(a);
        opLog(s, "旷课登记", reason);
    }

    /**
     * 扣课统一路径：edu_consume 台账 + 合同已消课时（先购买后赠送）+ 报名课时（V1口径）保持一致。
     * 返回消课合同数。
     */
    int deductHours(EduSchedule s, int hours, String source) {
        return deductHours(s, hours, source, null);
    }

    int deductHours(EduSchedule s, int hours, String source, String remark) {
        int contracts = consumeService.signBySchedule(s.getId(),
                java.math.BigDecimal.valueOf(hours), source, remark);
        // 报名课时同步扣减（学员档案/课时进度展示口径）
        if (s.getClazzId() != null) {
            List<EduEnroll> enrolls = enrollMapper.selectList(new LambdaQueryWrapper<EduEnroll>()
                    .eq(EduEnroll::getClazzId, s.getClazzId())
                    .eq(EduEnroll::getStatus, 1));
            for (EduEnroll e : enrolls) enrollService.consumeHours(e.getId(), hours);
        } else if (s.getStudentId() != null) {
            EduEnroll e = enrollMapper.selectOne(new LambdaQueryWrapper<EduEnroll>()
                    .eq(EduEnroll::getStudentId, s.getStudentId())
                    .eq(EduEnroll::getCourseId, s.getCourseId())
                    .eq(EduEnroll::getStatus, 1).last("LIMIT 1"));
            if (e != null) enrollService.consumeHours(e.getId(), hours);
        }
        return contracts;
    }

    /** 补课：基于已取消课次生成新课次（可指定新教室，默认沿用原教室） */
    @Transactional
    public EduSchedule compensate(Long originId, LocalDateTime start, LocalDateTime end, Long classroomId) {
        EduSchedule origin = mustGet(originId);
        if (origin.getStatus() != 2) throw new BizException("仅已取消的课次可安排补课");
        checkFutureStart(start);
        checkConflict(origin.getTeacherId(), start, end, null);
        // 教室沿用原课次（补 copy classroom_id 导致教室占用校验漏检的历史问题），可换教室
        Long roomId = classroomId != null ? classroomId : origin.getClassroomId();
        String roomName = classroomId != null ? classroomName(classroomId) : origin.getClassroom();
        checkRoomConflict(roomId, roomName, start, end, null, false);
        EduSchedule neu = new EduSchedule();
        neu.setTenantId(origin.getTenantId());
        neu.setCourseId(origin.getCourseId());
        neu.setClazzId(origin.getClazzId());
        neu.setTeacherId(origin.getTeacherId());
        neu.setStudentId(origin.getStudentId());
        neu.setClassroomId(roomId);
        neu.setClassroom(roomName);
        neu.setStartTime(start);
        neu.setEndTime(end);
        neu.setStatus(0);
        neu.setOriginId(origin.getId());
        neu.setCompensation(1);
        scheduleMapper.insert(neu);
        opLog(neu, "补课", "来源课次#" + originId);
        return neu;
    }

    /* ==================== 重复排课 / 编辑排课 ==================== */

    /** 重复排课入参：开始/结束规则 + 周几 + 时段 + 教师/助教/教室/主题（clazzId 归属班级） */
    @lombok.Data
    public static class RepeatDTO {
        private Long clazzId;
        private Long teacherId;
        private Long assistantId;
        private Long classroomId;
        private String topic;
        /** 重复方式：1每周 2隔周 4隔天 */
        private Integer repeatMode;
        /** 周几上课：1~7（ISO，周一=1）；隔天重复时不适用 */
        private java.util.List<Integer> weekdays;
        private java.time.LocalDate beginDate;
        /** 结束方式：date按日期 / count按次数 */
        private String endMode;
        private java.time.LocalDate endDate;
        private Integer endCount;
        /** 节假日过滤：true=节假日不排课 */
        private Boolean skipHoliday;
        /** 上课时段 HH:mm */
        private String beginTime;
        private String endTime;
    }

    /** 节假日集合：字典 dict_type=holiday（label=节日名，value=yyyy-MM-dd），租户在字典管理维护 */
    private java.util.Set<java.time.LocalDate> holidaySet() {
        java.util.Set<java.time.LocalDate> set = new java.util.HashSet<>();
        for (com.edu.entity.SysDict d : dictMapper.selectList(new LambdaQueryWrapper<com.edu.entity.SysDict>()
                .eq(com.edu.entity.SysDict::getDictType, "holiday"))) {
            try { set.add(java.time.LocalDate.parse(d.getValue())); } catch (Exception ignore) { }
        }
        return set;
    }

    /**
     * 按重复规则推算上课日期（未做节假日过滤的规则命中集）。
     * 每周：选中周几每周排；隔周：以开始日期所在周为第0周，隔一周排；隔天：从开始日期起隔一天排一次（周几不适用）。
     * 防御上限 3000 天 / 200 次，避免结束方式异常时死循环。
     */
    private java.util.List<java.time.LocalDate> computeRepeatDates(RepeatDTO dto) {
        java.util.List<java.time.LocalDate> dates = new java.util.ArrayList<>();
        java.time.LocalDate begin = dto.getBeginDate();
        if (begin == null) return dates;
        java.util.Set<Integer> wd = dto.getWeekdays() == null
                ? new java.util.HashSet<Integer>() : new java.util.HashSet<>(dto.getWeekdays());
        boolean byCount = "count".equals(dto.getEndMode());
        int endCount = byCount && dto.getEndCount() != null ? Math.min(dto.getEndCount(), 200) : 200;
        java.time.LocalDate beginMonday = begin.with(java.time.temporal.TemporalAdjusters
                .previousOrSame(java.time.DayOfWeek.MONDAY));
        int guard = 0;
        for (java.time.LocalDate d = begin; guard++ < 3000; d = d.plusDays(1)) {
            if (!byCount && (dto.getEndDate() == null || d.isAfter(dto.getEndDate()))) break;
            boolean hit;
            if (dto.getRepeatMode() != null && dto.getRepeatMode() == 4) {
                hit = java.time.temporal.ChronoUnit.DAYS.between(begin, d) % 2 == 0;
            } else if (wd.contains(d.getDayOfWeek().getValue())) {
                if (dto.getRepeatMode() != null && dto.getRepeatMode() == 2) {
                    long weeks = java.time.temporal.ChronoUnit.WEEKS.between(beginMonday,
                            d.with(java.time.temporal.TemporalAdjusters.previousOrSame(java.time.DayOfWeek.MONDAY)));
                    hit = weeks % 2 == 0;
                } else {
                    hit = true;
                }
            } else {
                hit = false;
            }
            if (hit) {
                dates.add(d);
                if (dates.size() >= endCount) break;
            }
        }
        return dates;
    }

    /** 重复规则校验（新建与预览共用） */
    private void validateRepeat(RepeatDTO dto) {
        if (dto.getTeacherId() == null) throw new BizException("请选择上课教师");
        if (dto.getClassroomId() == null) throw new BizException("请选择上课教室");
        if (dto.getBeginTime() == null || dto.getEndTime() == null || dto.getBeginTime().isEmpty() || dto.getEndTime().isEmpty())
            throw new BizException("请选择上课时段");
        java.time.LocalTime t0, t1;
        try {
            t0 = java.time.LocalTime.parse(dto.getBeginTime());
            t1 = java.time.LocalTime.parse(dto.getEndTime());
        } catch (Exception e) { throw new BizException("上课时段格式不正确"); }
        if (!t1.isAfter(t0)) throw new BizException("结束时间必须晚于开始时间");
        if (dto.getBeginDate() == null) throw new BizException("请选择开始日期");
        boolean byCount = "count".equals(dto.getEndMode());
        if (byCount && (dto.getEndCount() == null || dto.getEndCount() < 1)) throw new BizException("请填写排课次数");
        if (!byCount && dto.getEndDate() == null) throw new BizException("请选择结束日期");
        if (!byCount && dto.getEndDate().isBefore(dto.getBeginDate())) throw new BizException("结束日期不能早于开始日期");
        if (dto.getRepeatMode() == null) throw new BizException("请选择重复方式");
        if (dto.getRepeatMode() != 4 && (dto.getWeekdays() == null || dto.getWeekdays().isEmpty()))
            throw new BizException("请选择周几上课");
    }

    /**
     * 重复排课：按规则逐次建课次。逐次走 create()（教师冲突/教室占用/禁排时段/过去时间各自校验），
     * 单次失败不中断整批——汇总返回 成功数 + 跳过明细（日期+原因），前端弹窗展示。
     */
    @Transactional
    public java.util.Map<String, Object> createRepeat(RepeatDTO dto) {
        validateRepeat(dto);
        java.util.Set<java.time.LocalDate> holidays = Boolean.TRUE.equals(dto.getSkipHoliday())
                ? holidaySet() : new java.util.HashSet<java.time.LocalDate>();
        java.util.List<java.time.LocalDate> dates = computeRepeatDates(dto);
        if (dates.isEmpty()) throw new BizException("按当前规则没有命中的上课日期，请调整重复规则");
        java.time.LocalTime t0 = java.time.LocalTime.parse(dto.getBeginTime());
        java.time.LocalTime t1 = java.time.LocalTime.parse(dto.getEndTime());
        int created = 0;
        java.util.List<java.util.Map<String, String>> skipped = new java.util.ArrayList<>();
        for (java.time.LocalDate d : dates) {
            if (Boolean.TRUE.equals(dto.getSkipHoliday()) && holidays.contains(d)) {
                skipped.add(skipItem(d, "节假日"));
                continue;
            }
            EduSchedule s = new EduSchedule();
            s.setClazzId(dto.getClazzId());
            s.setTeacherId(dto.getTeacherId());
            s.setAssistantId(dto.getAssistantId());
            s.setClassroomId(dto.getClassroomId());
            s.setTopic(dto.getTopic());
            s.setSchedType(1);
            s.setStartTime(d.atTime(t0));
            s.setEndTime(d.atTime(t1));
            try {
                create(s);
                created++;
            } catch (BizException e) {
                skipped.add(skipItem(d, e.getMessage()));
            }
        }
        java.util.Map<String, Object> r = new java.util.HashMap<>();
        r.put("created", created);
        r.put("total", dates.size());
        r.put("skipped", skipped);
        return r;
    }

    private java.util.Map<String, String> skipItem(java.time.LocalDate d, String reason) {
        java.util.Map<String, String> m = new java.util.HashMap<>();
        m.put("date", d.toString());
        m.put("reason", reason);
        return m;
    }

    /** 重复排课预览：预计排课次数 / 最后一次日期 / 节假日跳过数（弹窗"预计排课 N 次"提示） */
    public java.util.Map<String, Object> repeatPreview(RepeatDTO dto) {
        java.util.Map<String, Object> r = new java.util.HashMap<>();
        if (dto.getBeginDate() == null) { r.put("count", 0); return r; }
        java.util.Set<java.time.LocalDate> holidays = Boolean.TRUE.equals(dto.getSkipHoliday())
                ? holidaySet() : new java.util.HashSet<java.time.LocalDate>();
        java.util.List<java.time.LocalDate> dates = computeRepeatDates(dto);
        java.util.List<java.time.LocalDate> kept = new java.util.ArrayList<>();
        int holidaySkipped = 0;
        for (java.time.LocalDate d : dates) {
            if (Boolean.TRUE.equals(dto.getSkipHoliday()) && holidays.contains(d)) holidaySkipped++;
            else kept.add(d);
        }
        r.put("count", kept.size());
        r.put("holidaySkipped", holidaySkipped);
        r.put("lastDate", kept.isEmpty() ? null : kept.get(kept.size() - 1).toString());
        return r;
    }

    /**
     * 编辑排课（班级排课列表「编辑」）：时间/时段/教师/助教/教室/主题一并修改。
     * 仅待上课课次可编辑；时间或教师变化时按新值重新做 禁排/冲突/教室占用 校验。
     */
    @Transactional
    public void update(Long id, LocalDateTime startTime, LocalDateTime endTime, Long teacherId,
                       Long assistantId, Long classroomId, String topic) {
        EduSchedule s = mustGet(id);
        if (s.getStatus() == null || s.getStatus() != 0) throw new BizException("仅待上课的课次可编辑");
        Long tid = teacherId != null ? teacherId : s.getTeacherId();
        boolean timeChanged = startTime != null || endTime != null;
        LocalDateTime start = timeChanged ? (startTime != null ? startTime : s.getStartTime()) : s.getStartTime();
        LocalDateTime end = timeChanged ? (endTime != null ? endTime : s.getEndTime()) : s.getEndTime();
        // 时间未变化时允许编辑已开课课次的教师/助教/主题（编辑上课场景）；只有改时间才校验"不能调往过去"
        if (timeChanged) checkFutureStart(start);
        checkBanTime(tid, start, end);
        checkConflict(tid, start, end, id);
        Long roomId = classroomId != null ? classroomId : s.getClassroomId();
        String roomName = classroomId != null ? classroomName(classroomId) : s.getClassroom();
        checkRoomConflict(roomId, roomName, start, end, id, true);
        s.setTeacherId(tid);
        s.setAssistantId(assistantId);
        s.setTopic(topic == null || topic.trim().isEmpty() ? null : topic.trim());
        if (classroomId != null) {
            s.setClassroomId(classroomId);
            s.setClassroom(roomName);
        }
        s.setStartTime(start);
        s.setEndTime(end);
        s.setUpdateTime(LocalDateTime.now());
        scheduleMapper.updateById(s);
        opLog(s, "编辑排课", null);
    }

    /* ==================== 上课记录TAB：出勤统计聚合 + 未排课补录 ==================== */

    /**
     * 班级上课记录：课次行（已登记课次 status 1/3/4/5）+ 每课次出勤聚合 + 学员明细。
     * 到课=消课记录（未撤销、未驳回）逐学员计数与课时合计；请假=edu_leave；旷课=edu_absence；
     * 应到=班级在读学员数（当前口径）；迟到暂无数据恒 0。
     */
    public java.util.Map<String, Object> classRecords(Long clazzId, String begin, String end) {
        java.time.format.DateTimeFormatter fmt =
                java.time.format.DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss");
        LambdaQueryWrapper<EduSchedule> qw = new LambdaQueryWrapper<EduSchedule>()
                .eq(EduSchedule::getClazzId, clazzId)
                .in(EduSchedule::getStatus, 1, 3, 4, 5)
                .orderByDesc(EduSchedule::getStartTime);
        if (begin != null && !begin.isEmpty() && end != null && !end.isEmpty()) {
            qw.ge(EduSchedule::getStartTime, begin + " 00:00:00")
              .le(EduSchedule::getStartTime, end + " 23:59:59");
        }
        List<EduSchedule> list = scheduleMapper.selectList(qw);
        java.util.Map<Long, int[]> cnt = new java.util.HashMap<>();          // id -> [到课,请假,旷课]
        java.util.Map<Long, java.math.BigDecimal> hoursSum = new java.util.HashMap<>();
        java.util.Set<Long> ids = new java.util.HashSet<>();
        for (EduSchedule s : list) {
            ids.add(s.getId());
            cnt.put(s.getId(), new int[]{0, 0, 0});
            hoursSum.put(s.getId(), java.math.BigDecimal.ZERO);
        }
        java.util.List<java.util.Map<String, Object>> detail = new java.util.ArrayList<>();
        java.util.Set<Long> stuIds = new java.util.HashSet<>();
        if (!ids.isEmpty()) {
            // 到课：消课记录（未撤销、未驳回）
            for (com.edu.entity.EduConsume c : consumeRecMapper.selectList(
                    new LambdaQueryWrapper<com.edu.entity.EduConsume>()
                            .in(com.edu.entity.EduConsume::getScheduleId, ids)
                            .eq(com.edu.entity.EduConsume::getCanceled, 0)
                            .and(w -> w.isNull(com.edu.entity.EduConsume::getAuditStatus)
                                    .or().ne(com.edu.entity.EduConsume::getAuditStatus, 2)))) {
                int[] n = cnt.get(c.getScheduleId());
                if (n == null) continue;
                n[0]++;
                hoursSum.merge(c.getScheduleId(), c.getHours() == null ? java.math.BigDecimal.ZERO
                        : c.getHours(), java.math.BigDecimal::add);
                stuIds.add(c.getStudentId());
                java.util.Map<String, Object> d = new java.util.HashMap<>();
                d.put("scheduleId", c.getScheduleId());
                d.put("studentId", c.getStudentId());
                d.put("type", "到课");
                d.put("hours", c.getHours());
                d.put("remark", c.getRemark());
                detail.add(d);
            }
            // 请假
            for (com.edu.entity.EduLeave l : leaveRecMapper.selectList(
                    new LambdaQueryWrapper<com.edu.entity.EduLeave>()
                            .in(com.edu.entity.EduLeave::getScheduleId, ids))) {
                int[] n = cnt.get(l.getScheduleId());
                if (n == null) continue;
                n[1]++;
                stuIds.add(l.getStudentId());
                java.util.Map<String, Object> d = new java.util.HashMap<>();
                d.put("scheduleId", l.getScheduleId());
                d.put("studentId", l.getStudentId());
                d.put("type", "请假");
                d.put("hours", null);
                d.put("remark", l.getReason());
                detail.add(d);
            }
            // 旷课
            for (com.edu.entity.EduAbsence a : absenceMapper.selectList(
                    new LambdaQueryWrapper<com.edu.entity.EduAbsence>()
                            .in(com.edu.entity.EduAbsence::getScheduleId, ids))) {
                int[] n = cnt.get(a.getScheduleId());
                if (n == null) continue;
                n[2]++;
                stuIds.add(a.getStudentId());
                java.util.Map<String, Object> d = new java.util.HashMap<>();
                d.put("scheduleId", a.getScheduleId());
                d.put("studentId", a.getStudentId());
                d.put("type", "旷课");
                d.put("hours", null);
                d.put("remark", a.getReason());
                detail.add(d);
            }
        }
        // 应到：班级在读学员数
        Long should = enrollMapper.selectCount(new LambdaQueryWrapper<EduEnroll>()
                .eq(EduEnroll::getClazzId, clazzId).eq(EduEnroll::getStatus, 1));
        // 学员姓名
        java.util.Map<Long, String> names = new java.util.HashMap<>();
        if (!stuIds.isEmpty())
            for (com.edu.entity.EduStudent st : studentMapper.selectBatchIds(stuIds))
                names.put(st.getId(), st.getName());

        List<java.util.Map<String, Object>> rows = new java.util.ArrayList<>();
        for (EduSchedule s : list) {
            int[] n = cnt.get(s.getId());
            java.util.Map<String, Object> r = new java.util.HashMap<>();
            r.put("id", s.getId());
            r.put("startTime", s.getStartTime() == null ? null : s.getStartTime().format(fmt));
            r.put("endTime", s.getEndTime() == null ? null : s.getEndTime().format(fmt));
            r.put("teacherId", s.getTeacherId());
            r.put("assistantId", s.getAssistantId());
            r.put("status", s.getStatus());
            r.put("topic", s.getTopic());
            r.put("remark", s.getRemark());
            r.put("createTime", s.getCreateTime() == null ? null : s.getCreateTime().format(fmt));
            r.put("attend", n[0]);
            r.put("leave", n[1]);
            r.put("absent", n[2]);
            r.put("late", 0);
            r.put("should", should == null ? 0 : should.intValue());
            r.put("hours", hoursSum.get(s.getId()));
            rows.add(r);
        }
        for (java.util.Map<String, Object> d : detail) {
            d.put("studentName", names.getOrDefault((Long) d.get("studentId"), "-"));
            EduSchedule sch = list.stream().filter(x -> x.getId().equals(d.get("scheduleId"))).findFirst().orElse(null);
            d.put("time", sch == null || sch.getStartTime() == null ? null
                    : sch.getStartTime().format(java.time.format.DateTimeFormatter.ofPattern("MM-dd HH:mm")));
        }
        java.util.Map<String, Object> result = new java.util.HashMap<>();
        result.put("rows", rows);
        result.put("detail", detail);
        result.put("should", should == null ? 0 : should.intValue());
        return result;
    }

    /** 未排课记上课入参：上课信息 + 学员逐个出勤标记（1到课 2请假 3旷课） */
    @lombok.Data
    public static class RecordDTO {
        private Long clazzId;
        private Long teacherId;
        private Long assistantId;
        private Long classroomId;
        private String topic;
        /** 备注（记入课次备注，即上课记录的"上课内容"） */
        private String memo;
        private String date;
        private String beginTime;
        private String endTime;
        private List<StuAtt> students;
    }

    /** 单学员出勤：att 1到课（按 hours 扣课） 2请假（不扣课，写请假记录） 3旷课（生成旷课申请）；kind makeup=补课学员（扣本人合同） temp=临时学员（只登记名单不扣课） */
    @lombok.Data
    public static class StuAtt {
        private Long studentId;
        private Integer att;
        private Integer hours;
        private String kind;
        /** 到课类型附加标记：如"迟到"（写入消课台账备注 / 请假原因） */
        private String ext;
    }

    /**
     * 未排课记上课（按学员逐个登记）：补录历史课次，按学员标记到课/请假/旷课。
     * 补录的是历史事实，跳过未来/冲突/禁排校验；到课学员逐个走合同消课并同步扣报名课时；
     * 请假写 edu_leave（不扣课）；旷课生成 edu_absence 待审批（沿用 72 小时自动扣课规则）。
     */
    @Transactional
    public int record(RecordDTO d) {
        if (d.getClazzId() == null) throw new BizException("缺少班级");
        if (d.getTeacherId() == null) throw new BizException("请选择上课教师");
        if (d.getDate() == null || d.getDate().isEmpty()) throw new BizException("请选择上课日期");
        if (d.getStudents() == null || d.getStudents().isEmpty())
            throw new BizException("请先标记学员出勤（到课/请假/旷课）");
        LocalDateTime start, end;
        try {
            start = LocalDateTime.parse(d.getDate() + "T" + d.getBeginTime());
            end = LocalDateTime.parse(d.getDate() + "T" + d.getEndTime());
        } catch (Exception e) { throw new BizException("请选择完整的上课时段"); }
        if (!end.isAfter(start)) throw new BizException("结束时间必须晚于开始时间");
        EduClazz clazz = clazzMapper.selectById(d.getClazzId());
        if (clazz == null) throw new BizException("班级不存在");
        EduSchedule s = new EduSchedule();
        s.setTenantId(AuthContext.get().getTenantId());
        // 归属校区：继承创建人校区
        com.edu.security.LoginUser lu = AuthContext.get();
        if (lu != null && lu.getOrgId() != null) s.setOrgId(lu.getOrgId()); else s.setOrgId(1L);
        s.setClazzId(d.getClazzId());
        s.setCourseId(clazz.getCourseId());
        s.setTeacherId(d.getTeacherId());
        s.setAssistantId(d.getAssistantId());
        if (d.getClassroomId() != null) {
            s.setClassroomId(d.getClassroomId());
            s.setClassroom(classroomName(d.getClassroomId()));
        }
        s.setTopic(d.getTopic());
        s.setRemark(d.getMemo());
        s.setStartTime(start);
        s.setEndTime(end);
        s.setStatus(0);
        s.setSchedType(0);
        scheduleMapper.insert(s);
        int max = maxAttendHours(s);
        int contracts = 0;
        java.util.List<String> tempNames = new java.util.ArrayList<>();
        java.util.Map<Long, String> stuNames = new java.util.HashMap<>();
        if (!d.getStudents().isEmpty()) {
            java.util.Set<Long> sids = new java.util.HashSet<>();
            for (StuAtt a : d.getStudents())
                if (a.getStudentId() != null) sids.add(a.getStudentId());
            if (!sids.isEmpty())
                for (com.edu.entity.EduStudent st : studentMapper.selectBatchIds(sids))
                    stuNames.put(st.getId(), st.getName());
        }
        for (StuAtt a : d.getStudents()) {
            if (a.getStudentId() == null) continue;
            int att = a.getAtt() == null ? 1 : a.getAtt();
            int h = a.getHours() == null ? 0 : a.getHours();
            if (att == 1 && "temp".equals(a.getKind())) {   // 临时学员：到课但只登记名单，不扣课
                tempNames.add(stuNames.getOrDefault(a.getStudentId(), "#" + a.getStudentId()));
                continue;
            }
            if (att == 1) {   // 到课：单学员合同消课（补课学员扣本人合同）+ 本班报名课时同步扣减
                if (h < 1) throw new BizException("到课学员的扣课节数至少 1 节");
                if (h > max) throw new BizException("扣课节数超出上限（本课次时长折算最多 " + max + " 节）");
                contracts += consumeService.signByScheduleForStudent(s.getId(), a.getStudentId(),
                        java.math.BigDecimal.valueOf(h), "到课",
                        "迟到".equals(a.getExt()) && org.springframework.util.StringUtils.hasText(d.getMemo()) ? d.getMemo() + "（迟到）"
                                : ("迟到".equals(a.getExt()) ? "迟到" : d.getMemo()));
                EduEnroll e = enrollMapper.selectOne(new LambdaQueryWrapper<EduEnroll>()
                        .eq(EduEnroll::getStudentId, a.getStudentId())
                        .eq(EduEnroll::getClazzId, d.getClazzId())
                        .eq(EduEnroll::getStatus, 1).last("LIMIT 1"));
                if (e != null) enrollService.consumeHours(e.getId(), h);
            } else if (att == 2) {   // 请假：写请假记录，不扣课
                com.edu.entity.EduLeave l = new com.edu.entity.EduLeave();
                l.setTenantId(s.getTenantId());
                l.setStudentId(a.getStudentId());
                l.setScheduleId(s.getId());
                l.setReason("未排课补录·请假（不扣课）");
                l.setStatus(1);
                l.setCreateTime(LocalDateTime.now());
                leaveRecMapper.insert(l);
            } else if (att == 3) {   // 旷课：生成旷课申请（待审批，超时自动扣课）
                com.edu.entity.EduAbsence ab = new com.edu.entity.EduAbsence();
                ab.setTenantId(s.getTenantId());
                ab.setOrgId(s.getOrgId());
                ab.setScheduleId(s.getId());
                ab.setStudentId(a.getStudentId());
                ab.setTeacherId(s.getTeacherId());
                ab.setCourseId(s.getCourseId());
                ab.setClazzId(s.getClazzId());
                ab.setClassTime(start);
                ab.setReason("未排课补录·旷课");
                ab.setStatus(0);
                ab.setApplyTime(LocalDateTime.now());
                ab.setDeadline(LocalDateTime.now().plusHours(ABSENCE_DEADLINE_HOURS));
                absenceMapper.insert(ab);
            }
        }
        if (!tempNames.isEmpty()) {   // 临时学员名单记入课次备注（出勤统计不含，不扣课）
            String extra = "临时学员：" + String.join("、", tempNames) + "（不扣课）";
            s.setRemark(org.springframework.util.StringUtils.hasText(s.getRemark())
                    ? s.getRemark() + "；" + extra : extra);
        }
        s.setStatus(1);
        s.setUpdateTime(LocalDateTime.now());
        scheduleMapper.updateById(s);
        opLog(s, "记上课", "未排课补录·按学员登记");
        return contracts;
    }

    /**
     * 补课学员候选：同课程已分班的请假学员（edu_leave 记录 + 学生请假课次 status=4 并集，按学员去重取最近一次）。
     * 校区取学员归属校区，无则取其班级校区；请假时间取所缺课次的上课时间。
     */
    public java.util.List<java.util.Map<String, Object>> makeupCandidates(Long courseId, Long clazzId) {
        java.util.List<java.util.Map<String, Object>> out = new java.util.ArrayList<>();
        if (courseId == null) return out;
        java.time.format.DateTimeFormatter fmt =
                java.time.format.DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm");
        List<EduSchedule> courseSchedules = scheduleMapper.selectList(new LambdaQueryWrapper<EduSchedule>()
                .eq(EduSchedule::getCourseId, courseId)
                .isNotNull(EduSchedule::getClazzId)
                .eq(clazzId != null, EduSchedule::getClazzId, clazzId));
        if (courseSchedules.isEmpty()) return out;
        java.util.Map<Long, EduSchedule> schMap = new java.util.HashMap<>();
        for (EduSchedule s : courseSchedules) schMap.put(s.getId(), s);

        // studentId -> 候选行（时间靠后的覆盖）
        java.util.Map<Long, java.util.Map<String, Object>> byStu = new java.util.HashMap<>();
        java.util.function.BiConsumer<Long, EduSchedule> merge = (sid, sch) -> {
            if (sid == null || sch == null) return;
            java.util.Map<String, Object> r = byStu.get(sid);
            String time = sch.getStartTime() == null ? null : sch.getStartTime().format(fmt);
            if (r == null || String.valueOf(r.get("leaveTime")).compareTo(String.valueOf(time)) < 0) {
                java.util.Map<String, Object> row = new java.util.HashMap<>();
                row.put("studentId", sid);
                row.put("clazzId", sch.getClazzId());
                row.put("teacherId", sch.getTeacherId());
                row.put("leaveTime", time);
                byStu.put(sid, row);
            }
        };
        // 1) 学员请假记录（按课次）
        for (com.edu.entity.EduLeave l : leaveRecMapper.selectList(new LambdaQueryWrapper<com.edu.entity.EduLeave>()
                .in(com.edu.entity.EduLeave::getScheduleId, schMap.keySet())))
            merge.accept(l.getStudentId(), schMap.get(l.getScheduleId()));
        // 2) 学生请假课次（status=4 且带学员的一对一/点名课次）
        for (EduSchedule s : courseSchedules)
            if (s.getStatus() != null && s.getStatus() == 4 && s.getStudentId() != null)
                merge.accept(s.getStudentId(), s);
        if (byStu.isEmpty()) return out;

        // 名称解析：学员 / 班级 / 校区
        java.util.Map<Long, com.edu.entity.EduStudent> stus = new java.util.HashMap<>();
        for (com.edu.entity.EduStudent st : studentMapper.selectBatchIds(byStu.keySet())) stus.put(st.getId(), st);
        java.util.Map<Long, EduClazz> clazzes = new java.util.HashMap<>();
        for (EduClazz c : clazzMapper.selectBatchIds(
                new java.util.HashSet<>(java.util.Arrays.asList(
                        byStu.values().stream().map(m -> (Long) m.get("clazzId")).filter(java.util.Objects::nonNull).toArray(Long[]::new)))))
            clazzes.put(c.getId(), c);
        java.util.Map<Long, String> orgNames = new java.util.HashMap<>();
        for (com.edu.entity.SysOrg o : orgRecMapper.selectList(null)) orgNames.put(o.getId(), o.getName());

        for (java.util.Map<String, Object> row : byStu.values()) {
            com.edu.entity.EduStudent st = stus.get((Long) row.get("studentId"));
            if (st == null) continue;   // 学员已删除
            EduClazz cz = clazzes.get((Long) row.get("clazzId"));
            row.put("stuName", st.getName());
            row.put("clazzName", cz == null ? "-" : cz.getName());
            Long orgId = st.getOrgId() != null ? st.getOrgId() : (cz == null ? null : cz.getOrgId());
            row.put("campus", orgId == null ? "-" : orgNames.getOrDefault(orgId, "-"));
            out.add(row);
        }
        out.sort((a, b) -> String.valueOf(b.get("leaveTime")).compareTo(String.valueOf(a.get("leaveTime"))));
        return out;
    }

    /**
     * 已排课次的按学员登记上课（排课管理-编辑上课）：到课逐学员合同消课+报名课时扣减、请假写记录、旷课走审批，
     * 登记后课次置为已记上课（status=1）。仅未登记（status=0）课次可操作。
     */
    @Transactional
    public int markAttendance(Long scheduleId, List<StuAtt> students, String memo) {
        EduSchedule s = mustGet(scheduleId);
        if (s.getStatus() == null || s.getStatus() != 0) throw new BizException("仅未登记的课次可编辑上课");
        if (students == null || students.isEmpty()) throw new BizException("请先标记学员出勤");
        int max = maxAttendHours(s);
        int contracts = 0;
        java.util.List<String> tempNames = new java.util.ArrayList<>();
        java.util.Map<Long, String> stuNames = new java.util.HashMap<>();
        java.util.Set<Long> sids = new java.util.HashSet<>();
        for (StuAtt a : students) if (a.getStudentId() != null) sids.add(a.getStudentId());
        if (!sids.isEmpty())
            for (com.edu.entity.EduStudent st : studentMapper.selectBatchIds(sids))
                stuNames.put(st.getId(), st.getName());
        for (StuAtt a : students) {
            if (a.getStudentId() == null) continue;
            int att = a.getAtt() == null ? 1 : a.getAtt();
            int h = a.getHours() == null ? 0 : a.getHours();
            if (att == 1 && "temp".equals(a.getKind())) {   // 临时学员：仅留名单不扣课
                tempNames.add(stuNames.getOrDefault(a.getStudentId(), "#" + a.getStudentId()));
                continue;
            }
            if (att == 1) {   // 到课：单学员消课（班课按学员+班级、一对一按学员+课程）+ 报名课时扣减
                if (h < 1) throw new BizException("到课学员的扣课节数至少 1 节");
                if (h > max) throw new BizException("扣课节数超出上限（本课次时长折算最多 " + max + " 节）");
                contracts += consumeService.signByScheduleForStudent(s.getId(), a.getStudentId(),
                        java.math.BigDecimal.valueOf(h), "到课", "迟到".equals(a.getExt()) ? "迟到" : null);
                EduEnroll e = enrollMapper.selectOne(new LambdaQueryWrapper<EduEnroll>()
                        .eq(EduEnroll::getStudentId, a.getStudentId())
                        .eq(s.getClazzId() != null, EduEnroll::getClazzId, s.getClazzId())
                        .eq(s.getClazzId() == null, EduEnroll::getCourseId, s.getCourseId())
                        .eq(EduEnroll::getStatus, 1).last("LIMIT 1"));
                if (e != null) enrollService.consumeHours(e.getId(), h);
            } else if (att == 2) {   // 请假：不扣课
                com.edu.entity.EduLeave l = new com.edu.entity.EduLeave();
                l.setTenantId(s.getTenantId());
                l.setStudentId(a.getStudentId());
                l.setScheduleId(s.getId());
                l.setReason("排课管理登记·请假（不扣课）" + ("迟到".equals(a.getExt()) ? "；迟到" : ""));
                l.setStatus(1);
                l.setCreateTime(LocalDateTime.now());
                leaveRecMapper.insert(l);
            } else if (att == 3) {   // 旷课：走审批
                com.edu.entity.EduAbsence ab = new com.edu.entity.EduAbsence();
                ab.setTenantId(s.getTenantId());
                ab.setOrgId(s.getOrgId());
                ab.setScheduleId(s.getId());
                ab.setStudentId(a.getStudentId());
                ab.setTeacherId(s.getTeacherId());
                ab.setCourseId(s.getCourseId());
                ab.setClazzId(s.getClazzId());
                ab.setClassTime(s.getStartTime());
                ab.setReason("排课管理登记·旷课");
                ab.setStatus(0);
                ab.setApplyTime(LocalDateTime.now());
                ab.setDeadline(LocalDateTime.now().plusHours(ABSENCE_DEADLINE_HOURS));
                absenceMapper.insert(ab);
            }
        }
        if (org.springframework.util.StringUtils.hasText(memo))
            s.setRemark(memo.trim());   // 上课备注（上课信息区填写，记入课次备注即"上课内容"）
        if (!tempNames.isEmpty()) {
            String extra = "临时学员：" + String.join("、", tempNames) + "（不扣课）";
            s.setRemark(org.springframework.util.StringUtils.hasText(s.getRemark())
                    ? s.getRemark() + "；" + extra : extra);
        }
        s.setStatus(1);
        s.setUpdateTime(LocalDateTime.now());
        scheduleMapper.updateById(s);
        opLog(s, "记上课", "排课管理按学员登记");
        return contracts;
    }

    /** 课次出勤摘要：应到（班课在读人数/一对一 1）与实到（消课人数） */
    public java.util.Map<String, Object> attendSummary(Long id) {
        EduSchedule s = mustGet(id);
        int should = 1;
        if (s.getClazzId() != null) {
            Long n = enrollMapper.selectCount(new LambdaQueryWrapper<EduEnroll>()
                    .eq(EduEnroll::getClazzId, s.getClazzId()).eq(EduEnroll::getStatus, 1));
            should = n == null ? 0 : n.intValue();
        }
        int attend = 0;
        for (com.edu.entity.EduConsume c : consumeRecMapper.selectList(
                new LambdaQueryWrapper<com.edu.entity.EduConsume>()
                        .eq(com.edu.entity.EduConsume::getScheduleId, id)
                        .eq(com.edu.entity.EduConsume::getCanceled, 0)
                        .and(w -> w.isNull(com.edu.entity.EduConsume::getAuditStatus)
                                .or().ne(com.edu.entity.EduConsume::getAuditStatus, 2))))
            attend++;
        java.util.Map<String, Object> m = new java.util.HashMap<>();
        m.put("should", should);
        m.put("attend", attend);
        return m;
    }

    /**
     * 编辑重复系列：以该课次定位同系列（同班级+同教师+重复排课生成+同周几+同时段，今天及以后、待上课），
     * 统一改时段（新开始/结束时刻，日期保持各自日期），可同时换教师/教室；逐次校验，冲突跳过并汇总。
     */
    @Transactional
    public java.util.Map<String, Object> repeatUpdate(Long id, LocalDateTime newStart, LocalDateTime newEnd,
                                                      Long teacherId, Long classroomId) {
        EduSchedule s = mustGet(id);
        if (s.getStatus() == null || s.getStatus() != 0) throw new BizException("仅待上课的课次可编辑重复");
        if (s.getSchedType() == null || s.getSchedType() != 1) throw new BizException("该课次不是重复排课生成的，请用「编辑单次」");
        if (newStart == null || newEnd == null || !newEnd.isAfter(newStart))
            throw new BizException("请选择完整的上课时段");
        java.time.LocalTime oldT = s.getStartTime().toLocalTime();
        java.time.LocalTime newT = newStart.toLocalTime();
        long dur = java.time.Duration.between(newStart, newEnd).toMinutes();
        String today = java.time.LocalDate.now().toString() + " 00:00:00";
        java.util.List<EduSchedule> series = new java.util.ArrayList<>();
        for (EduSchedule it : scheduleMapper.selectList(new LambdaQueryWrapper<EduSchedule>()
                .eq(EduSchedule::getClazzId, s.getClazzId())
                .eq(EduSchedule::getTeacherId, s.getTeacherId())
                .eq(EduSchedule::getSchedType, 1)
                .eq(EduSchedule::getStatus, 0)
                .ge(EduSchedule::getStartTime, today))) {
            if (it.getStartTime().getDayOfWeek() != s.getStartTime().getDayOfWeek()) continue;
            if (!it.getStartTime().toLocalTime().equals(oldT)) continue;
            series.add(it);
        }
        if (series.isEmpty()) throw new BizException("未找到同系列课次");
        Long tid = teacherId != null ? teacherId : s.getTeacherId();
        Long roomId = classroomId != null ? classroomId : s.getClassroomId();
        String roomName = classroomId != null ? classroomName(classroomId) : s.getClassroom();
        int updated = 0;
        java.util.List<java.util.Map<String, String>> skipped = new java.util.ArrayList<>();
        for (EduSchedule it : series) {
            LocalDateTime ns = LocalDateTime.of(it.getStartTime().toLocalDate(), newT);
            LocalDateTime ne = ns.plusMinutes(dur);
            try {
                checkFutureStart(ns);
                checkBanTime(tid, ns, ne);
                checkConflict(tid, ns, ne, it.getId());
                checkRoomConflict(roomId, roomName, ns, ne, it.getId(), true);
            } catch (BizException e) {
                skipped.add(skipItem(it.getStartTime().toLocalDate(), e.getMessage()));
                continue;
            }
            it.setTeacherId(tid);
            if (classroomId != null) { it.setClassroomId(roomId); it.setClassroom(roomName); }
            it.setStartTime(ns);
            it.setEndTime(ne);
            it.setUpdateTime(LocalDateTime.now());
            scheduleMapper.updateById(it);
            opLog(it, "编辑重复", "系列自课次#" + id);
            updated++;
        }
        java.util.Map<String, Object> r = new java.util.HashMap<>();
        r.put("total", series.size());
        r.put("updated", updated);
        r.put("skipped", skipped);
        return r;
    }

    /** 排课操作日志：操作人记录当前登录账号的用户名（排课历史展示用） */
    private void opLog(EduSchedule s, String opType, String remark) {
        com.edu.entity.EduScheduleLog lg = new com.edu.entity.EduScheduleLog();
        lg.setTenantId(s.getTenantId());
        lg.setScheduleId(s.getId());
        lg.setOpType(opType);
        com.edu.security.LoginUser lu = AuthContext.get();
        lg.setOperator(lu == null ? "system" : lu.getUsername());
        lg.setOpTime(LocalDateTime.now());
        lg.setRemark(remark);
        opLogMapper.insert(lg);
    }

    /** 开始时间必须晚于当前系统时间：不允许新增/调整/补课到已过去的时间 */
    private void checkFutureStart(LocalDateTime start) {
        if (start == null || !start.isAfter(LocalDateTime.now()))
            throw new BizException("开始时间必须晚于当前系统时间，不能选择已过去的时间");
    }

    /**
     * 教室占用冲突：同教室时间重叠不能排课/调时。
     * 历史课次多仅存教室名称（classroom_id 为空），因此按教室 ID 或名称任一命中即视为占用；
     * 冲突时提示占用课次时段与当前空闲教室，引导用户换时段或换教室。
     */
    private void checkRoomConflict(Long classroomId, String classroomName,
                                   LocalDateTime start, LocalDateTime end, Long excludeId, boolean isAdjust) {
        boolean hasName = classroomName != null && !classroomName.trim().isEmpty();
        if (classroomId == null && !hasName) return;
        EduSchedule occ = scheduleMapper.selectOne(new LambdaQueryWrapper<EduSchedule>()
                .ne(EduSchedule::getStatus, 2)
                .ne(excludeId != null, EduSchedule::getId, excludeId)
                .lt(EduSchedule::getStartTime, end)
                .gt(EduSchedule::getEndTime, start)
                .and(w -> {
                    if (classroomId != null && hasName)
                        w.eq(EduSchedule::getClassroomId, classroomId).or().eq(EduSchedule::getClassroom, classroomName);
                    else if (classroomId != null) w.eq(EduSchedule::getClassroomId, classroomId);
                    else w.eq(EduSchedule::getClassroom, classroomName);
                })
                .orderByAsc(EduSchedule::getStartTime)
                .last("LIMIT 1"));
        if (occ == null) return;
        String range = rangeText(occ.getStartTime(), occ.getEndTime());
        String free = freeClassrooms(start, end);
        throw new BizException("该教室在 " + range + " 已排课" + (isAdjust ? "，本次调整未生效" : "")
                + (free.isEmpty() ? "，请更换时段" : "，请更换时段或改用空闲教室：" + free));
    }

    /** 占用时段文案：同日省略第二个日期（10-30 14:00~16:00），跨日两边都带日期 */
    private String rangeText(LocalDateTime s, LocalDateTime e) {
        java.time.format.DateTimeFormatter dt = java.time.format.DateTimeFormatter.ofPattern("MM-dd HH:mm");
        java.time.format.DateTimeFormatter tm = java.time.format.DateTimeFormatter.ofPattern("HH:mm");
        return s.format(dt) + "~" + (e.toLocalDate().equals(s.toLocalDate()) ? e.format(tm) : e.format(dt));
    }

    /** 该时段空闲的教室名（最多3个），拼进冲突提示，方便直接换教室 */
    private String freeClassrooms(LocalDateTime start, LocalDateTime end) {
        List<com.edu.entity.EduClassroom> rooms = classroomMapper.selectList(
                new LambdaQueryWrapper<com.edu.entity.EduClassroom>()
                        .eq(com.edu.entity.EduClassroom::getDeleted, 0));
        if (rooms.isEmpty()) return "";
        List<EduSchedule> overlaps = scheduleMapper.selectList(new LambdaQueryWrapper<EduSchedule>()
                .ne(EduSchedule::getStatus, 2)
                .lt(EduSchedule::getStartTime, end)
                .gt(EduSchedule::getEndTime, start));
        java.util.Set<Long> busyIds = new java.util.HashSet<>();
        java.util.Set<String> busyNames = new java.util.HashSet<>();
        for (EduSchedule s : overlaps) {
            if (s.getClassroomId() != null) busyIds.add(s.getClassroomId());
            if (s.getClassroom() != null && !s.getClassroom().trim().isEmpty()) busyNames.add(s.getClassroom());
        }
        StringBuilder sb = new StringBuilder();
        int found = 0;
        for (com.edu.entity.EduClassroom r : rooms) {
            if (busyIds.contains(r.getId()) || busyNames.contains(r.getName())) continue;
            if (sb.length() > 0) sb.append("、");
            sb.append(r.getName());
            if (++found >= 3) break;
        }
        return sb.toString();
    }

    /** 按教室 ID 取名称（调时/补课换教室时回填 classroom 字段用） */
    private String classroomName(Long id) {
        com.edu.entity.EduClassroom r = classroomMapper.selectById(id);
        if (r == null) throw new BizException("所选教室不存在或已删除");
        return r.getName();
    }

    /** 教师固定禁排时段校验（PRD：全局生效，排课/空闲查询均过滤） */
    private void checkBanTime(Long teacherId, LocalDateTime start, LocalDateTime end) {
        com.edu.entity.EduTeacher t = teacherMapper.selectById(teacherId);
        if (t == null || !org.springframework.util.StringUtils.hasText(t.getBanTimes())) return;
        try {
            com.fasterxml.jackson.databind.ObjectMapper om = new com.fasterxml.jackson.databind.ObjectMapper();
            java.util.List<java.util.Map<String, Object>> bans = om.readValue(t.getBanTimes(),
                    om.getTypeFactory().constructCollectionType(java.util.List.class, java.util.Map.class));
            LocalDateTime cur = start;
            while (!cur.isAfter(end)) {
                int week = cur.getDayOfWeek().getValue(); // 1=周一
                for (java.util.Map<String, Object> b : bans) {
                    if (Integer.parseInt(String.valueOf(b.get("week"))) != week) continue;
                    String[] bs = String.valueOf(b.get("start")).split(":");
                    String[] be = String.valueOf(b.get("end")).split(":");
                    LocalDateTime banStart = cur.toLocalDate().atTime(Integer.parseInt(bs[0]), bs.length > 1 ? Integer.parseInt(bs[1]) : 0);
                    LocalDateTime banEnd = cur.toLocalDate().atTime(Integer.parseInt(be[0]), be.length > 1 ? Integer.parseInt(be[1]) : 0);
                    if (start.isBefore(banEnd) && end.isAfter(banStart))
                        throw new BizException("该教师在 " + cur.toLocalDate() + " 有固定禁排时段（"
                                + b.get("start") + "-" + b.get("end") + "），不能排课");
                }
                cur = cur.plusDays(1);
            }
        } catch (BizException e) { throw e; }
        catch (Exception ignore) { }
    }

    /** 教师时间段冲突检测（同教师时间重叠即冲突） */
    private void checkConflict(Long teacherId, LocalDateTime start, LocalDateTime end, Long excludeId) {
        Long cnt = scheduleMapper.selectCount(new LambdaQueryWrapper<EduSchedule>()
                .eq(EduSchedule::getTeacherId, teacherId)
                .ne(EduSchedule::getStatus, 2)
                .ne(excludeId != null, EduSchedule::getId, excludeId)
                .lt(EduSchedule::getStartTime, end)
                .gt(EduSchedule::getEndTime, start));
        if (cnt > 0) throw new BizException("该教师在此时间段已有排课，存在冲突");
    }

    private EduSchedule mustGet(Long id) {
        EduSchedule s = scheduleMapper.selectById(id);
        if (s == null) throw new BizException("课次不存在");
        return s;
    }
}
