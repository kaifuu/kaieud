package com.edu.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.edu.entity.*;
import com.edu.mapper.*;
import com.edu.security.AuthContext;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.util.HashMap;
import java.util.Map;

/** 首页概览：核心数量指标 */
@Service
@RequiredArgsConstructor
public class HomeService {

    private final EduStudentMapper studentMapper;
    private final EduTeacherMapper teacherMapper;
    private final EduClazzMapper clazzMapper;
    private final EduCourseMapper courseMapper;
    private final EduEnrollMapper enrollMapper;
    private final EduPaymentMapper paymentMapper;
    private final EduScheduleMapper scheduleMapper;

    public Map<String, Object> overview() {
        Long tenantId = AuthContext.get().getTenantId();
        boolean scoped = tenantId != null && tenantId != 0; // 超级管理员查看全部
        Map<String, Object> data = new HashMap<>();
        data.put("studentCount", studentMapper.selectCount(
                new LambdaQueryWrapper<EduStudent>().eq(scoped, EduStudent::getTenantId, tenantId)));
        data.put("teacherCount", teacherMapper.selectCount(
                new LambdaQueryWrapper<EduTeacher>().eq(scoped, EduTeacher::getTenantId, tenantId)
                        .eq(EduTeacher::getStatus, 1)));
        data.put("clazzCount", clazzMapper.selectCount(
                new LambdaQueryWrapper<EduClazz>().eq(scoped, EduClazz::getTenantId, tenantId)
                        .ne(EduClazz::getStatus, 3)));
        data.put("courseCount", courseMapper.selectCount(
                new LambdaQueryWrapper<EduCourse>().eq(scoped, EduCourse::getTenantId, tenantId)
                        .eq(EduCourse::getStatus, 1)));
        data.put("totalRevenue", paymentMapper.selectList(null).stream()
                .filter(p -> !scoped || tenantId.equals(p.getTenantId()))
                .map(EduPayment::getAmount).reduce(java.math.BigDecimal.ZERO, java.math.BigDecimal::add));
        data.put("studyingCount", enrollMapper.selectCount(
                new LambdaQueryWrapper<EduEnroll>().eq(EduEnroll::getStatus, 1)));
        LocalDateTime dayStart = LocalDate.now().atStartOfDay();
        LocalDateTime dayEnd = LocalDate.now().atTime(LocalTime.MAX);
        data.put("todaySchedules", scheduleMapper.selectCount(
                new LambdaQueryWrapper<EduSchedule>()
                        .eq(scoped, EduSchedule::getTenantId, tenantId)
                        .ge(EduSchedule::getStartTime, dayStart)
                        .le(EduSchedule::getStartTime, dayEnd)
                        .ne(EduSchedule::getStatus, 2)));
        return data;
    }
}
