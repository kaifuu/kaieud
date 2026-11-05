package com.edu.controller;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.edu.common.PageResult;
import com.edu.common.Result;
import com.edu.common.SafeSort;
import com.edu.common.exception.BizException;
import com.edu.entity.*;
import com.edu.entity.SysUserRole;
import com.edu.entity.SysRole;
import com.edu.mapper.*;
import com.edu.security.AuthContext;
import com.edu.security.LoginUser;
import com.edu.service.EduEnrollService;
import lombok.Data;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.stream.Collectors;

/** 学生端门户：选课、我的课表、我的老师、咨询、评价 */
@RestController
@RequestMapping("/portal")
@RequiredArgsConstructor
public class PortalController {

    /** 消息时间展示格式 */
    private static final java.time.format.DateTimeFormatter TIME_FMT =
            java.time.format.DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm");

    private final EduEnrollService enrollService;
    private final EduEnrollMapper enrollMapper;
    private final EduCourseMapper courseMapper;
    private final EduClazzMapper clazzMapper;
    private final EduScheduleMapper scheduleMapper;
    private final EduTeacherMapper teacherMapper;
    private final EduEvaluationMapper evaluationMapper;
    private final EduConsultMapper consultMapper;
    private final EduConsultMsgMapper consultMsgMapper;
    private final com.edu.mapper.EduAccountLogMapper accountMapper;
    private final com.edu.mapper.EduContractMapper contractMapper;
    private final com.edu.mapper.EduStudentMapper studentMapper;
    private final com.edu.mapper.SysUserRoleMapper userRoleMapper;
    private final com.edu.mapper.SysRoleMapper roleMapper;

    private Long studentId() {
        LoginUser lu = AuthContext.get();
        if (lu.getStudentId() == null)
            throw new BizException("当前账号未关联学生档案（学生账号请在用户管理中编辑保存一次补建档案；其他账号可开启\"兼任学生\"）");
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
        com.edu.controller.EduEnrollController.SubscribeDTO d =
                new com.edu.controller.EduEnrollController.SubscribeDTO();
        d.setStudentId(studentId());
        d.setCourseId(dto.getCourseId());
        d.setClazzId(dto.getClazzId());
        d.setTeacherId(dto.getTeacherId());
        return Result.ok(enrollService.subscribe(d));
    }

    /**
     * 我的课次范围：一对一/定向课次(student_id=本人) + 在读班课所在班级的共享课次。
     * 班课匹配必须限定 student_id IS NULL——班级下挂到具体学员的课次（如补课/旷课处理）
     * 属于该学员个人记录，不能漏给同班其他同学。
     */
    private LambdaQueryWrapper<EduSchedule> myScheduleQuery() {
        Long sid = studentId();
        List<Long> clazzIds = enrollService.myEnrolls(sid).stream()
                .map(EduEnroll::getClazzId).filter(Objects::nonNull).distinct().collect(Collectors.toList());
        LambdaQueryWrapper<EduSchedule> qw = new LambdaQueryWrapper<>();
        if (clazzIds.isEmpty()) qw.eq(EduSchedule::getStudentId, sid);
        else qw.and(w -> w.eq(EduSchedule::getStudentId, sid)
                .or(w2 -> w2.isNull(EduSchedule::getStudentId).in(EduSchedule::getClazzId, clazzIds)));
        return qw;
    }

    /** 课次行组装：附课程/班级/授课老师名称与格式化时间，供日历与记录列表展示 */
    private Map<String, Object> scheduleRow(EduSchedule s) {
        java.time.format.DateTimeFormatter fmt =
                java.time.format.DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss");
        Map<String, Object> m = new HashMap<>();
        m.put("id", s.getId());
        m.put("start", s.getStartTime() == null ? null : s.getStartTime().format(fmt));
        m.put("end", s.getEndTime() == null ? null : s.getEndTime().format(fmt));
        m.put("classroom", s.getClassroom());
        m.put("status", s.getStatus());
        m.put("remark", s.getRemark());
        // 学生视角：班课展示班级名，一对一无需再写学员名（即本人）
        if (s.getClazzId() != null) {
            EduClazz c = clazzMapper.selectById(s.getClazzId());
            m.put("name", c == null ? "-" : c.getName());
            m.put("classType", "班课");
        } else {
            m.put("name", "一对一");
            m.put("classType", "一对一");
        }
        EduCourse c2 = s.getCourseId() == null ? null : courseMapper.selectById(s.getCourseId());
        m.put("courseName", c2 == null ? "-" : c2.getName());
        EduTeacher t = s.getTeacherId() == null ? null : teacherMapper.selectById(s.getTeacherId());
        m.put("teacherName", t == null ? "-" : t.getName());
        return m;
    }

    /**
     * 我的课表（学生端只读，与教师端同款日历）：附带课程/班级/老师名称与格式化时间；
     * begin/end 为日历当前视图的日期范围（可缺省取全部）。
     */
    @GetMapping("/my-schedule")
    public Result<List<Map<String, Object>>> mySchedule(@RequestParam(required = false) String begin,
                                                        @RequestParam(required = false) String end) {
        LambdaQueryWrapper<EduSchedule> qw = myScheduleQuery()
                .ge(begin != null, EduSchedule::getStartTime, begin + " 00:00:00")
                .le(end != null, EduSchedule::getStartTime, end + " 23:59:59")
                .orderByAsc(EduSchedule::getStartTime);
        List<Map<String, Object>> result = new ArrayList<>();
        for (EduSchedule s : scheduleMapper.selectList(qw)) result.add(scheduleRow(s));
        return Result.ok(result);
    }

    /**
     * 我的排课记录（我的记录TAB）：本人全部课次分页列表（含班课按班级归属），
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
        LambdaQueryWrapper<EduSchedule> qw = myScheduleQuery()
                .eq(status != null, EduSchedule::getStatus, status)
                .ge(begin != null, EduSchedule::getStartTime, begin + " 00:00:00")
                .le(end != null, EduSchedule::getStartTime, end + " 23:59:59")
                .last(SafeSort.order(sortBy, direction, "startTime", "startTime", "endTime", "status"));
        Page<EduSchedule> p = scheduleMapper.selectPage(new Page<>(page, size), qw);
        List<Map<String, Object>> result = new ArrayList<>();
        for (EduSchedule s : p.getRecords()) result.add(scheduleRow(s));
        return Result.ok(PageResult.of(p.getTotal(), result));
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

    /** 发起咨询：落咨询主记录 + 首条学生消息（追问对话流的起点） */
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
        insertMsg(c.getId(), 0, dto.getContent());
        return Result.ok();
    }

    /** 追问：同一咨询下可多次发问，状态回到待回复提醒老师处理 */
    @Data
    public static class AskDTO {
        private String content;
    }

    @PostMapping("/consult/{id}/ask")
    public Result<Void> ask(@PathVariable Long id, @RequestBody AskDTO dto) {
        EduConsult c = myConsult(id);
        if (!hasText(dto.getContent())) throw new BizException("请输入问题");
        insertMsg(c.getId(), 0, dto.getContent());
        c.setStatus(0);
        consultMapper.updateById(c);
        return Result.ok();
    }

    /** 咨询对话流：按时间升序返回全部追问/回复（历史咨询无消息时用主记录内容合成） */
    @GetMapping("/consult/{id}/messages")
    public Result<List<Map<String, Object>>> consultMessages(@PathVariable Long id) {
        EduConsult c = myConsult(id);
        List<EduConsultMsg> msgs = consultMsgMapper.selectList(
                new LambdaQueryWrapper<EduConsultMsg>()
                        .eq(EduConsultMsg::getConsultId, c.getId())
                        .orderByAsc(EduConsultMsg::getId));
        if (msgs.isEmpty()) { // 升级前的历史咨询：主记录内容/回复合成为对话
            if (hasText(c.getContent())) msgs.add(synthMsg(c.getId(), 0, c.getContent(), c.getCreateTime()));
            if (hasText(c.getReply())) msgs.add(synthMsg(c.getId(), 1, c.getReply(), c.getReplyTime()));
        }
        List<Map<String, Object>> result = new ArrayList<>();
        for (EduConsultMsg m : msgs) {
            Map<String, Object> item = new HashMap<>();
            item.put("sender", m.getSender());
            item.put("content", m.getContent());
            item.put("time", m.getCreateTime() == null ? null : m.getCreateTime().format(TIME_FMT));
            result.add(item);
        }
        return Result.ok(result);
    }

    /** 本人咨询（越权访问直接拒绝） */
    private EduConsult myConsult(Long id) {
        EduConsult c = consultMapper.selectById(id);
        if (c == null || !studentId().equals(c.getStudentId())) throw new BizException("咨询不存在");
        return c;
    }

    private void insertMsg(Long consultId, int sender, String content) {
        EduConsultMsg m = new EduConsultMsg();
        m.setConsultId(consultId);
        m.setSender(sender);
        m.setContent(content);
        m.setCreateTime(LocalDateTime.now());
        consultMsgMapper.insert(m);
    }

    private EduConsultMsg synthMsg(Long consultId, int sender, String content, LocalDateTime time) {
        EduConsultMsg m = new EduConsultMsg();
        m.setConsultId(consultId);
        m.setSender(sender);
        m.setContent(content);
        m.setCreateTime(time);
        return m;
    }

    private static boolean hasText(String s) { return s != null && !s.trim().isEmpty(); }

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

    /** 我的签约班级（咨询入口用）：在读报名按 老师+班级/课程 去重，附老师信息与剩余课时 */
    @GetMapping("/my-classes")
    public Result<List<Map<String, Object>>> myClasses() {
        List<Map<String, Object>> result = new ArrayList<>();
        Set<String> seen = new HashSet<>();
        for (EduEnroll e : enrollService.myEnrolls(studentId())) {
            if (e.getTeacherId() == null) continue;
            String key = e.getTeacherId() + "-" + (e.getClazzId() == null ? "o" + e.getCourseId() : "c" + e.getClazzId());
            if (!seen.add(key)) continue;
            Map<String, Object> item = new HashMap<>();
            item.put("teacher", teacherMapper.selectById(e.getTeacherId()));
            item.put("course", courseMapper.selectById(e.getCourseId()));
            if (e.getClazzId() != null) item.put("clazz", clazzMapper.selectById(e.getClazzId()));
            item.put("classType", e.getClazzId() != null ? "班课" : "一对一");
            item.put("remainHours", e.getTotalHours() - e.getUsedHours());
            result.add(item);
        }
        return Result.ok(result);
    }

    /**
     * 我的咨询记录：附老师姓名与班级/课程名（咨询无班级字段，
     * 按老师回查本人在读报名：班课优先显示班级，否则显示一对一课程）。
     */
    /**
     * 我的咨询记录（分页）：支持按老师筛选（签约班级切换条），
     * 行内附最新一条消息与对话条数，便于列表直读进展。
     */
    @GetMapping("/my-consults")
    public Result<PageResult<Map<String, Object>>> myConsults(
            @RequestParam(defaultValue = "1") long page,
            @RequestParam(defaultValue = "10") long size,
            @RequestParam(required = false) Long teacherId) {
        List<EduEnroll> myEnrolls = enrollService.myEnrolls(studentId());
        Page<EduConsult> p = consultMapper.selectPage(new Page<>(page, size),
                new LambdaQueryWrapper<EduConsult>()
                        .eq(EduConsult::getStudentId, studentId())
                        .eq(teacherId != null, EduConsult::getTeacherId, teacherId)
                        .orderByDesc(EduConsult::getId));
        List<Map<String, Object>> result = new ArrayList<>();
        for (EduConsult c : p.getRecords()) {
            Map<String, Object> item = new HashMap<>();
            item.put("consult", c);
            EduTeacher t = c.getTeacherId() == null ? null : teacherMapper.selectById(c.getTeacherId());
            item.put("teacherName", t == null ? "-" : t.getName());
            item.put("className", enrollClassName(myEnrolls, c.getTeacherId()));
            // 对话进展：最新一条消息（无追问时即首问）与总条数（历史咨询按首问+回复合成）
            List<EduConsultMsg> msgs = consultMsgMapper.selectList(new LambdaQueryWrapper<EduConsultMsg>()
                    .eq(EduConsultMsg::getConsultId, c.getId()).orderByDesc(EduConsultMsg::getId));
            if (msgs.isEmpty()) {
                item.put("lastMsg", hasText(c.getContent()) ? c.getContent() : c.getReply());
                item.put("msgCount", (hasText(c.getContent()) ? 1 : 0) + (hasText(c.getReply()) ? 1 : 0));
            } else {
                item.put("lastMsg", msgs.get(0).getContent());
                item.put("msgCount", msgs.size());
            }
            result.add(item);
        }
        return Result.ok(PageResult.of(p.getTotal(), result));
    }

    /** 按老师在本人在读报名中回查班级/课程名：班课优先，否则一对一课程 */
    private String enrollClassName(List<EduEnroll> enrolls, Long teacherId) {
        EduEnroll hit = enrolls.stream()
                .filter(e -> teacherId.equals(e.getTeacherId()) && e.getClazzId() != null)
                .findFirst().orElse(null);
        if (hit == null) hit = enrolls.stream()
                .filter(e -> teacherId.equals(e.getTeacherId()))
                .findFirst().orElse(null);
        if (hit == null) return null;
        if (hit.getClazzId() != null) {
            EduClazz c = clazzMapper.selectById(hit.getClazzId());
            return c == null ? null : c.getName();
        }
        EduCourse c2 = hit.getCourseId() == null ? null : courseMapper.selectById(hit.getCourseId());
        return c2 == null ? null : "一对一·" + c2.getName();
    }

    /**
     * 学生账户管理：余额 = 累计充值 + 累计退费入账；
     * 流水含 退费金额/退费课时/关联合同/操作人/操作时间，充值记录同理。
     */
    @GetMapping("/my-account")
    public Result<Map<String, Object>> myAccount() {
        Long sid = studentId();
        List<com.edu.entity.EduAccountLog> logs = accountMapper.selectList(
                new LambdaQueryWrapper<com.edu.entity.EduAccountLog>()
                        .eq(com.edu.entity.EduAccountLog::getStudentId, sid)
                        .orderByDesc(com.edu.entity.EduAccountLog::getId)
                        .last("LIMIT 100"));
        java.math.BigDecimal recharge = java.math.BigDecimal.ZERO, refund = java.math.BigDecimal.ZERO;
        for (com.edu.entity.EduAccountLog l : logs) {
            if (l.getAmount() == null) continue;
            if (l.getType() != null && l.getType() == 1) recharge = recharge.add(l.getAmount());
            if (l.getType() != null && l.getType() == 2) refund = refund.add(l.getAmount());
        }
        // 合同信息（编号+课程名）
        Set<Long> cids = logs.stream().map(com.edu.entity.EduAccountLog::getContractId)
                .filter(Objects::nonNull).collect(java.util.stream.Collectors.toSet());
        Map<Long, com.edu.entity.EduContract> cMap = cids.isEmpty() ? new HashMap<>()
                : contractMapper.selectBatchIds(cids).stream()
                    .collect(java.util.stream.Collectors.toMap(com.edu.entity.EduContract::getId, c -> c, (a, b) -> a));
        List<Map<String, Object>> rows = new ArrayList<>();
        for (com.edu.entity.EduAccountLog l : logs) {
            Map<String, Object> m = new HashMap<>();
            m.put("type", l.getType());
            m.put("amount", l.getAmount());
            m.put("hours", l.getHours());
            com.edu.entity.EduContract c = l.getContractId() == null ? null : cMap.get(l.getContractId());
            m.put("contractNo", c == null ? "-" : c.getContractNo());
            m.put("remark", l.getRemark());
            m.put("operator", l.getOperator());
            m.put("createTime", l.getCreateTime());
            rows.add(m);
        }
        Map<String, Object> data = new HashMap<>();
        data.put("rechargeTotal", recharge);
        data.put("refundInTotal", refund);
        data.put("balance", recharge.add(refund));
        data.put("logs", rows);
        return Result.ok(data);
    }

    /**
     * 全部学生充值记录（查询视角）：仅企业管理员与财务可查，
     * 数据由租户拦截器限定在本企业内。学生本人请用 /portal/my-account。
     */
    @GetMapping("/account-records")
    public Result<Map<String, Object>> accountRecords(@RequestParam(defaultValue = "1") long page,
                                                      @RequestParam(defaultValue = "10") long size,
                                                      @RequestParam(required = false) Long studentId,
                                                      @RequestParam(required = false) Integer type,
                                                      @RequestParam(required = false) String begin,
                                                      @RequestParam(required = false) String end) {
        LoginUser lu = AuthContext.get();
        if (!lu.isSuperAdmin() && !hasRole(lu, "FINANCE"))
            throw new BizException("403", "仅企业管理员与财务可查看全部学生充值记录");

        com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper<com.edu.entity.EduAccountLog> qw =
                new com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper<com.edu.entity.EduAccountLog>()
                        .eq(studentId != null, com.edu.entity.EduAccountLog::getStudentId, studentId)
                        .eq(type != null, com.edu.entity.EduAccountLog::getType, type)
                        .ge(begin != null, com.edu.entity.EduAccountLog::getCreateTime, begin + " 00:00:00")
                        .le(end != null, com.edu.entity.EduAccountLog::getCreateTime, end + " 23:59:59")
                        .orderByDesc(com.edu.entity.EduAccountLog::getId);
        com.baomidou.mybatisplus.extension.plugins.pagination.Page<com.edu.entity.EduAccountLog> p =
                accountMapper.selectPage(new com.baomidou.mybatisplus.extension.plugins.pagination.Page<>(page, size), qw);

        // 汇总（同筛选口径）
        java.math.BigDecimal recharge = java.math.BigDecimal.ZERO, refund = java.math.BigDecimal.ZERO;
        for (com.edu.entity.EduAccountLog l : accountMapper.selectList(qw)) {
            if (l.getAmount() == null) continue;
            if (l.getType() != null && l.getType() == 1) recharge = recharge.add(l.getAmount());
            if (l.getType() != null && l.getType() == 2) refund = refund.add(l.getAmount());
        }
        // 学生姓名 / 合同编号
        java.util.Set<Long> sids = new java.util.HashSet<>();
        java.util.Set<Long> cids = new java.util.HashSet<>();
        for (com.edu.entity.EduAccountLog l : p.getRecords()) {
            if (l.getStudentId() != null) sids.add(l.getStudentId());
            if (l.getContractId() != null) cids.add(l.getContractId());
        }
        Map<Long, String> sName = new HashMap<>();
        if (!sids.isEmpty())
            for (com.edu.entity.EduStudent s : studentMapper.selectBatchIds(sids)) sName.put(s.getId(), s.getName());
        Map<Long, String> cNo = new HashMap<>();
        if (!cids.isEmpty())
            for (com.edu.entity.EduContract c : contractMapper.selectBatchIds(cids)) cNo.put(c.getId(), c.getContractNo());

        List<Map<String, Object>> rows = new ArrayList<>();
        for (com.edu.entity.EduAccountLog l : p.getRecords()) {
            Map<String, Object> m = new HashMap<>();
            m.put("studentName", sName.getOrDefault(l.getStudentId(), "-"));
            m.put("type", l.getType());
            m.put("amount", l.getAmount());
            m.put("hours", l.getHours());
            m.put("contractNo", cNo.getOrDefault(l.getContractId(), "-"));
            m.put("remark", l.getRemark());
            m.put("operator", l.getOperator());
            m.put("createTime", l.getCreateTime());
            rows.add(m);
        }
        Map<String, Object> data = new HashMap<>();
        data.put("total", p.getTotal());
        data.put("records", rows);
        data.put("rechargeTotal", recharge);
        data.put("refundInTotal", refund);
        data.put("balance", recharge.add(refund));
        return Result.ok(data);
    }

    private boolean hasRole(LoginUser lu, String code) {
        List<Long> roleIds = userRoleMapper.selectList(
                        new com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper<SysUserRole>()
                                .eq(SysUserRole::getUserId, lu.getId()))
                .stream().map(SysUserRole::getRoleId).collect(java.util.stream.Collectors.toList());
        if (roleIds.isEmpty()) return false;
        for (SysRole r : roleMapper.selectBatchIds(roleIds))
            if (code.equals(r.getCode())) return true;
        return false;
    }
}
