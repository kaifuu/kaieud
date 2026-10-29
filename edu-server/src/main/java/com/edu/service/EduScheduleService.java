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
    private final EduEnrollService enrollService;

    /** 新增排课，校验教师时间冲突 */
    @Transactional
    public EduSchedule create(EduSchedule s) {
        checkBanTime(s.getTeacherId(), s.getStartTime(), s.getEndTime());
        checkConflict(s.getTeacherId(), s.getStartTime(), s.getEndTime(), null);
        s.setTenantId(AuthContext.get().getTenantId());
        // 教室占用校验：同教室同时段不能重复排课
        if (s.getClassroomId() != null) {
            checkRoomConflict(s.getClassroomId(), s.getStartTime(), s.getEndTime(), null);
        }
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
        return s;
    }

    /** 调整上课时间（班主任/教管为学生或老师调时） */
    @Transactional
    public void adjustTime(Long id, LocalDateTime startTime, LocalDateTime endTime, String reason) {
        EduSchedule s = mustGet(id);
        if (s.getStatus() != 0) throw new BizException("仅待上课的课次可调整时间");
        checkBanTime(s.getTeacherId(), startTime, endTime);
        checkConflict(s.getTeacherId(), startTime, endTime, id);
        s.setStartTime(startTime);
        s.setEndTime(endTime);
        s.setRemark("调整时间：" + (reason == null ? "" : reason));
        scheduleMapper.updateById(s);
    }

    /** 取消课次 */
    @Transactional
    public void cancel(Long id, String reason) {
        EduSchedule s = mustGet(id);
        if (s.getStatus() != 0) throw new BizException("仅待上课的课次可取消");
        s.setStatus(2);
        s.setCancelReason(reason);
        scheduleMapper.updateById(s);
    }

    /** 完成课次：扣减学生课时 */
    @Transactional
    public void complete(Long id) {
        EduSchedule s = mustGet(id);
        if (s.getStatus() != 0) throw new BizException("仅待上课的课次可标记完成");
        s.setStatus(1);
        scheduleMapper.updateById(s);
        // 班课按班级在读学生批量扣课时；一对一按学生扣
        if (s.getClazzId() != null) {
            List<EduEnroll> enrolls = enrollMapper.selectList(new LambdaQueryWrapper<EduEnroll>()
                    .eq(EduEnroll::getClazzId, s.getClazzId())
                    .eq(EduEnroll::getStatus, 1));
            for (EduEnroll e : enrolls) enrollService.consumeHours(e.getId(), 1);
        } else if (s.getStudentId() != null) {
            EduEnroll e = enrollMapper.selectOne(new LambdaQueryWrapper<EduEnroll>()
                    .eq(EduEnroll::getStudentId, s.getStudentId())
                    .eq(EduEnroll::getCourseId, s.getCourseId())
                    .eq(EduEnroll::getStatus, 1).last("LIMIT 1"));
            if (e != null) enrollService.consumeHours(e.getId(), 1);
        }
    }

    /** 补课：基于已取消课次生成新课次 */
    @Transactional
    public EduSchedule compensate(Long originId, LocalDateTime start, LocalDateTime end) {
        EduSchedule origin = mustGet(originId);
        if (origin.getStatus() != 2) throw new BizException("仅已取消的课次可安排补课");
        checkConflict(origin.getTeacherId(), start, end, null);
        EduSchedule neu = new EduSchedule();
        neu.setTenantId(origin.getTenantId());
        neu.setCourseId(origin.getCourseId());
        neu.setClazzId(origin.getClazzId());
        neu.setTeacherId(origin.getTeacherId());
        neu.setStudentId(origin.getStudentId());
        neu.setClassroom(origin.getClassroom());
        neu.setStartTime(start);
        neu.setEndTime(end);
        neu.setStatus(0);
        neu.setOriginId(origin.getId());
        neu.setCompensation(1);
        scheduleMapper.insert(neu);
        return neu;
    }

    /** 教室占用冲突：同教室时间重叠不能排课 */
    private void checkRoomConflict(Long classroomId, LocalDateTime start, LocalDateTime end, Long excludeId) {
        Long cnt = scheduleMapper.selectCount(new LambdaQueryWrapper<EduSchedule>()
                .eq(classroomId != null, EduSchedule::getClassroomId, classroomId)
                .ne(EduSchedule::getStatus, 2)
                .ne(excludeId != null, EduSchedule::getId, excludeId)
                .lt(EduSchedule::getStartTime, end)
                .gt(EduSchedule::getEndTime, start));
        if (cnt > 0) throw new BizException("该教室在此时间段已被占用，请更换教室或时间");
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
