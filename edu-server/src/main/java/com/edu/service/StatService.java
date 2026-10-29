package com.edu.service;

import com.edu.mapper.StatMapper;
import com.edu.security.AuthContext;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.Map;

/** 统计汇总：收入/课时/教师投入量与闲置量 */
@Service
@RequiredArgsConstructor
public class StatService {

    private final StatMapper statMapper;

    /** 综合统计（收入维度+教师投入闲置+课时占比） */
    public Map<String, Object> summary(String begin, String end) {
        Long tenantId = AuthContext.get().getTenantId();
        if (tenantId != null && tenantId == 0) tenantId = null; // 超管查看全部
        Map<String, Object> result = new java.util.HashMap<>();
        result.put("revenueBySchool", statMapper.revenueBySchool(tenantId, begin, end));
        result.put("revenueByTeacher", statMapper.revenueByTeacher(tenantId, begin, end));
        result.put("revenueByStudent", statMapper.revenueByStudent(tenantId, begin, end));
        result.put("teacherLoad", statMapper.teacherLoad(tenantId, begin, end));
        result.put("hoursShare", statMapper.hoursShareByCourse(tenantId));
        // 近7日缴费趋势
        String trendBegin = java.time.LocalDate.now().minusDays(6).toString();
        String trendEnd = java.time.LocalDate.now().plusDays(1).toString();
        result.put("revenueTrend", statMapper.revenueTrend(tenantId, trendBegin, trendEnd));
        return result;
    }
}
