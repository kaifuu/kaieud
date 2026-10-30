package com.edu.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.edu.common.exception.BizException;
import com.edu.entity.EduEnroll;
import com.edu.entity.EduSchedule;
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
    private final com.edu.mapper.EduTeacherMapper teacherMapper;
    private final com.edu.mapper.EduStudentMapper studentMapper;
    private final com.edu.mapper.EduCourseMapper courseMapper;
    private final com.edu.mapper.EduClassroomMapper classroomMapper;
    private final EduEnrollService enrollService;
    private final com.edu.mapper.EduAbsenceMapper absenceMapper;
    private final EduConsumeService consumeService;
    private final com.edu.mapper.EduScheduleLogMapper opLogMapper;

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
        s.setStatus(0);
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
