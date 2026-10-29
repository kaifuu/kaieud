package com.edu.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.edu.common.exception.BizException;
import com.edu.entity.*;
import com.edu.mapper.*;
import com.edu.security.AuthContext;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.util.List;

/** 报名/订阅班课业务：订阅、退订、指派老师、课时管理 */
@Service
@RequiredArgsConstructor
public class EduEnrollService {

    private final EduEnrollMapper enrollMapper;
    private final EduCourseMapper courseMapper;
    private final EduClazzMapper clazzMapper;
    private final EduScheduleMapper scheduleMapper;
    private final EduStudentMapper studentMapper;
    private final EduTeacherMapper teacherMapper;

    /** 学生订阅班课（选课中心/班主任代订） */
    @Transactional
    public EduEnroll subscribe(Long studentId, Long courseId, Long clazzId, Long teacherId) {
        EduCourse course = courseMapper.selectById(courseId);
        if (course == null) throw new BizException("课程不存在");
        if (course.getStatus() != 1) throw new BizException("课程已下架");

        if (clazzId != null) {
            EduClazz clazz = clazzMapper.selectById(clazzId);
            if (clazz == null) throw new BizException("班级不存在");
            // 校验容量
            Long cnt = enrollMapper.selectCount(new LambdaQueryWrapper<EduEnroll>()
                    .eq(EduEnroll::getClazzId, clazzId).eq(EduEnroll::getStatus, 1));
            if (cnt >= clazz.getCapacity()) throw new BizException("班级名额已满");
        }

        // 防止重复订阅同一课程
        Long dup = enrollMapper.selectCount(new LambdaQueryWrapper<EduEnroll>()
                .eq(EduEnroll::getStudentId, studentId)
                .eq(EduEnroll::getCourseId, courseId)
                .eq(EduEnroll::getStatus, 1));
        if (dup > 0) throw new BizException("该学生已订阅此课程，请勿重复订阅");

        EduEnroll enroll = new EduEnroll();
        enroll.setTenantId(AuthContext.get().getTenantId());
        enroll.setStudentId(studentId);
        enroll.setCourseId(courseId);
        enroll.setClazzId(clazzId);
        enroll.setTeacherId(teacherId);
        enroll.setTotalHours(course.getTotalHours());
        enroll.setUsedHours(0);
        enroll.setPrice(course.getPrice());
        enroll.setDiscount(course.getDiscount() == null
                ? BigDecimal.valueOf(100) : course.getDiscount());
        enroll.setTotalAmount(course.getPrice()
                .multiply(BigDecimal.valueOf(course.getTotalHours()))
                .multiply(enroll.getDiscount()).divide(BigDecimal.valueOf(100)));
        enroll.setPaidAmount(BigDecimal.ZERO);
        enroll.setStatus(1);
        enrollMapper.insert(enroll);
        return enroll;
    }

    /** 取消订阅班课（在读->退费状态，课时不再消耗） */
    @Transactional
    public void unsubscribe(Long id) {
        EduEnroll enroll = mustGet(id);
        if (enroll.getStatus() != 1) throw new BizException("仅在读状态可退订");
        enroll.setStatus(3);
        enrollMapper.updateById(enroll);
        // 取消该生该课程未来待上的排课
        EduSchedule upd = new EduSchedule();
        upd.setStatus(2);
        upd.setCancelReason("退订课程自动取消");
        scheduleMapper.update(upd, new LambdaQueryWrapper<EduSchedule>()
                .eq(EduSchedule::getStudentId, enroll.getStudentId())
                .eq(EduSchedule::getCourseId, enroll.getCourseId())
                .eq(EduSchedule::getStatus, 0));
    }

    /** 为学生指派/更换授课老师 */
    @Transactional
    public void assignTeacher(Long enrollId, Long teacherId) {
        EduEnroll enroll = mustGet(enrollId);
        EduTeacher teacher = teacherMapper.selectById(teacherId);
        if (teacher == null || teacher.getStatus() != 1) throw new BizException("教师不存在或已离职");
        enroll.setTeacherId(teacherId);
        enrollMapper.updateById(enroll);
        // 同步更新该生该课程未来的排课
        EduSchedule upd = new EduSchedule();
        upd.setTeacherId(teacherId);
        scheduleMapper.update(upd, new LambdaQueryWrapper<EduSchedule>()
                .eq(EduSchedule::getStudentId, enroll.getStudentId())
                .eq(EduSchedule::getCourseId, enroll.getCourseId())
                .eq(EduSchedule::getStatus, 0));
    }

    /** 课时确认消耗：排课完成后调用 */
    @Transactional
    public void consumeHours(Long enrollId, int hours) {
        EduEnroll enroll = mustGet(enrollId);
        enroll.setUsedHours(enroll.getUsedHours() + hours);
        if (enroll.getUsedHours() >= enroll.getTotalHours()) enroll.setStatus(2);
        enrollMapper.updateById(enroll);
    }

    /** 补充因请假取消的课时次数：总课时+2，已消耗不变 */
    @Transactional
    public void compensateHours(Long enrollId, int hours) {
        EduEnroll total = mustGet(enrollId);
        total.setTotalHours(total.getTotalHours() + hours);
        total.setStatus(1); // 恢复在读
        enrollMapper.updateById(total);
    }

    private EduEnroll mustGet(Long id) {
        EduEnroll e = enrollMapper.selectById(id);
        if (e == null) throw new BizException("报名记录不存在");
        return e;
    }

    /** 学生在读报名记录 */
    public List<EduEnroll> myEnrolls(Long studentId) {
        return enrollMapper.selectList(new LambdaQueryWrapper<EduEnroll>()
                .eq(EduEnroll::getStudentId, studentId)
                .ne(EduEnroll::getStatus, 3)
                .orderByDesc(EduEnroll::getId));
    }
}
