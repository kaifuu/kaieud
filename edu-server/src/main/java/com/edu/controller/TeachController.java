package com.edu.controller;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.edu.common.Result;
import com.edu.common.exception.BizException;
import com.edu.entity.*;
import com.edu.mapper.*;
import com.edu.security.AuthContext;
import com.edu.security.LoginUser;
import lombok.Data;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/** 教师端：我的课表、我的学生、评价、咨询(回复)、收入 */
@RestController
@RequestMapping("/teach")
@RequiredArgsConstructor
public class TeachController {

    private final EduScheduleMapper scheduleMapper;
    private final EduEnrollMapper enrollMapper;
    private final EduEvaluationMapper evaluationMapper;
    private final EduConsultMapper consultMapper;
    private final EduStudentMapper studentMapper;
    private final EduCourseMapper courseMapper;

    private Long teacherId() {
        LoginUser lu = AuthContext.get();
        if (lu.getTeacherId() == null) throw new BizException("当前账号未关联教师档案");
        return lu.getTeacherId();
    }

    /** 我的课表 */
    @GetMapping("/my-schedule")
    public Result<List<EduSchedule>> mySchedule() {
        return Result.ok(scheduleMapper.selectList(new LambdaQueryWrapper<EduSchedule>()
                .eq(EduSchedule::getTeacherId, teacherId())
                .orderByAsc(EduSchedule::getStartTime)));
    }

    /** 我所带的学生（在读报名 + 课时/折扣信息） */
    @GetMapping("/my-students")
    public Result<List<Map<String, Object>>> myStudents() {
        List<Map<String, Object>> result = new ArrayList<>();
        List<EduEnroll> enrolls = enrollMapper.selectList(new LambdaQueryWrapper<EduEnroll>()
                .eq(EduEnroll::getTeacherId, teacherId())
                .ne(EduEnroll::getStatus, 3)
                .orderByDesc(EduEnroll::getId));
        for (EduEnroll e : enrolls) {
            Map<String, Object> item = new HashMap<>();
            item.put("enroll", e);
            item.put("student", studentMapper.selectById(e.getStudentId()));
            item.put("course", courseMapper.selectById(e.getCourseId()));
            result.add(item);
        }
        return Result.ok(result);
    }

    /** 我收到的评价 */
    @GetMapping("/my-evaluations")
    public Result<List<EduEvaluation>> myEvaluations() {
        return Result.ok(evaluationMapper.selectList(new LambdaQueryWrapper<EduEvaluation>()
                .eq(EduEvaluation::getTeacherId, teacherId())
                .orderByDesc(EduEvaluation::getId)));
    }

    /** 咨询列表（待回复/全部） */
    @GetMapping("/my-consults")
    public Result<List<Map<String, Object>>> myConsults() {
        List<Map<String, Object>> result = new ArrayList<>();
        List<EduConsult> consults = consultMapper.selectList(new LambdaQueryWrapper<EduConsult>()
                .eq(EduConsult::getTeacherId, teacherId())
                .orderByDesc(EduConsult::getId));
        for (EduConsult c : consults) {
            Map<String, Object> item = new HashMap<>();
            item.put("consult", c);
            item.put("student", studentMapper.selectById(c.getStudentId()));
            result.add(item);
        }
        return Result.ok(result);
    }

    /** 回复咨询 */
    @Data
    public static class ReplyDTO {
        private String reply;
    }

    @PostMapping("/consult/{id}/reply")
    public Result<Void> reply(@PathVariable Long id, @RequestBody ReplyDTO dto) {
        EduConsult c = consultMapper.selectById(id);
        if (c == null || !teacherId().equals(c.getTeacherId()))
            throw new BizException("咨询不存在");
        c.setReply(dto.getReply());
        c.setReplyTime(LocalDateTime.now());
        c.setStatus(1);
        consultMapper.updateById(c);
        return Result.ok();
    }

    /** 我的收入：所带学生总体课程收入、折扣、课时消耗 */
    @GetMapping("/my-income")
    public Result<Map<String, Object>> myIncome() {
        List<EduEnroll> enrolls = enrollMapper.selectList(new LambdaQueryWrapper<EduEnroll>()
                .eq(EduEnroll::getTeacherId, teacherId())
                .ne(EduEnroll::getStatus, 3));
        BigDecimal totalAmount = BigDecimal.ZERO;
        BigDecimal paidAmount = BigDecimal.ZERO;
        BigDecimal avgDiscount = BigDecimal.ZERO;
        int totalHours = 0;
        int usedHours = 0;
        for (EduEnroll e : enrolls) {
            totalAmount = totalAmount.add(e.getTotalAmount() == null ? BigDecimal.ZERO : e.getTotalAmount());
            paidAmount = paidAmount.add(e.getPaidAmount() == null ? BigDecimal.ZERO : e.getPaidAmount());
            avgDiscount = avgDiscount.add(e.getDiscount() == null ? BigDecimal.valueOf(100) : e.getDiscount());
            totalHours += e.getTotalHours() == null ? 0 : e.getTotalHours();
            usedHours += e.getUsedHours() == null ? 0 : e.getUsedHours();
        }
        if (!enrolls.isEmpty()) {
            avgDiscount = avgDiscount.divide(BigDecimal.valueOf(enrolls.size()), 1, RoundingMode.HALF_UP);
        }
        Map<String, Object> data = new HashMap<>();
        data.put("totalAmount", totalAmount);
        data.put("paidAmount", paidAmount);
        data.put("avgDiscount", avgDiscount);
        data.put("totalHours", totalHours);
        data.put("usedHours", usedHours);
        data.put("studentCount", enrolls.size());
        return Result.ok(data);
    }
}
