package com.edu.controller;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.edu.common.Log;
import com.edu.common.PageResult;
import com.edu.common.SafeSort;
import com.edu.common.Result;
import com.edu.common.exception.BizException;
import com.edu.entity.*;
import com.edu.mapper.*;
import com.edu.security.AuthContext;
import com.edu.security.LoginUser;
import com.edu.service.EduScheduleService;
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
    private final EduConsultMsgMapper consultMsgMapper;
    private final EduStudentMapper studentMapper;
    private final EduCourseMapper courseMapper;
    private final EduClazzMapper clazzMapper;
    private final EduScheduleService scheduleService;

    private Long teacherId() {
        LoginUser lu = AuthContext.get();
        if (lu.getTeacherId() == null)
            throw new BizException("当前账号未关联教师档案（可在用户管理中开启\"兼任教师\"）");
        return lu.getTeacherId();
    }

    /**
     * 教师端课次登记操作（到课/教师请假/学生请假/旷课），与排课管理同口径：
     * 请假不扣课、不生成课时管理记录，仅排课历史留痕；到课生成消课记录；
     * 旷课生成旷课申请（批准免扣课，72小时内未批准自动扣课）。
     * 仅允许操作本人课次；请假限未开始的课次，到课/旷课限已下课的课次。
     */
    private EduSchedule mySchedule(Long id) {
        EduSchedule s = scheduleMapper.selectById(id);
        if (s == null || !teacherId().equals(s.getTeacherId()))
            throw new BizException("课次不存在或不是您的课次");
        return s;
    }

    /** 到课登记信息：可扣节数上限（排课时长÷每节时长折算）等，弹窗展示用 */
    @GetMapping("/schedule/{id}/attend-info")
    public Result<Map<String, Object>> scheduleAttendInfo(@PathVariable Long id) {
        EduSchedule s = mySchedule(id);
        Map<String, Object> m = new HashMap<>();
        m.put("maxHours", scheduleService.maxAttendHours(s));
        m.put("minutes", scheduleService.scheduleMinutes(s));
        m.put("durationMinutes", scheduleService.perLessonMinutes(s));
        return Result.ok(m);
    }

    /** 到课登记：已下课的课次按所选节数扣课（生成课时管理消课记录） */
    @PostMapping("/schedule/{id}/attend")
    @Log("教师端到课登记")
    public Result<Integer> scheduleAttend(@PathVariable Long id, @RequestBody(required = false) AttendDTO dto) {
        EduSchedule s = mySchedule(id);
        if (s.getEndTime() == null || !s.getEndTime().isBefore(LocalDateTime.now()))
            throw new BizException("仅已下课（结束时间已过）的课次可登记到课");
        return Result.ok(scheduleService.attend(id,
                dto == null ? null : dto.getHours(), dto == null ? null : dto.getRemark()));
    }

    /** 请假登记：未开始的课次，type=3教师请假/4学生请假，均不扣课、仅在排课历史留痕 */
    @PostMapping("/schedule/{id}/leave")
    @Log("教师端请假登记")
    public Result<Void> scheduleLeave(@PathVariable Long id, @RequestParam int type) {
        EduSchedule s = mySchedule(id);
        if (s.getStartTime() == null || !s.getStartTime().isAfter(LocalDateTime.now()))
            throw new BizException("仅未开始（开始时间在未来）的课次可登记请假");
        scheduleService.leave(id, type);
        return Result.ok();
    }

    /** 旷课登记：已下课的课次，生成旷课申请（批准免扣课，72小时内未批准自动扣课） */
    @PostMapping("/schedule/{id}/absent")
    @Log("教师端旷课登记")
    public Result<Void> scheduleAbsent(@PathVariable Long id, @RequestBody(required = false) ReasonDTO dto) {
        EduSchedule s = mySchedule(id);
        if (s.getEndTime() == null || !s.getEndTime().isBefore(LocalDateTime.now()))
            throw new BizException("仅已下课（结束时间已过）的课次可登记旷课");
        scheduleService.absent(id, dto == null ? null : dto.getReason());
        return Result.ok();
    }

    @Data
    public static class AttendDTO {
        private Integer hours;
        /** 扣课说明：扣减节数少于本课次应扣节数时必填 */
        private String remark;
    }

    @Data
    public static class ReasonDTO {
        private String reason;
    }

    /**
     * 我的课表（教师端只读）：附带课程/班级/学员名称与格式化时间，供日历展示；
     * begin/end 为日历当前视图的日期范围（可缺省取全部）。
     */
    @GetMapping("/my-schedule")
    public Result<List<Map<String, Object>>> mySchedule(@RequestParam(required = false) String begin,
                                                        @RequestParam(required = false) String end) {
        LambdaQueryWrapper<EduSchedule> qw = new LambdaQueryWrapper<EduSchedule>()
                .eq(EduSchedule::getTeacherId, teacherId())
                .ge(begin != null, EduSchedule::getStartTime, begin + " 00:00:00")
                .le(end != null, EduSchedule::getStartTime, end + " 23:59:59")
                .orderByAsc(EduSchedule::getStartTime);
        java.time.format.DateTimeFormatter fmt =
                java.time.format.DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss");
        List<Map<String, Object>> result = new ArrayList<>();
        for (EduSchedule s : scheduleMapper.selectList(qw)) {
            Map<String, Object> m = new HashMap<>();
            m.put("id", s.getId());
            m.put("start", s.getStartTime() == null ? null : s.getStartTime().format(fmt));
            m.put("end", s.getEndTime() == null ? null : s.getEndTime().format(fmt));
            m.put("classroom", s.getClassroom());
            m.put("status", s.getStatus());
            m.put("remark", s.getRemark());
            // 展示名称：班课取班级名，一对一取学员名
            if (s.getClazzId() != null) {
                com.edu.entity.EduClazz c = clazzMapper.selectById(s.getClazzId());
                m.put("name", c == null ? "-" : c.getName());
                m.put("classType", "班课");
            } else if (s.getStudentId() != null) {
                EduStudent st = studentMapper.selectById(s.getStudentId());
                m.put("name", st == null ? "-" : st.getName());
                m.put("classType", "一对一");
            } else {
                m.put("name", "-");
                m.put("classType", "-");
            }
            EduCourse c2 = s.getCourseId() == null ? null : courseMapper.selectById(s.getCourseId());
            m.put("courseName", c2 == null ? "-" : c2.getName());
            result.add(m);
        }
        return Result.ok(result);
    }

    /**
     * 我的排课记录（我的记录TAB）：教师本人全部课次分页列表，
     * 支持状态/时间范围筛选与排序（白名单 startTime/endTime/status），默认按上课时间倒序。
     */
    @GetMapping("/my-schedule-records")
    public Result<PageResult<Map<String, Object>>> myScheduleRecords(
            @RequestParam(defaultValue = "1") long page,
            @RequestParam(defaultValue = "10") long size,
            @RequestParam(required = false) Integer status,
            @RequestParam(required = false) String begin,
            @RequestParam(required = false) String end,
            @RequestParam(required = false) String sortBy,
            @RequestParam(required = false) String direction) {
        LambdaQueryWrapper<EduSchedule> qw = new LambdaQueryWrapper<EduSchedule>()
                .eq(EduSchedule::getTeacherId, teacherId())
                .eq(status != null, EduSchedule::getStatus, status)
                .ge(begin != null, EduSchedule::getStartTime, begin + " 00:00:00")
                .le(end != null, EduSchedule::getStartTime, end + " 23:59:59")
                .last(SafeSort.order(sortBy, direction, "startTime", "startTime", "endTime", "status"));
        Page<EduSchedule> p = scheduleMapper.selectPage(new Page<>(page, size), qw);
        java.time.format.DateTimeFormatter fmt =
                java.time.format.DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss");
        List<Map<String, Object>> result = new ArrayList<>();
        for (EduSchedule s : p.getRecords()) {
            Map<String, Object> m = new HashMap<>();
            m.put("id", s.getId());
            m.put("start", s.getStartTime() == null ? null : s.getStartTime().format(fmt));
            m.put("end", s.getEndTime() == null ? null : s.getEndTime().format(fmt));
            m.put("classroom", s.getClassroom());
            m.put("status", s.getStatus());
            m.put("remark", s.getRemark());
            if (s.getClazzId() != null) {
                com.edu.entity.EduClazz c = clazzMapper.selectById(s.getClazzId());
                m.put("name", c == null ? "-" : c.getName());
                m.put("classType", "班课");
            } else if (s.getStudentId() != null) {
                EduStudent st = studentMapper.selectById(s.getStudentId());
                m.put("name", st == null ? "-" : st.getName());
                m.put("classType", "一对一");
            } else {
                m.put("name", "-");
                m.put("classType", "-");
            }
            EduCourse c2 = s.getCourseId() == null ? null : courseMapper.selectById(s.getCourseId());
            m.put("courseName", c2 == null ? "-" : c2.getName());
            result.add(m);
        }
        return Result.ok(PageResult.of(p.getTotal(), result));
    }

    /** 我所带的学生（在读报名 + 课时/折扣信息；学生信息出参脱敏，防接口抓包泄露手机号） */
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
            item.put("student", maskedStudent(e.getStudentId()));
            item.put("course", courseMapper.selectById(e.getCourseId()));
            // 班课附班级信息（我的学生按班级视角分组展示用）
            if (e.getClazzId() != null) item.put("clazz", clazzMapper.selectById(e.getClazzId()));
            result.add(item);
        }
        return Result.ok(result);
    }

    /**
     * 学生基本信息（教师端）：仅限本人所带学生可查；
     * 手机号在接口层脱敏（前3后4），前端/抓包均拿不到明文。
     */
    @GetMapping("/student-info/{studentId}")
    public Result<Map<String, Object>> studentInfo(@PathVariable Long studentId) {
        Long cnt = enrollMapper.selectCount(new LambdaQueryWrapper<EduEnroll>()
                .eq(EduEnroll::getTeacherId, teacherId())
                .eq(EduEnroll::getStudentId, studentId)
                .ne(EduEnroll::getStatus, 3));
        if (cnt == null || cnt == 0) throw new BizException("该学生不在您名下，无法查看");
        Map<String, Object> data = maskedStudent(studentId);
        // 该生在本教师名下的报名概览（课程/班级/课时）
        List<Map<String, Object>> rows = new ArrayList<>();
        for (EduEnroll e : enrollMapper.selectList(new LambdaQueryWrapper<EduEnroll>()
                .eq(EduEnroll::getTeacherId, teacherId())
                .eq(EduEnroll::getStudentId, studentId)
                .ne(EduEnroll::getStatus, 3)
                .orderByDesc(EduEnroll::getId))) {
            Map<String, Object> r = new HashMap<>();
            EduCourse c = e.getCourseId() == null ? null : courseMapper.selectById(e.getCourseId());
            r.put("courseName", c == null ? "-" : c.getName());
            r.put("clazzName", e.getClazzId() == null ? "一对一"
                    : (clazzMapper.selectById(e.getClazzId()) == null ? "-"
                            : clazzMapper.selectById(e.getClazzId()).getName()));
            r.put("totalHours", e.getTotalHours());
            r.put("usedHours", e.getUsedHours());
            r.put("status", e.getStatus());
            rows.add(r);
        }
        data.put("enrolls", rows);
        return Result.ok(data);
    }

    /** 学生信息出参（白名单字段 + 手机号脱敏），教师端所有返回学生的接口共用 */
    private Map<String, Object> maskedStudent(Long studentId) {
        EduStudent st = studentId == null ? null : studentMapper.selectById(studentId);
        if (st == null) return null;
        Map<String, Object> m = new HashMap<>();
        m.put("id", st.getId());
        m.put("name", st.getName());
        m.put("gender", st.getGender());
        m.put("birthDate", st.getBirthDate());
        m.put("grade", st.getGrade());
        m.put("school", st.getSchool());
        m.put("parentName", st.getParentName());
        m.put("parentPhone", maskPhone(st.getParentPhone()));
        m.put("backupPhone", maskPhone(st.getBackupPhone()));
        m.put("advisor", st.getAdvisor());
        m.put("tags", st.getTags());
        m.put("address", st.getAddress());
        m.put("remark", st.getRemark());
        m.put("status", st.getStatus());
        m.put("createTime", st.getCreateTime());
        return m;
    }

    /** 手机号脱敏：保留前3位后4位，中间打码；过短则只留首位 */
    private String maskPhone(String phone) {
        if (phone == null || phone.trim().isEmpty()) return null;
        String p = phone.trim();
        if (p.length() >= 7) return p.substring(0, 3) + "****" + p.substring(p.length() - 4);
        return p.charAt(0) + "****";
    }

    /** 我收到的评价（附学生/课程/班级信息，供按学生、按班级卡片双视角切换展示） */
    @GetMapping("/my-evaluations")
    public Result<List<Map<String, Object>>> myEvaluations() {
        List<Map<String, Object>> result = new ArrayList<>();
        List<EduEvaluation> evals = evaluationMapper.selectList(new LambdaQueryWrapper<EduEvaluation>()
                .eq(EduEvaluation::getTeacherId, teacherId())
                .orderByDesc(EduEvaluation::getId));
        for (EduEvaluation e : evals) {
            Map<String, Object> item = new HashMap<>();
            item.put("evaluation", e);
            item.put("student", maskedStudent(e.getStudentId()));
            // 班级优先取评价关联的排课记录，排课缺失/无班级时回退到该生在本教师名下的班课报名
            EduSchedule s = e.getScheduleId() == null ? null : scheduleMapper.selectById(e.getScheduleId());
            if (s != null) {
                if (e.getCourseId() == null && s.getCourseId() != null)
                    item.put("course", courseMapper.selectById(s.getCourseId()));
                if (s.getClazzId() != null) item.put("clazz", clazzMapper.selectById(s.getClazzId()));
            }
            if (!item.containsKey("course") && e.getCourseId() != null)
                item.put("course", courseMapper.selectById(e.getCourseId()));
            if (!item.containsKey("clazz")) item.put("clazz", studentClazz(e.getStudentId()));
            result.add(item);
        }
        return Result.ok(result);
    }

    /** 咨询列表（待回复/全部），附学生/班级信息供双视角卡片展示 */
    @GetMapping("/my-consults")
    public Result<List<Map<String, Object>>> myConsults() {
        List<Map<String, Object>> result = new ArrayList<>();
        List<EduConsult> consults = consultMapper.selectList(new LambdaQueryWrapper<EduConsult>()
                .eq(EduConsult::getTeacherId, teacherId())
                .orderByDesc(EduConsult::getId));
        for (EduConsult c : consults) {
            Map<String, Object> item = new HashMap<>();
            item.put("consult", c);
            item.put("student", maskedStudent(c.getStudentId()));
            item.put("clazz", studentClazz(c.getStudentId()));
            result.add(item);
        }
        return Result.ok(result);
    }

    /** 咨询无直接班级字段，回退按学生+当前教师查最近一次有效班课报名取其班级 */
    private EduClazz studentClazz(Long studentId) {
        EduEnroll enroll = enrollMapper.selectOne(new LambdaQueryWrapper<EduEnroll>()
                .eq(EduEnroll::getStudentId, studentId)
                .eq(EduEnroll::getTeacherId, teacherId())
                .isNotNull(EduEnroll::getClazzId)
                .ne(EduEnroll::getStatus, 3)
                .orderByDesc(EduEnroll::getId)
                .last("limit 1"));
        return enroll == null ? null : clazzMapper.selectById(enroll.getClazzId());
    }

    /** 回复咨询：主记录保留最近一次回复（列表直读），同时追加进对话流（学生端可多轮追问） */
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
        EduConsultMsg m = new EduConsultMsg();
        m.setConsultId(c.getId());
        m.setSender(1);
        m.setContent(dto.getReply());
        m.setCreateTime(LocalDateTime.now());
        consultMsgMapper.insert(m);
        return Result.ok();
    }

    /**
     * 我的收入：所带学生总体课程收入、折扣、课时消耗；
     * 附班级视角分组明细——各班对应合同收入、总课时/已上课时/未完课时、请假、旷课。
     */
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
        data.put("remainHours", Math.max(totalHours - usedHours, 0));
        data.put("studentCount", enrolls.size());
        data.put("groups", incomeGroups(enrolls));
        return Result.ok(data);
    }

    /** 收入班级分组：班课按班级聚合（一对一单独一组），含合同收入与课次统计 */
    private List<Map<String, Object>> incomeGroups(List<EduEnroll> enrolls) {
        Map<Long, List<EduEnroll>> byClazz = new java.util.LinkedHashMap<>();
        List<EduEnroll> one2one = new ArrayList<>();
        for (EduEnroll e : enrolls) {
            if (e.getClazzId() != null)
                byClazz.computeIfAbsent(e.getClazzId(), k -> new ArrayList<>()).add(e);
            else one2one.add(e);
        }
        List<Map<String, Object>> groups = new ArrayList<>();
        for (Map.Entry<Long, List<EduEnroll>> en : byClazz.entrySet()) {
            EduClazz c = clazzMapper.selectById(en.getKey());
            Map<String, Object> g = incomeGroupRow(
                    c == null ? ("班级#" + en.getKey()) : c.getName(), en.getValue());
            g.put("clazzId", en.getKey());
            EduCourse course = (c == null || c.getCourseId() == null) ? null : courseMapper.selectById(c.getCourseId());
            g.put("courseName", course == null ? "-" : course.getName());
            groups.add(g);
        }
        if (!one2one.isEmpty()) {
            Map<String, Object> g = incomeGroupRow("一对一", one2one);
            g.put("courseName", "一对一授课");
            groups.add(g);
        }
        return groups;
    }

    /** 单组收入行：合同金额/已缴、总课时/已上/未完、请假（教师+学生）、旷课 */
    private Map<String, Object> incomeGroupRow(String name, List<EduEnroll> list) {
        BigDecimal totalAmount = BigDecimal.ZERO;
        BigDecimal paidAmount = BigDecimal.ZERO;
        int totalHours = 0;
        int usedHours = 0;
        for (EduEnroll e : list) {
            totalAmount = totalAmount.add(e.getTotalAmount() == null ? BigDecimal.ZERO : e.getTotalAmount());
            paidAmount = paidAmount.add(e.getPaidAmount() == null ? BigDecimal.ZERO : e.getPaidAmount());
            totalHours += e.getTotalHours() == null ? 0 : e.getTotalHours();
            usedHours += e.getUsedHours() == null ? 0 : e.getUsedHours();
        }
        // 该组课次：班课取本人在该班级的排课，一对一取本人在这些学员名下的一对一排课
        LambdaQueryWrapper<EduSchedule> qw = new LambdaQueryWrapper<EduSchedule>()
                .eq(EduSchedule::getTeacherId, teacherId())
                .eq(EduSchedule::getDeleted, 0);
        Long clazzId = list.get(0).getClazzId();
        if (clazzId != null) {
            qw.eq(EduSchedule::getClazzId, clazzId);
        } else {
            List<Long> sids = new ArrayList<>();
            for (EduEnroll e : list) if (e.getStudentId() != null) sids.add(e.getStudentId());
            qw.isNull(EduSchedule::getClazzId);
            if (sids.isEmpty()) sids.add(-1L);
            qw.in(EduSchedule::getStudentId, sids);
        }
        long leaveCnt = 0;
        long absentCnt = 0;
        for (EduSchedule s : scheduleMapper.selectList(qw)) {
            Integer st = s.getStatus();
            if (st != null && (st == 3 || st == 4)) leaveCnt++;
            if (st != null && st >= 5) absentCnt++;
        }
        Map<String, Object> g = new HashMap<>();
        g.put("name", name);
        g.put("studentCount", list.size());
        g.put("totalAmount", totalAmount);
        g.put("paidAmount", paidAmount);
        g.put("totalHours", totalHours);
        g.put("usedHours", usedHours);
        g.put("remainHours", Math.max(totalHours - usedHours, 0));
        g.put("leaveCount", leaveCnt);
        g.put("absentCount", absentCnt);
        return g;
    }
}
