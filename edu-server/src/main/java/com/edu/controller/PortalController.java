package com.edu.controller;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.edu.common.Result;
import com.edu.common.exception.BizException;
import com.edu.entity.*;
import com.edu.mapper.*;
import com.edu.security.AuthContext;
import com.edu.security.LoginUser;
import com.edu.service.EduEnrollService;
import lombok.Data;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/** 学生端门户：选课、我的课表、我的老师、咨询、评价 */
@RestController
@RequestMapping("/portal")
@RequiredArgsConstructor
public class PortalController {

    private final EduEnrollService enrollService;
    private final EduEnrollMapper enrollMapper;
    private final EduCourseMapper courseMapper;
    private final EduClazzMapper clazzMapper;
    private final EduScheduleMapper scheduleMapper;
    private final EduTeacherMapper teacherMapper;
    private final EduEvaluationMapper evaluationMapper;
    private final EduConsultMapper consultMapper;

    private Long studentId() {
        LoginUser lu = AuthContext.get();
        if (lu.getStudentId() == null) throw new BizException("当前账号未关联学生档案");
        return lu.getStudentId();
    }

    /** 选课中心：上架课程 + 开课中班级 */
    @GetMapping("/courses")
    public Result<Map<String, Object>> courses() {
        Map<String, Object> data = new HashMap<>();
        data.put("courses", courseMapper.selectList(
                new LambdaQueryWrapper<EduCourse>().eq(EduCourse::getStatus, 1)));
        data.put("clazzes", clazzMapper.selectList(
                new LambdaQueryWrapper<EduClazz>().ne(EduClazz::getStatus, 3)));
        data.put("myEnrolls", enrollService.myEnrolls(studentId()));
        return Result.ok(data);
    }

    /** 订阅班型 */
    @PostMapping("/subscribe")
    public Result<EduEnroll> subscribe(@RequestBody EduEnrollController.SubscribeDTO dto) {
        return Result.ok(enrollService.subscribe(studentId(), dto.getCourseId(),
                dto.getClazzId(), dto.getTeacherId()));
    }

    /** 我的课表 */
    @GetMapping("/my-schedule")
    public Result<List<EduSchedule>> mySchedule() {
        return Result.ok(scheduleMapper.selectList(new LambdaQueryWrapper<EduSchedule>()
                .eq(EduSchedule::getStudentId, studentId())
                .ne(EduSchedule::getStatus, 2)
                .orderByAsc(EduSchedule::getStartTime)));
    }

    /** 我的老师（在读课程对应的授课老师基本信息+联系方式） */
    @GetMapping("/my-teachers")
    public Result<List<Map<String, Object>>> myTeachers() {
        List<Map<String, Object>> result = new ArrayList<>();
        for (EduEnroll e : enrollService.myEnrolls(studentId())) {
            if (e.getTeacherId() == null) continue;
            EduTeacher t = teacherMapper.selectById(e.getTeacherId());
            if (t == null) continue;
            Map<String, Object> item = new HashMap<>();
            item.put("teacher", t);
            item.put("course", courseMapper.selectById(e.getCourseId()));
            item.put("remainHours", e.getTotalHours() - e.getUsedHours());
            result.add(item);
        }
        return Result.ok(result);
    }

    /** 我的报名记录 */
    @GetMapping("/my-enrolls")
    public Result<List<EduEnroll>> myEnrolls() {
        return Result.ok(enrollService.myEnrolls(studentId()));
    }

    /** 发起咨询 */
    @Data
    public static class ConsultDTO {
        private Long teacherId;
        private String content;
    }

    @PostMapping("/consult")
    public Result<Void> consult(@RequestBody ConsultDTO dto) {
        EduConsult c = new EduConsult();
        c.setTenantId(AuthContext.get().getTenantId());
        c.setStudentId(studentId());
        c.setTeacherId(dto.getTeacherId());
        c.setContent(dto.getContent());
        c.setStatus(0);
        consultMapper.insert(c);
        return Result.ok();
    }

    /** 提交评价 */
    @Data
    public static class EvalDTO {
        private Long teacherId;
        private Long courseId;
        private Integer score;
        private String content;
    }

    @PostMapping("/evaluation")
    public Result<Void> evaluation(@RequestBody EvalDTO dto) {
        EduEvaluation ev = new EduEvaluation();
        ev.setTenantId(AuthContext.get().getTenantId());
        ev.setStudentId(studentId());
        ev.setTeacherId(dto.getTeacherId());
        ev.setCourseId(dto.getCourseId());
        ev.setScore(dto.getScore() == null ? 5 : dto.getScore());
        ev.setContent(dto.getContent());
        evaluationMapper.insert(ev);
        return Result.ok();
    }

    /** 我的咨询记录 */
    @GetMapping("/my-consults")
    public Result<List<EduConsult>> myConsults() {
        return Result.ok(consultMapper.selectList(new LambdaQueryWrapper<EduConsult>()
                .eq(EduConsult::getStudentId, studentId())
                .orderByDesc(EduConsult::getId)));
    }
}
