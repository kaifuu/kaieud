package com.edu.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.edu.entity.*;
import com.edu.mapper.*;
import com.edu.security.AuthContext;
import com.edu.security.LoginUser;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import java.math.BigDecimal;
import java.time.DayOfWeek;
import java.time.Duration;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.temporal.TemporalAdjusters;
import java.util.*;
import java.util.function.Function;
import java.util.stream.Collectors;

/**
 * 首页概览：按登录用户视角呈现（8 种）——
 * 运营总负责人/校区校长/校区咨询师/班主任/财务/教师/学生/系统管理员。
 * 返回通用结构 {view, viewName, orgName, cards[], charts[], tables[]}，前端按类型渲染。
 * 数据量演示级，一次性装载全表内存聚合。
 */
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
    private final EduContractMapper contractMapper;
    private final EduConsumeMapper consumeMapper;
    private final EduRefundMapper refundMapper;
    private final EduLeaveMapper leaveMapper;
    private final EduAbsenceMapper absenceMapper;
    private final EduEvaluationMapper evaluationMapper;
    private final EduConsultMapper consultMapper;
    private final EduClassroomMapper classroomMapper;
    private final SysOrgMapper orgMapper;
    private final SysUserRoleMapper userRoleMapper;
    private final SysRoleMapper roleMapper;
    private final SysTenantMapper tenantMapper;
    private final SysUserMapper sysUserMapper;

    /** 合同状态：0待缴费 1部分缴费 2已结清 3已退费 4已到期 */
    private static final Map<Integer, String> CONTRACT_STATUS = new HashMap<>();
    /** 课次状态：0待上课 1已到课 2已取消 3教师请假 4学生请假 5旷课待审批 6旷课免扣 7旷课已扣课 */
    private static final Map<Integer, String> SCH_STATUS = new HashMap<>();
    /** 班级状态：1招生中 2开课中 3已结课 */
    private static final Map<Integer, String> CLAZZ_STATUS = new HashMap<>();
    static {
        CONTRACT_STATUS.put(0, "待缴费"); CONTRACT_STATUS.put(1, "部分缴费");
        CONTRACT_STATUS.put(2, "已结清"); CONTRACT_STATUS.put(3, "已退费"); CONTRACT_STATUS.put(4, "已到期");
        SCH_STATUS.put(0, "待上课"); SCH_STATUS.put(1, "已到课"); SCH_STATUS.put(2, "已取消");
        SCH_STATUS.put(3, "教师请假"); SCH_STATUS.put(4, "学生请假"); SCH_STATUS.put(5, "旷课·待审批");
        SCH_STATUS.put(6, "旷课·已免扣"); SCH_STATUS.put(7, "旷课·已扣课");
        CLAZZ_STATUS.put(1, "招生中"); CLAZZ_STATUS.put(2, "开课中"); CLAZZ_STATUS.put(3, "已结课");
    }

    /* ==================== 数据上下文 ==================== */

    /** 一次性装载全量数据 + 名称映射，各视角共用 */
    private class Ctx {
        List<EduContract> contracts;
        List<EduPayment> payments;
        List<EduSchedule> schedules;
        List<EduEnroll> enrolls;
        List<EduLeave> leaves;
        List<EduAbsence> absences;
        List<EduEvaluation> evals;
        List<EduConsult> consults;
        List<EduRefund> refunds;
        List<EduConsume> consumes;
        List<SysOrg> orgs;
        List<EduTeacher> teachers;
        List<EduStudent> students;
        List<EduCourse> courses;
        List<EduClazz> clazzes;

        Map<Long, String> orgName = new HashMap<>();
        Map<Long, String> teacherName = new HashMap<>();
        Map<Long, String> studentName = new HashMap<>();
        Map<Long, String> courseName = new HashMap<>();
        Map<Long, String> clazzName = new HashMap<>();

        void load(LoginUser lu) {
            Long tid = (lu.getTenantId() == null || lu.getTenantId() == 0) ? null : lu.getTenantId();
            contracts = scope(contractMapper.selectList(new LambdaQueryWrapper<EduContract>()
                    .eq(EduContract::getDeleted, 0)), EduContract::getTenantId, tid);
            payments = scope(paymentMapper.selectList(null), EduPayment::getTenantId, tid);
            schedules = scope(scheduleMapper.selectList(new LambdaQueryWrapper<EduSchedule>()
                    .eq(EduSchedule::getDeleted, 0)), EduSchedule::getTenantId, tid);
            enrolls = scope(enrollMapper.selectList(null), EduEnroll::getTenantId, tid);
            leaves = scope(leaveMapper.selectList(null), EduLeave::getTenantId, tid);
            absences = scope(absenceMapper.selectList(null), EduAbsence::getTenantId, tid);
            evals = scope(evaluationMapper.selectList(null), EduEvaluation::getTenantId, tid);
            consults = scope(consultMapper.selectList(null), EduConsult::getTenantId, tid);
            refunds = scope(refundMapper.selectList(null), EduRefund::getTenantId, tid);
            consumes = scope(consumeMapper.selectList(new LambdaQueryWrapper<EduConsume>()
                    .eq(EduConsume::getCanceled, 0).eq(EduConsume::getDeleted, 0)), EduConsume::getTenantId, tid);
            orgs = orgMapper.selectList(null);
            teachers = scope(teacherMapper.selectList(new LambdaQueryWrapper<EduTeacher>()
                    .eq(EduTeacher::getDeleted, 0)), EduTeacher::getTenantId, tid);
            students = scope(studentMapper.selectList(new LambdaQueryWrapper<EduStudent>()
                    .eq(EduStudent::getDeleted, 0)), EduStudent::getTenantId, tid);
            courses = courseMapper.selectList(null);
            clazzes = scope(clazzMapper.selectList(null), EduClazz::getTenantId, tid);

            for (SysOrg o : orgs) orgName.put(o.getId(), o.getName());
            for (EduTeacher t : teachers) teacherName.put(t.getId(), t.getName());
            for (EduStudent s : students) studentName.put(s.getId(), s.getName());
            for (EduCourse c : courses) courseName.put(c.getId(), c.getName());
            for (EduClazz c : clazzes) clazzName.put(c.getId(), c.getName());
        }

        String tName(Long id) { return id == null ? "-" : teacherName.getOrDefault(id, "-"); }
        String sName(Long id) { return id == null ? "-" : studentName.getOrDefault(id, "-"); }
        String cName(Long id) { return id == null ? "-" : courseName.getOrDefault(id, "-"); }
        String zName(Long id) { return id == null ? "-" : clazzName.getOrDefault(id, "-"); }
        String oName(Long id) { return id == null ? "-" : orgName.getOrDefault(id, "-"); }
    }

    /** 租户过滤：记录的 tenantId 为空或 0 视为平台全局数据，任何租户可见 */
    private <T> List<T> scope(List<T> list, Function<T, Long> tidFn, Long tid) {
        if (tid == null) return list;
        return list.stream().filter(x -> {
            Long v = tidFn.apply(x);
            return v == null || v == 0 || tid.equals(v);
        }).collect(Collectors.toList());
    }

    /* ==================== 视图分发 ==================== */

    public Map<String, Object> overview() {
        LoginUser lu = AuthContext.get();
        Ctx ctx = new Ctx();
        ctx.load(lu);
        switch (resolveView(lu)) {
            case "platform":    return platformView(lu, ctx);
            case "student":     return studentView(lu, ctx);
            case "teacher":     return teacherView(lu, ctx);
            case "headteacher": return headTeacherView(lu, ctx);
            case "consultant":  return consultantView(lu, ctx);
            case "principal":   return principalView(lu, ctx);
            case "finance":     return financeView(lu, ctx);
            case "operator":    return operatorView(lu, ctx);
            default:            return sysAdminView(lu, ctx);
        }
    }

    /**
     * 视角识别：学生5/教师4/班主任3/校区咨询师7/校区校长6 按用户类型；
     * 财务按 FINANCE 角色；admin 与其余类型（教务/学管师/系统用户）走整体维度。
     */
    private String resolveView(LoginUser lu) {
        Integer t = lu.getUserType();
        // 平台运营（tenant_id=0 的管理身份）：平台级概览（企业/用户/业务规模），不掺企业内部视角
        if (lu.isPlatformAdmin()) return "platform";
        if (t != null && t == 5) return "student";
        if (t != null && t == 4) return "teacher";
        if (t != null && t == 3) return "headteacher";
        if (t != null && t == 7) return "consultant";
        if (t != null && t == 6) return "principal";
        if (hasRole(lu, "FINANCE")) return "finance";
        if ("admin".equals(lu.getUsername())) return "sysadmin";
        if (t != null && t == 1) return "operator";
        return "sysadmin";
    }

    private boolean hasRole(LoginUser lu, String code) {
        List<Long> roleIds = userRoleMapper.selectList(new LambdaQueryWrapper<SysUserRole>()
                .eq(SysUserRole::getUserId, lu.getId()))
                .stream().map(SysUserRole::getRoleId).collect(Collectors.toList());
        if (roleIds.isEmpty()) return false;
        for (SysRole r : roleMapper.selectBatchIds(roleIds))
            if (code.equals(r.getCode())) return true;
        return false;
    }

    /* ==================== 1. 运营总负责人 ==================== */

    private Map<String, Object> operatorView(LoginUser lu, Ctx ctx) {
        Map<String, Object> data = base("operator", "运营总负责人视角");
        LocalDateTime dayStart = LocalDate.now().atStartOfDay();

        card(data, "学生总量", ctx.students.size(), "人");
        card(data, "在职教师", ctx.teachers.stream().filter(t -> t.getStatus() != null && t.getStatus() == 1).count(), "人");
        card(data, "合同总收入", sum(ctx.contracts, EduContract::getTotalAmount), "元");
        card(data, "今日排课", ctx.schedules.stream().filter(s -> s.getStartTime() != null
                && !s.getStartTime().isBefore(dayStart)).count(), "节");
        card(data, "累计退费", sum(ctx.contracts, EduContract::getRefundAmount), "元");
        card(data, "在读学生", ctx.enrolls.stream().filter(e -> e.getStatus() != null && e.getStatus() == 1)
                .map(EduEnroll::getStudentId).distinct().count(), "人");

        // 各校区合同签单量
        addBarChart(data, "orgContracts", "各校区合同签单量", 12,
                countBy(ctx.contracts, EduContract::getOrgId, ctx.orgName), ctx.orgName, "份");
        // 各校区排课量（教室口径）
        addBarChart(data, "orgSchedules", "各校区排课量", 12,
                countBy(ctx.schedules, EduSchedule::getOrgId, ctx.orgName), ctx.orgName, "节");
        // 校区收入排名
        addRankChart(data, "orgRevenueRank", "校区收入排名", 12,
                sumBy(ctx.payments, EduPayment::getOrgId, EduPayment::getAmount, ctx.orgName), ctx.orgName, "元");
        // 近7日签单趋势
        Map<String, Object> trend = dayTrend(ctx.contracts, EduContract::getCreateTime, 7);
        addLineChart(data, "signTrend", "近7日签单趋势", 12, trend, "份");

        // 教师授课问题：教师请假/旷课登记/评分（问题最多的前10）
        List<Map<String, Object>> rows = new ArrayList<>();
        Map<Long, List<EduSchedule>> byTeacher = ctx.schedules.stream()
                .filter(s -> s.getTeacherId() != null)
                .collect(Collectors.groupingBy(EduSchedule::getTeacherId));
        LocalDateTime monthAgo = LocalDateTime.now().minusDays(30);
        for (Map.Entry<Long, List<EduSchedule>> en : byTeacher.entrySet()) {
            List<EduSchedule> ss = en.getValue();
            long tLeave = ss.stream().filter(s -> s.getStatus() != null && s.getStatus() == 3).count();
            long absent = ss.stream().filter(s -> s.getStatus() != null && s.getStatus() >= 5).count();
            double score = avgScore(ctx.evals, en.getKey());
            long taught30 = ss.stream().filter(s -> s.getStartTime() != null
                    && s.getStartTime().isAfter(monthAgo)).count();
            String orgs = ss.stream().map(EduSchedule::getOrgId).filter(Objects::nonNull).distinct()
                    .map(ctx::oName).collect(Collectors.joining("、"));
            Map<String, Object> r = new LinkedHashMap<>();
            r.put("teacher", ctx.tName(en.getKey()));
            r.put("orgs", orgs.isEmpty() ? "-" : orgs);
            r.put("tLeave", tLeave);
            r.put("absent", absent);
            r.put("score", score <= 0 ? "-" : String.format("%.1f", score));
            r.put("taught30", taught30);
            r.put("issue", tLeave + absent);
            rows.add(r);
        }
        rows.sort((a, b) -> Long.compare((Long) b.get("issue"), (Long) a.get("issue")));
        table(data, "教师授课问题（近30日，按请假+旷课排序）",
                cols("teacher:教师:110", "orgs:授课校区:150", "tLeave:教师请假:90",
                        "absent:旷课登记:90", "score:平均评分:90", "taught30:授课(节):90"),
                rows.size() > 10 ? rows.subList(0, 10) : rows);
        return data;
    }

    /* ==================== 2. 校区校长 ==================== */

    private Map<String, Object> principalView(LoginUser lu, Ctx ctx) {
        Map<String, Object> data = base("principal", "校区校长视角");
        Long orgId = lu.getOrgId();
        data.put("orgName", ctx.oName(orgId));
        List<EduSchedule> sch = ctx.schedules.stream()
                .filter(s -> orgId != null && orgId.equals(s.getOrgId())).collect(Collectors.toList());
        List<EduClazz> clazzes = ctx.clazzes.stream()
                .filter(c -> orgId != null && orgId.equals(c.getOrgId())).collect(Collectors.toList());
        List<EduStudent> students = ctx.students.stream()
                .filter(s -> orgId != null && orgId.equals(s.getOrgId())).collect(Collectors.toList());
        List<EduPayment> payments = ctx.payments.stream()
                .filter(p -> orgId != null && orgId.equals(p.getOrgId())).collect(Collectors.toList());
        Set<Long> orgTeacherIds = sch.stream().map(EduSchedule::getTeacherId)
                .filter(Objects::nonNull).collect(Collectors.toSet());
        Set<Long> studentIds = students.stream().map(EduStudent::getId).collect(Collectors.toSet());
        List<EduEnroll> enrolls = ctx.enrolls.stream()
                .filter(e -> e.getStatus() != null && e.getStatus() == 1 && studentIds.contains(e.getStudentId()))
                .collect(Collectors.toList());

        card(data, "本校区学生", students.size(), "人");
        card(data, "授课教师", orgTeacherIds.size(), "人");
        card(data, "开课班级", clazzes.stream().filter(c -> c.getStatus() != null && c.getStatus() != 3).count(), "个");
        card(data, "缴费收入", sum(payments, EduPayment::getAmount), "元");
        card(data, "学生订课量", enrolls.size(), "份");
        long leaveCnt = ctx.leaves.stream().filter(l -> studentIds.contains(l.getStudentId())).count()
                + sch.stream().filter(s -> s.getStatus() != null && (s.getStatus() == 3 || s.getStatus() == 4)).count();
        card(data, "请假量", leaveCnt, "次");

        // 教室排课量
        Map<Long, Long> byRoom = new LinkedHashMap<>();
        for (EduSchedule s : sch) {
            if (s.getClassroomId() == null) continue;
            byRoom.merge(s.getClassroomId(), 1L, Long::sum);
        }
        Map<Long, String> roomNames = new HashMap<>();
        for (EduClassroom r : classroomMapper.selectList(null)) roomNames.put(r.getId(), r.getName());
        addBarChart(data, "roomSchedules", "教室排课量", 12, byRoom, roomNames, "节");
        // 教师授课量
        addRankChart(data, "teacherLoad", "教师授课量排名", 12,
                countBy(sch, EduSchedule::getTeacherId, ctx.teacherName), ctx.teacherName, "节");
        // 教师评分
        Map<Long, Double> scores = new LinkedHashMap<>();
        for (Long tid : orgTeacherIds) {
            double v = avgScore(ctx.evals, tid);
            if (v > 0) scores.put(tid, v);
        }
        Map<String, Object> scoreChart = chart("teacherScore", "教师评分", "bar", 12);
        scoreChart.put("categories", scores.keySet().stream().map(ctx::tName).collect(Collectors.toList()));
        scoreChart.put("series", singleSeries("平均评分",
                scores.values().stream().map(v -> Math.round(v * 10) / 10.0).collect(Collectors.toList())));
        charts(data).add(scoreChart);
        // 请假分布
        List<Map<String, Object>> leavePie = new ArrayList<>();
        leavePie.add(kv("教师请假", sch.stream().filter(s -> s.getStatus() != null && s.getStatus() == 3).count()));
        leavePie.add(kv("学生请假", sch.stream().filter(s -> s.getStatus() != null && s.getStatus() == 4).count()
                + ctx.leaves.stream().filter(l -> studentIds.contains(l.getStudentId())).count()));
        pieChart(data, "leaveDist", "请假分布", 8, leavePie, "次");
        // 咨询回复情况
        List<EduConsult> myConsults = ctx.consults.stream()
                .filter(c -> c.getTeacherId() != null && orgTeacherIds.contains(c.getTeacherId()))
                .collect(Collectors.toList());
        long replied = myConsults.stream().filter(c -> StringUtils.hasText(c.getReply())).count();
        Map<String, Object> consultChart = chart("consultReply", "问题回复情况", "bar", 12);
        consultChart.put("categories", Arrays.asList("已回复", "待回复"));
        consultChart.put("series", singleSeries("咨询数",
                Arrays.asList(replied, myConsults.size() - replied)));
        charts(data).add(consultChart);

        // 本校区班级概览
        List<Map<String, Object>> rows = new ArrayList<>();
        for (EduClazz c : clazzes) {
            long studying = ctx.enrolls.stream().filter(e -> c.getId().equals(e.getClazzId())
                    && e.getStatus() != null && e.getStatus() == 1).count();
            Map<String, Object> r = new LinkedHashMap<>();
            r.put("name", c.getName());
            r.put("course", ctx.cName(c.getCourseId()));
            r.put("status", CLAZZ_STATUS.getOrDefault(c.getStatus(), "-"));
            r.put("capacity", c.getCapacity() == null ? "-" : c.getCapacity());
            r.put("studying", studying);
            r.put("startDate", c.getStartDate() == null ? "-" : c.getStartDate().toString());
            rows.add(r);
        }
        table(data, "本校区班级概览",
                cols("name:班级名称:160", "course:课程:140", "status:状态:90",
                        "capacity:容量:70", "studying:在读人数:90", "startDate:开课日期:110"), rows);
        return data;
    }

    /* ==================== 3. 校区咨询师 ==================== */

    private Map<String, Object> consultantView(LoginUser lu, Ctx ctx) {
        Map<String, Object> data = base("consultant", "校区咨询师视角");
        String advisor = lu.getRealName() == null ? "" : lu.getRealName().trim();
        List<EduContract> mine = ctx.contracts.stream()
                .filter(c -> advisor.equals(c.getAdvisor() == null ? "" : c.getAdvisor().trim()))
                .collect(Collectors.toList());
        Set<Long> myIds = mine.stream().map(EduContract::getId).collect(Collectors.toSet());
        List<EduPayment> myPays = ctx.payments.stream()
                .filter(p -> p.getContractId() != null && myIds.contains(p.getContractId()))
                .collect(Collectors.toList());
        List<EduContract> refunds = mine.stream()
                .filter(c -> c.getStatus() != null && c.getStatus() == 3).collect(Collectors.toList());
        LocalDate monthStart = LocalDate.now().withDayOfMonth(1);

        card(data, "累计签单", mine.size(), "份");
        card(data, "签单总额", sum(mine, EduContract::getTotalAmount), "元");
        card(data, "学生缴费", sum(myPays, EduPayment::getAmount), "元");
        card(data, "退费单量", refunds.size(), "份");
        card(data, "退费金额", sum(refunds, EduContract::getRefundAmount), "元");
        card(data, "本月签单", mine.stream().filter(c -> c.getCreateTime() != null
                && !c.getCreateTime().toLocalDate().isBefore(monthStart)).count(), "份");

        // 近30日签单趋势
        addLineChart(data, "mySignTrend", "近30日签单趋势", 24,
                dayTrend(mine, EduContract::getCreateTime, 30), "份");
        // 签单课程占比
        Map<Long, Long> byCourse = countBy(mine, EduContract::getCourseId, ctx.courseName);
        pieChart(data, "courseShare", "签单课程占比", 8, toList(byCourse), "份");
        // 合同状态分布
        Map<String, Object> statusChart = chart("statusDist", "合同状态分布", "bar", 12);
        List<String> cats = new ArrayList<>();
        List<Object> vals = new ArrayList<>();
        for (int st = 0; st <= 4; st++) {
            if (st == 4) continue;
            cats.add(CONTRACT_STATUS.get(st));
            final int fst = st;
            vals.add(mine.stream().filter(c -> c.getStatus() != null && c.getStatus() == fst).count());
        }
        statusChart.put("categories", cats);
        statusChart.put("series", singleSeries("合同数", vals));
        charts(data).add(statusChart);

        // 最近签单
        List<EduContract> recent = mine.stream()
                .sorted(Comparator.comparing(EduContract::getId, Comparator.reverseOrder()))
                .limit(10).collect(Collectors.toList());
        List<Map<String, Object>> rows = new ArrayList<>();
        for (EduContract c : recent) {
            Map<String, Object> r = new LinkedHashMap<>();
            r.put("contractNo", c.getContractNo());
            r.put("student", ctx.sName(c.getStudentId()));
            r.put("course", ctx.cName(c.getCourseId()));
            r.put("totalAmount", c.getTotalAmount());
            r.put("paidAmount", c.getPaidAmount());
            r.put("status", CONTRACT_STATUS.getOrDefault(c.getStatus(), "-"));
            r.put("startDate", c.getStartDate() == null ? "-" : c.getStartDate().toString());
            rows.add(r);
        }
        table(data, "最近签单合同",
                cols("contractNo:合同编号:150", "student:学员:90", "course:课程:130",
                        "totalAmount:合同总价:100", "paidAmount:已缴:90", "status:状态:90",
                        "startDate:生效日期:110"), rows);
        return data;
    }

    /* ==================== 4. 班主任 ==================== */

    private Map<String, Object> headTeacherView(LoginUser lu, Ctx ctx) {
        Map<String, Object> data = base("headteacher", "班主任视角");
        Long tid = lu.getTeacherId();
        List<EduClazz> myClazzes = tid == null ? new ArrayList<>() : ctx.clazzes.stream()
                .filter(c -> tid.equals(c.getClassTeacherId())).collect(Collectors.toList());
        Set<Long> clazzIds = myClazzes.stream().map(EduClazz::getId).collect(Collectors.toSet());
        List<EduSchedule> mySch = ctx.schedules.stream()
                .filter(s -> s.getClazzId() != null && clazzIds.contains(s.getClazzId()))
                .collect(Collectors.toList());
        List<EduEnroll> myEnrolls = ctx.enrolls.stream()
                .filter(e -> e.getClazzId() != null && clazzIds.contains(e.getClazzId())
                        && e.getStatus() != null && e.getStatus() == 1)
                .collect(Collectors.toList());
        Set<Long> studentIds = myEnrolls.stream().map(EduEnroll::getStudentId)
                .filter(Objects::nonNull).collect(Collectors.toSet());
        LocalDate weekStart = LocalDate.now().with(TemporalAdjusters.previousOrSame(DayOfWeek.MONDAY));
        LocalDate weekEnd = weekStart.plusDays(6);

        card(data, "所带班级", myClazzes.size(), "个");
        card(data, "在读学生", studentIds.size(), "人");
        card(data, "本周课次", mySch.stream().filter(s -> s.getStartTime() != null
                && !s.getStartTime().toLocalDate().isBefore(weekStart)
                && !s.getStartTime().toLocalDate().isAfter(weekEnd)).count(), "节");
        card(data, "待上课课次", mySch.stream().filter(s -> s.getStatus() != null && s.getStatus() == 0
                && s.getStartTime() != null && s.getStartTime().isAfter(LocalDateTime.now())).count(), "节");
        card(data, "已结课班级", myClazzes.stream().filter(c -> c.getStatus() != null && c.getStatus() == 3).count(), "个");
        card(data, "学生请假", ctx.leaves.stream().filter(l -> studentIds.contains(l.getStudentId())).count(), "次");

        // 班级在读人数
        Map<Long, Long> byClazz = new LinkedHashMap<>();
        for (EduEnroll e : myEnrolls)
            if (e.getClazzId() != null) byClazz.merge(e.getClazzId(), 1L, Long::sum);
        addBarChart(data, "clazzStudying", "班级在读人数", 12, byClazz, ctx.clazzName, "人");
        // 班级状态分布
        List<Map<String, Object>> statusPie = new ArrayList<>();
        for (int st = 1; st <= 3; st++) {
            final int fst = st;
            long n = myClazzes.stream().filter(c -> c.getStatus() != null && c.getStatus() == fst).count();
            if (n > 0) statusPie.add(kv(CLAZZ_STATUS.get(st), n));
        }
        pieChart(data, "clazzStatus", "班级状态分布", 8, statusPie, "个");
        // 近14日课次趋势
        addLineChart(data, "schTrend", "近14日课次趋势", 12,
                dayTrend(mySch, EduSchedule::getStartTime, 14), "节");

        // 我的班级
        List<Map<String, Object>> rows = new ArrayList<>();
        for (EduClazz c : myClazzes) {
            Map<String, Object> r = new LinkedHashMap<>();
            r.put("name", c.getName());
            r.put("course", ctx.cName(c.getCourseId()));
            r.put("status", CLAZZ_STATUS.getOrDefault(c.getStatus(), "-"));
            r.put("capacity", c.getCapacity() == null ? "-" : c.getCapacity());
            r.put("studying", byClazz.getOrDefault(c.getId(), 0L));
            r.put("startDate", c.getStartDate() == null ? "-" : c.getStartDate().toString());
            rows.add(r);
        }
        table(data, "我的班级",
                cols("name:班级名称:160", "course:课程:140", "status:状态:90",
                        "capacity:容量:70", "studying:在读人数:90", "startDate:开课日期:110"), rows);
        // 近期课次
        List<Map<String, Object>> schRows = mySch.stream()
                .filter(s -> s.getStartTime() != null && s.getStartTime().isAfter(LocalDateTime.now()))
                .sorted(Comparator.comparing(EduSchedule::getStartTime)).limit(10)
                .map(s -> {
                    Map<String, Object> r = new LinkedHashMap<>();
                    r.put("time", fmtTime(s.getStartTime()));
                    r.put("clazz", s.getClazzId() == null ? ctx.sName(s.getStudentId()) + "（一对一）" : ctx.zName(s.getClazzId()));
                    r.put("teacher", ctx.tName(s.getTeacherId()));
                    r.put("classroom", s.getClassroom() == null ? "-" : s.getClassroom());
                    r.put("status", SCH_STATUS.getOrDefault(s.getStatus(), "-"));
                    return r;
                }).collect(Collectors.toList());
        table(data, "近期课次（待上课）",
                cols("time:上课时间:150", "clazz:班级/学生:150", "teacher:教师:90",
                        "classroom:教室:100", "status:状态:100"), schRows);
        return data;
    }

    /* ==================== 5. 财务 ==================== */

    private Map<String, Object> financeView(LoginUser lu, Ctx ctx) {
        Map<String, Object> data = base("finance", "财务视角");
        BigDecimal contractTotal = sum(ctx.contracts, EduContract::getTotalAmount);
        BigDecimal paidTotal = sum(ctx.payments, EduPayment::getAmount);
        BigDecimal refundTotal = sum(ctx.refunds, EduRefund::getRefundAmount);
        BigDecimal due = ctx.contracts.stream()
                .filter(c -> c.getStatus() != null && c.getStatus() <= 1)
                .map(c -> (c.getTotalAmount() == null ? BigDecimal.ZERO : c.getTotalAmount())
                        .subtract(c.getPaidAmount() == null ? BigDecimal.ZERO : c.getPaidAmount())
                        .max(BigDecimal.ZERO))
                .reduce(BigDecimal.ZERO, BigDecimal::add);

        card(data, "合同总收入", contractTotal, "元");
        card(data, "累计缴费", paidTotal, "元");
        card(data, "累计退费", refundTotal, "元");
        card(data, "待收金额", due, "元");

        // 各校区合同收入
        addBarChart(data, "orgContractRevenue", "各校区合同收入", 12,
                sumBy(ctx.contracts, EduContract::getOrgId, EduContract::getTotalAmount, ctx.orgName), ctx.orgName, "元");
        // 校区收入排行
        addRankChart(data, "orgRevenueRank", "校区缴费收入排行", 12,
                sumBy(ctx.payments, EduPayment::getOrgId, EduPayment::getAmount, ctx.orgName), ctx.orgName, "元");
        // 近30日资金趋势（缴费/退费）
        Map<String, Object> payT = dayTrend(ctx.payments, EduPayment::getPayTime, EduPayment::getAmount, 30);
        Map<String, Object> refT = dayTrend(ctx.refunds, EduRefund::getCreateTime, EduRefund::getRefundAmount, 30);
        Map<String, Object> trendChart = chart("fundingTrend", "近30日资金趋势", "line", 24);
        trendChart.put("categories", payT.get("categories"));
        trendChart.put("series", Arrays.asList(
                seriesMap("缴费", payT.get("data")), seriesMap("退费", refT.get("data"))));
        charts(data).add(trendChart);
        // 学生缴费排行
        addRankChart(data, "studentPayRank", "学生缴费排行 TOP10", 12,
                sumBy(ctx.payments, EduPayment::getStudentId, EduPayment::getAmount, ctx.studentName), ctx.studentName, "元");

        // 各校区资金汇总
        List<Map<String, Object>> rows = new ArrayList<>();
        Set<Long> orgIds = new LinkedHashSet<>();
        ctx.contracts.forEach(c -> orgIds.add(c.getOrgId()));
        ctx.payments.forEach(p -> orgIds.add(p.getOrgId()));
        orgIds.remove(null);
        for (Long oid : orgIds) {
            Map<String, Object> r = new LinkedHashMap<>();
            r.put("org", ctx.oName(oid));
            r.put("contractRevenue", sum(ctx.contracts.stream()
                    .filter(c -> oid.equals(c.getOrgId())).collect(Collectors.toList()), EduContract::getTotalAmount));
            r.put("paid", sum(ctx.payments.stream()
                    .filter(p -> oid.equals(p.getOrgId())).collect(Collectors.toList()), EduPayment::getAmount));
            r.put("refund", sum(ctx.refunds.stream()
                    .filter(x -> oid.equals(x.getOrgId())).collect(Collectors.toList()), EduRefund::getRefundAmount));
            rows.add(r);
        }
        table(data, "各校区资金汇总（元）",
                cols("org:校区:140", "contractRevenue:合同收入:120", "paid:缴费:110", "refund:退费:110"), rows);
        return data;
    }

    /* ==================== 6. 教师 ==================== */

    private Map<String, Object> teacherView(LoginUser lu, Ctx ctx) {
        Map<String, Object> data = base("teacher", "教师视角");
        Long tid = lu.getTeacherId();
        List<EduSchedule> mySch = tid == null ? new ArrayList<>() : ctx.schedules.stream()
                .filter(s -> tid.equals(s.getTeacherId())).collect(Collectors.toList());
        List<EduConsume> myConsume = tid == null ? new ArrayList<>() : ctx.consumes.stream()
                .filter(c -> tid.equals(c.getTeacherId())).collect(Collectors.toList());
        List<EduEnroll> myEnrolls = tid == null ? new ArrayList<>() : ctx.enrolls.stream()
                .filter(e -> tid.equals(e.getTeacherId()) && e.getStatus() != null && e.getStatus() == 1)
                .collect(Collectors.toList());
        LocalDateTime now = LocalDateTime.now();
        List<EduSchedule> attended = mySch.stream()
                .filter(s -> s.getStatus() != null && s.getStatus() == 1).collect(Collectors.toList());
        List<EduSchedule> upcoming = mySch.stream()
                .filter(s -> s.getStatus() != null && s.getStatus() == 0
                        && s.getStartTime() != null && s.getStartTime().isAfter(now)).collect(Collectors.toList());

        card(data, "已上课时长", durationHours(attended), "小时");
        card(data, "未上课时长", durationHours(upcoming), "小时");
        card(data, "所带班级", mySch.stream().map(EduSchedule::getClazzId)
                .filter(Objects::nonNull).distinct().count(), "个");
        card(data, "所带学生", myEnrolls.stream().map(EduEnroll::getStudentId)
                .filter(Objects::nonNull).distinct().count(), "人");
        card(data, "已消课时", sum(myConsume, EduConsume::getHours), "节");
        double score = avgScore(ctx.evals, tid);
        card(data, "学员评分", score <= 0 ? "-" : String.format("%.1f", score), score <= 0 ? "" : "分");

        // 近14日授课课时
        addLineChart(data, "teachTrend", "近14日授课课时", 24,
                dayTrend(myConsume, EduConsume::getCreateTime, EduConsume::getHours, 14), "节");
        // 订课学生课时排名
        Map<Long, Integer> stuHours = new LinkedHashMap<>();
        for (EduEnroll e : myEnrolls)
            if (e.getStudentId() != null) stuHours.merge(e.getStudentId(), e.getUsedHours() == null ? 0 : e.getUsedHours(), Integer::sum);
        List<Map<String, Object>> rankData = stuHours.entrySet().stream()
                .sorted((a, b) -> Integer.compare(b.getValue(), a.getValue()))
                .limit(8)
                .map(e -> kv(ctx.sName(e.getKey()), e.getValue()))
                .collect(Collectors.toList());
        addRankChart(data, "studentHoursRank", "订课学生课时排名（已消）", 12, rankData, "节");
        // 课次状态分布
        List<Map<String, Object>> statusPie = new ArrayList<>();
        Map<Integer, Long> byStatus = mySch.stream().filter(s -> s.getStatus() != null)
                .collect(Collectors.groupingBy(EduSchedule::getStatus, Collectors.counting()));
        for (Map.Entry<Integer, Long> en : byStatus.entrySet())
            statusPie.add(kv(SCH_STATUS.getOrDefault(en.getKey(), "其他"), en.getValue()));
        pieChart(data, "schStatus", "课次状态分布", 8, statusPie, "节");

        // 近期排课
        List<Map<String, Object>> rows = upcoming.stream()
                .sorted(Comparator.comparing(EduSchedule::getStartTime)).limit(10)
                .map(s -> {
                    Map<String, Object> r = new LinkedHashMap<>();
                    r.put("time", fmtTime(s.getStartTime()));
                    r.put("target", s.getClazzId() == null ? ctx.sName(s.getStudentId()) + "（一对一）" : ctx.zName(s.getClazzId()));
                    r.put("classroom", s.getClassroom() == null ? "-" : s.getClassroom());
                    r.put("status", SCH_STATUS.getOrDefault(s.getStatus(), "-"));
                    return r;
                }).collect(Collectors.toList());
        table(data, "近期排课（待上课）",
                cols("time:上课时间:150", "target:班级/学生:160", "classroom:教室:100", "status:状态:100"), rows);
        return data;
    }

    /* ==================== 7. 学生 ==================== */

    private Map<String, Object> studentView(LoginUser lu, Ctx ctx) {
        Map<String, Object> data = base("student", "学生视角");
        Long sid = lu.getStudentId();
        List<EduContract> myContracts = sid == null ? new ArrayList<>() : ctx.contracts.stream()
                .filter(c -> sid.equals(c.getStudentId())).collect(Collectors.toList());
        List<EduPayment> myPays = sid == null ? new ArrayList<>() : ctx.payments.stream()
                .filter(p -> sid.equals(p.getStudentId())).collect(Collectors.toList());
        List<EduEnroll> myEnrolls = sid == null ? new ArrayList<>() : ctx.enrolls.stream()
                .filter(e -> sid.equals(e.getStudentId()) && e.getStatus() != null && e.getStatus() == 1)
                .collect(Collectors.toList());
        BigDecimal total = BigDecimal.ZERO, used = BigDecimal.ZERO;
        for (EduContract c : myContracts) {
            total = total.add(nvl(c.getPurchasedHours())).add(nvl(c.getGiftHours()));
            used = used.add(nvl(c.getUsedPurchased())).add(nvl(c.getUsedGift()));
        }
        BigDecimal due = myContracts.stream()
                .filter(c -> c.getStatus() != null && c.getStatus() <= 1)
                .map(c -> nvl(c.getTotalAmount()).subtract(nvl(c.getPaidAmount())).max(BigDecimal.ZERO))
                .reduce(BigDecimal.ZERO, BigDecimal::add);

        card(data, "缴费总额", sum(myPays, EduPayment::getAmount), "元");
        card(data, "待缴费金额", due, "元");
        card(data, "上课总课时", total, "节");
        card(data, "已上课时", used, "节");
        card(data, "未上课时", total.subtract(used), "节");
        card(data, "在读课程", myEnrolls.stream().map(EduEnroll::getCourseId).distinct().count(), "门");
        card(data, "请假次数", sid == null ? 0 : ctx.leaves.stream().filter(l -> sid.equals(l.getStudentId())).count(), "次");
        card(data, "旷课次数", sid == null ? 0 : ctx.absences.stream().filter(a -> sid.equals(a.getStudentId())).count(), "次");

        // 报名教师占比
        Map<Long, Integer> byTeacher = new LinkedHashMap<>();
        for (EduEnroll e : myEnrolls)
            if (e.getTeacherId() != null)
                byTeacher.merge(e.getTeacherId(), e.getTotalHours() == null ? 0 : e.getTotalHours(), Integer::sum);
        List<Map<String, Object>> pie = byTeacher.entrySet().stream()
                .map(e -> kv(ctx.tName(e.getKey()), e.getValue())).collect(Collectors.toList());
        pieChart(data, "teacherShare", "报名教师占比（按订课课时）", 8, pie, "节");
        // 各课程课时（总/已消）
        Map<Long, BigDecimal[]> byCourse = new LinkedHashMap<>();
        for (EduContract c : myContracts) {
            if (c.getCourseId() == null) continue;
            BigDecimal[] arr = byCourse.computeIfAbsent(c.getCourseId(), k -> new BigDecimal[]{BigDecimal.ZERO, BigDecimal.ZERO});
            arr[0] = arr[0].add(nvl(c.getPurchasedHours())).add(nvl(c.getGiftHours()));
            arr[1] = arr[1].add(nvl(c.getUsedPurchased())).add(nvl(c.getUsedGift()));
        }
        Map<String, Object> courseChart = chart("courseHours", "各课程课时（总/已消）", "bar", 14);
        courseChart.put("categories", byCourse.keySet().stream().map(ctx::cName).collect(Collectors.toList()));
        courseChart.put("series", Arrays.asList(
                seriesMap("总课时", byCourse.values().stream().map(a -> a[0]).collect(Collectors.toList())),
                seriesMap("已消课时", byCourse.values().stream().map(a -> a[1]).collect(Collectors.toList()))));
        charts(data).add(courseChart);

        // 我的缴费记录
        List<Map<String, Object>> rows = myPays.stream()
                .sorted(Comparator.comparing(EduPayment::getPayTime, Comparator.nullsLast(Comparator.reverseOrder())))
                .limit(10)
                .map(p -> {
                    Map<String, Object> r = new LinkedHashMap<>();
                    r.put("payTime", p.getPayTime() == null ? "-" : fmtTime(p.getPayTime()));
                    r.put("amount", p.getAmount());
                    r.put("course", ctx.cName(p.getCourseId()));
                    r.put("paymentNo", p.getPaymentNo());
                    return r;
                }).collect(Collectors.toList());
        table(data, "我的缴费记录",
                cols("payTime:缴费时间:150", "amount:金额(元):100", "course:课程:130", "paymentNo:缴费单号:170"), rows);
        return data;
    }

    /* ==================== 8. 系统管理员（整体维度） ==================== */

    private Map<String, Object> sysAdminView(LoginUser lu, Ctx ctx) {
        Map<String, Object> data = base("sysadmin", "系统管理员视角");
        LocalDateTime now = LocalDateTime.now();
        long leaveTotal = ctx.leaves.size() + ctx.schedules.stream()
                .filter(s -> s.getStatus() != null && (s.getStatus() == 3 || s.getStatus() == 4)).count();
        long roomsUsed = ctx.schedules.stream().map(EduSchedule::getClassroomId)
                .filter(Objects::nonNull).distinct().count();
        List<EduSchedule> finished = ctx.schedules.stream()
                .filter(s -> s.getStartTime() != null && s.getStartTime().isBefore(now)
                        && s.getStatus() != null && s.getStatus() != 0 && s.getStatus() != 2)
                .collect(Collectors.toList());
        long attended = finished.stream().filter(s -> s.getStatus() == 1).count();
        String rate = finished.isEmpty() ? "-" :
                String.format("%.1f", attended * 100.0 / finished.size());

        // 今日实时运转
        LocalDateTime dayStart = LocalDate.now().atStartOfDay();
        long todaySch = ctx.schedules.stream().filter(s -> s.getStartTime() != null
                && !s.getStartTime().isBefore(dayStart)).count();
        BigDecimal todayPay = sum(ctx.payments.stream()
                .filter(p -> p.getPayTime() != null && !p.getPayTime().isBefore(dayStart))
                .collect(Collectors.toList()), EduPayment::getAmount);

        card(data, "合同总量", ctx.contracts.size(), "份");
        card(data, "学生总量", ctx.students.size(), "人");
        card(data, "教师总量", ctx.teachers.stream().filter(t -> t.getStatus() != null && t.getStatus() == 1).count(), "人");
        card(data, "上课率", rate, rate.equals("-") ? "" : "%");
        card(data, "今日排课", todaySch, "节");
        card(data, "今日缴费", todayPay, "元");
        card(data, "教室使用量", roomsUsed, "间");
        card(data, "请假总量", leaveTotal, "次");

        // 近30日运营趋势：排课/签单/缴费 三序列对比
        Map<String, Object> schT = dayTrend(ctx.schedules, EduSchedule::getStartTime, 30);
        Map<String, Object> signT = dayTrend(ctx.contracts, EduContract::getCreateTime, 30);
        Map<String, Object> payT = dayTrend(ctx.payments, EduPayment::getPayTime, EduPayment::getAmount, 30);
        Map<String, Object> trendChart = chart("opsTrend", "近30日运营趋势（排课/签单/缴费）", "line", 24);
        trendChart.put("categories", schT.get("categories"));
        trendChart.put("series", Arrays.asList(
                seriesMap("排课(节)", schT.get("data")),
                seriesMap("签单(份)", signT.get("data")),
                seriesMap("缴费(元)", payT.get("data"))));
        charts(data).add(trendChart);
        // 校区收入（今日/本周/本月）
        LocalDate today = LocalDate.now();
        LocalDateTime weekStart = today.with(TemporalAdjusters.previousOrSame(DayOfWeek.MONDAY)).atStartOfDay();
        LocalDateTime monthStart = today.withDayOfMonth(1).atStartOfDay();
        Set<Long> orgIds = new LinkedHashSet<>();
        ctx.payments.forEach(p -> orgIds.add(p.getOrgId()));
        orgIds.remove(null);
        Map<String, Object> rankChart = chart("orgRank", "校区收入排名（今日/本周/本月）", "bar", 12);
        rankChart.put("categories", orgIds.stream().map(ctx::oName).collect(Collectors.toList()));
        rankChart.put("series", Arrays.asList(
                seriesMap("今日", paySum(ctx.payments, orgIds, today.atStartOfDay(), now)),
                seriesMap("本周", paySum(ctx.payments, orgIds, weekStart, now)),
                seriesMap("本月", paySum(ctx.payments, orgIds, monthStart, now))));
        charts(data).add(rankChart);
        // 课次状态分布
        List<Map<String, Object>> statusPie = ctx.schedules.stream()
                .filter(s -> s.getStatus() != null)
                .collect(Collectors.groupingBy(EduSchedule::getStatus, Collectors.counting()))
                .entrySet().stream()
                .map(en -> kv(SCH_STATUS.getOrDefault(en.getKey(), "其他"), en.getValue()))
                .collect(Collectors.toList());
        pieChart(data, "schStatus", "课次状态分布", 12, statusPie, "节");

        // 各校区汇总
        List<Map<String, Object>> rows = new ArrayList<>();
        Set<Long> allOrgs = new LinkedHashSet<>();
        ctx.students.forEach(s -> allOrgs.add(s.getOrgId()));
        ctx.clazzes.forEach(c -> allOrgs.add(c.getOrgId()));
        ctx.contracts.forEach(c -> allOrgs.add(c.getOrgId()));
        allOrgs.remove(null);
        for (Long oid : allOrgs) {
            Set<Long> tids = ctx.schedules.stream()
                    .filter(s -> oid.equals(s.getOrgId()) && s.getTeacherId() != null)
                    .map(EduSchedule::getTeacherId).collect(Collectors.toSet());
            Map<String, Object> r = new LinkedHashMap<>();
            r.put("org", ctx.oName(oid));
            r.put("students", ctx.students.stream().filter(s -> oid.equals(s.getOrgId())).count());
            r.put("teachers", tids.size());
            r.put("clazzes", ctx.clazzes.stream().filter(c -> oid.equals(c.getOrgId())).count());
            r.put("contracts", ctx.contracts.stream().filter(c -> oid.equals(c.getOrgId())).count());
            r.put("revenue", sum(ctx.payments.stream()
                    .filter(p -> oid.equals(p.getOrgId())).collect(Collectors.toList()), EduPayment::getAmount));
            rows.add(r);
        }
        table(data, "各校区汇总",
                cols("org:校区:140", "students:学生:80", "teachers:教师:80", "clazzes:班级:80",
                        "contracts:合同:80", "revenue:缴费收入(元):120"), rows);

        // 待办与预警：管理员视角"有什么要处理"
        LocalDate expireLine = LocalDate.now().plusDays(30);
        List<Map<String, Object>> todos = new ArrayList<>();
        todos.add(kv2("待审批旷课", ctx.absences.stream()
                .filter(a -> a.getStatus() != null && a.getStatus() == 0).count(), "72小时未审批将自动扣课"));
        todos.add(kv2("待回复咨询", ctx.consults.stream()
                .filter(c -> !StringUtils.hasText(c.getReply())).count(), "学生端问题咨询"));
        todos.add(kv2("待缴费合同", ctx.contracts.stream()
                .filter(c -> c.getStatus() != null && c.getStatus() == 0).count(), "已生效未缴费"));
        todos.add(kv2("30天内到期合同", ctx.contracts.stream()
                .filter(c -> c.getEndDate() != null && !c.getEndDate().isBefore(LocalDate.now())
                        && !c.getEndDate().isAfter(expireLine)
                        && (c.getStatus() == null || c.getStatus() < 3)).count(), "跟进续费或结课"));
        todos.add(kv2("已下课未登记", ctx.schedules.stream()
                .filter(s -> s.getEndTime() != null && s.getEndTime().isBefore(now)
                        && s.getStatus() != null && s.getStatus() == 0).count(), "到课/请假/旷课登记"));
        table(data, "待办与预警",
                cols("item:事项:160", "count:数量:90", "tip:说明:260"), todos);
        return data;
    }

    /** 待办行构建（事项/数量/说明） */
    private Map<String, Object> kv2(String item, Object count, String tip) {
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("item", item);
        m.put("count", count);
        m.put("tip", tip);
        return m;
    }

    /* ==================== 0. 平台运营（tenant_id=0 管理身份） ==================== */

    /**
     * 平台运营视角：企业规模与经营概况。平台管理员查询不带租户过滤（拦截器放行），
     * 各列表为全平台数据，按 tenant_id 分组统计。
     */
    private Map<String, Object> platformView(LoginUser lu, Ctx ctx) {
        Map<String, Object> data = base("platform", "平台运营视角");
        List<SysTenant> tenants = tenantMapper.selectList(null);
        // 平台租户行(id=0)不参与企业经营统计
        List<SysTenant> corps = new ArrayList<>();
        for (SysTenant t : tenants) if (t.getId() != null && t.getId() != 0) corps.add(t);
        LocalDateTime now = LocalDateTime.now();
        long active = corps.stream().filter(SysTenant::usable).count();
        long disabled = corps.stream().filter(t -> t.getStatus() != null && t.getStatus() == 0).count();
        long expired = corps.size() - active - disabled;
        List<SysUser> allUsers = sysUserMapper.selectList(null);
        LocalDate expireLine = LocalDate.now().plusDays(30);

        card(data, "企业总数", corps.size(), "家");
        card(data, "启用中", active, "家");
        card(data, "停用/过期", disabled + expired, "家");
        card(data, "30天内到期", corps.stream().filter(t -> t.getExpireTime() != null
                && t.getExpireTime().toLocalDate().isAfter(LocalDate.now())
                && !t.getExpireTime().toLocalDate().isAfter(expireLine)).count(), "家");
        card(data, "平台用户总数", allUsers.stream()
                .filter(u -> u.getTenantId() != null && u.getTenantId() != 0).count(), "人");

        // 各企业用户数
        Map<Long, String> corpNames = new HashMap<>();
        for (SysTenant t : corps) corpNames.put(t.getId(), t.getName());
        addBarChart(data, "corpUsers", "各企业用户数", 12,
                countBy(allUsers.stream().filter(u -> u.getTenantId() != null && u.getTenantId() != 0)
                        .collect(Collectors.toList()), SysUser::getTenantId, corpNames),
                corpNames, "人");
        // 近30日新增企业
        addLineChart(data, "newCorps", "近30日新增企业", 12,
                dayTrend(corps, SysTenant::getCreateTime, 30), "家");
        // 企业状态分布
        List<Map<String, Object>> statusPie = new ArrayList<>();
        statusPie.add(kv("正常运营", active));
        if (disabled > 0) statusPie.add(kv("已停用", disabled));
        if (expired > 0) statusPie.add(kv("已过期", expired));
        pieChart(data, "corpStatus", "企业状态分布", 12, statusPie, "家");

        // 企业经营概况表：用户/学生/班级/合同 规模 + 状态预警
        List<Map<String, Object>> rows = new ArrayList<>();
        for (SysTenant t : corps) {
            Map<String, Object> r = new LinkedHashMap<>();
            r.put("name", t.getName());
            r.put("code", t.getCode());
            String st = t.usable() ? "正常运营" : (t.getStatus() != null && t.getStatus() == 0 ? "已停用" : "已过期");
            r.put("status", st);
            r.put("expireTime", t.getExpireTime() == null ? "永久" : t.getExpireTime().toLocalDate().toString());
            r.put("admin", t.getAdminUsername() == null ? "-" : t.getAdminUsername());
            r.put("users", allUsers.stream().filter(u -> t.getId().equals(u.getTenantId())).count());
            r.put("students", ctx.students.stream().filter(s -> t.getId().equals(s.getTenantId())).count());
            r.put("clazzes", ctx.clazzes.stream().filter(c -> t.getId().equals(c.getTenantId())).count());
            r.put("contracts", ctx.contracts.stream().filter(c -> t.getId().equals(c.getTenantId())).count());
            rows.add(r);
        }
        table(data, "企业经营概况",
                cols("name:企业:150", "code:编码:90", "status:状态:100", "expireTime:到期:100",
                        "admin:管理员:80", "users:用户:70", "students:学生:70",
                        "clazzes:班级:70", "contracts:合同:70"), rows);
        return data;
    }

    /* ==================== 通用构建工具 ==================== */

    private Map<String, Object> base(String view, String viewName) {
        Map<String, Object> m = new HashMap<>();
        m.put("view", view);
        m.put("viewName", viewName);
        m.put("cards", new ArrayList<Map<String, Object>>());
        m.put("charts", new ArrayList<Map<String, Object>>());
        m.put("tables", new ArrayList<Map<String, Object>>());
        return m;
    }

    @SuppressWarnings("unchecked")
    private List<Map<String, Object>> cards(Map<String, Object> data) { return (List<Map<String, Object>>) data.get("cards"); }
    @SuppressWarnings("unchecked")
    private List<Map<String, Object>> charts(Map<String, Object> data) { return (List<Map<String, Object>>) data.get("charts"); }
    @SuppressWarnings("unchecked")
    private List<Map<String, Object>> tables(Map<String, Object> data) { return (List<Map<String, Object>>) data.get("tables"); }

    private void card(Map<String, Object> data, String label, Object value, String unit) {
        Map<String, Object> m = new HashMap<>();
        m.put("label", label);
        m.put("value", value == null ? 0 : value);
        m.put("unit", unit == null ? "" : unit);
        cards(data).add(m);
    }

    private Map<String, Object> chart(String key, String title, String type, int span) {
        Map<String, Object> m = new HashMap<>();
        m.put("key", key);
        m.put("title", title);
        m.put("type", type);
        m.put("span", span);
        return m;
    }

    private void pieChart(Map<String, Object> data, String key, String title, int span,
                          List<Map<String, Object>> pieData, String unit) {
        Map<String, Object> c = chart(key, title, "pie", span);
        c.put("data", pieData);
        c.put("unit", unit);
        charts(data).add(c);
    }

    /** 柱图：按分组计数/求和（值可为 Long 计数或 BigDecimal 金额） */
    private void addBarChart(Map<String, Object> data, String key, String title, int span,
                             Map<Long, ? extends Number> vals, Map<Long, String> names, String unit) {
        Map<String, Object> c = chart(key, title, "bar", span);
        c.put("categories", vals.keySet().stream().map(k -> names.getOrDefault(k, String.valueOf(k)))
                .collect(Collectors.toList()));
        c.put("series", singleSeries("数量", new ArrayList<Number>(vals.values())));
        c.put("unit", unit);
        charts(data).add(c);
    }

    /** 排名图（横向条形，按值倒序） */
    private void addRankChart(Map<String, Object> data, String key, String title, int span,
                              List<Map<String, Object>> rankData, String unit) {
        Map<String, Object> c = chart(key, title, "rank", span);
        c.put("data", rankData);
        c.put("unit", unit);
        charts(data).add(c);
    }

    private void addRankChart(Map<String, Object> data, String key, String title, int span,
                              Map<Long, ? extends Number> vals, Map<Long, String> names, String unit) {
        addRankChart(data, key, title, span, toList(vals, names), unit);
    }

    /** 折线趋势图（单序列，按日计数） */
    private <T> void addLineChart(Map<String, Object> data, String key, String title, int span,
                                  Map<String, Object> trend, String unit) {
        Map<String, Object> c = chart(key, title, "line", span);
        c.put("categories", trend.get("categories"));
        c.put("series", singleSeries(title, trend.get("data")));
        c.put("unit", unit);
        charts(data).add(c);
    }

    private List<Map<String, Object>> singleSeries(String name, Object data) {
        return new ArrayList<>(Collections.singletonList(seriesMap(name, data)));
    }

    private Map<String, Object> seriesMap(String name, Object data) {
        Map<String, Object> m = new HashMap<>();
        m.put("name", name);
        m.put("data", data);
        return m;
    }

    private void table(Map<String, Object> data, String title, List<Map<String, Object>> columns,
                       List<Map<String, Object>> rows) {
        Map<String, Object> t = new HashMap<>();
        t.put("title", title);
        t.put("columns", columns);
        t.put("rows", rows == null ? new ArrayList<>() : rows);
        tables(data).add(t);
    }

    /** 列定义："prop:label:minWidth" */
    private List<Map<String, Object>> cols(String... defs) {
        List<Map<String, Object>> list = new ArrayList<>();
        for (String d : defs) {
            String[] p = d.split(":");
            Map<String, Object> m = new HashMap<>();
            m.put("prop", p[0]);
            m.put("label", p[1]);
            m.put("width", p.length > 2 ? Integer.valueOf(p[2]) : 100);
            list.add(m);
        }
        return list;
    }

    private Map<String, Object> kv(String name, Object value) {
        Map<String, Object> m = new HashMap<>();
        m.put("name", name);
        m.put("value", value);
        return m;
    }

    /* ==================== 聚合工具 ==================== */

    private <T> Map<Long, Long> countBy(List<T> list, Function<T, Long> keyFn, Map<Long, String> names) {
        Map<Long, Long> m = new LinkedHashMap<>();
        for (T x : list) {
            Long k = keyFn.apply(x);
            if (k == null || !names.containsKey(k)) continue;
            m.merge(k, 1L, Long::sum);
        }
        return m;
    }

    private <T> Map<Long, BigDecimal> sumBy(List<T> list, Function<T, Long> keyFn,
                                            Function<T, BigDecimal> valFn, Map<Long, String> names) {
        Map<Long, BigDecimal> m = new LinkedHashMap<>();
        for (T x : list) {
            Long k = keyFn.apply(x);
            if (k == null || !names.containsKey(k)) continue;
            BigDecimal v = valFn.apply(x);
            m.merge(k, v == null ? BigDecimal.ZERO : v, BigDecimal::add);
        }
        return m;
    }

    private List<Map<String, Object>> toList(Map<Long, ? extends Number> vals) {
        return toList(vals, null);
    }

    private List<Map<String, Object>> toList(Map<Long, ? extends Number> vals, Map<Long, String> names) {
        return vals.entrySet().stream()
                .sorted((a, b) -> Double.compare(b.getValue().doubleValue(), a.getValue().doubleValue()))
                .map(e -> kv(names == null ? String.valueOf(e.getKey())
                        : names.getOrDefault(e.getKey(), String.valueOf(e.getKey())), e.getValue()))
                .collect(Collectors.toList());
    }

    private <T> BigDecimal sum(List<T> list, Function<T, BigDecimal> valFn) {
        BigDecimal s = BigDecimal.ZERO;
        for (T x : list) {
            BigDecimal v = valFn.apply(x);
            if (v != null) s = s.add(v);
        }
        return s;
    }

    private BigDecimal nvl(BigDecimal v) { return v == null ? BigDecimal.ZERO : v; }

    /** 教师平均评分（无评分返回 -1） */
    private double avgScore(List<EduEvaluation> evals, Long teacherId) {
        if (teacherId == null) return -1;
        List<EduEvaluation> mine = evals.stream()
                .filter(e -> teacherId.equals(e.getTeacherId()) && e.getScore() != null)
                .collect(Collectors.toList());
        return mine.isEmpty() ? -1 : mine.stream().mapToInt(EduEvaluation::getScore).average().orElse(-1);
    }

    /** 课次时长合计（小时，按开始/结束时间折算） */
    private BigDecimal durationHours(List<EduSchedule> list) {
        double h = 0;
        for (EduSchedule s : list) {
            if (s.getStartTime() == null || s.getEndTime() == null) continue;
            long mins = Duration.between(s.getStartTime(), s.getEndTime()).toMinutes();
            if (mins > 0) h += mins / 60.0;
        }
        return BigDecimal.valueOf(Math.round(h * 10) / 10.0);
    }

    /** 近N日按日计数 */
    private <T> Map<String, Object> dayTrend(List<T> list, Function<T, LocalDateTime> timeFn, int days) {
        return dayTrend(list, timeFn, null, days);
    }

    /** 近N日按日求和（valFn 为空则计数） */
    private <T> Map<String, Object> dayTrend(List<T> list, Function<T, LocalDateTime> timeFn,
                                             Function<T, BigDecimal> valFn, int days) {
        List<String> cats = new ArrayList<>();
        Set<String> catSet = new HashSet<>();
        LocalDate today = LocalDate.now();
        for (int i = days - 1; i >= 0; i--) {
            String d = today.minusDays(i).toString();
            cats.add(d); catSet.add(d);
        }
        Map<String, BigDecimal> byDay = new HashMap<>();
        for (T x : list) {
            LocalDateTime t = timeFn.apply(x);
            if (t == null) continue;
            String d = t.toLocalDate().toString();
            if (!catSet.contains(d)) continue;
            BigDecimal v = valFn == null ? BigDecimal.ONE
                    : (valFn.apply(x) == null ? BigDecimal.ZERO : valFn.apply(x));
            byDay.merge(d, v, BigDecimal::add);
        }
        Map<String, Object> m = new HashMap<>();
        m.put("categories", cats);
        m.put("data", cats.stream().map(c -> byDay.getOrDefault(c, BigDecimal.ZERO))
                .collect(Collectors.toList()));
        return m;
    }

    /** 校区区间缴费额（今日/本周/本月排名用） */
    private List<BigDecimal> paySum(List<EduPayment> payments, Set<Long> orgIds,
                                    LocalDateTime begin, LocalDateTime end) {
        List<BigDecimal> data = new ArrayList<>();
        for (Long oid : orgIds) {
            BigDecimal s = BigDecimal.ZERO;
            for (EduPayment p : payments) {
                if (!oid.equals(p.getOrgId()) || p.getPayTime() == null) continue;
                if (!p.getPayTime().isBefore(begin) && !p.getPayTime().isAfter(end))
                    s = s.add(p.getAmount() == null ? BigDecimal.ZERO : p.getAmount());
            }
            data.add(s);
        }
        return data;
    }

    private String fmtTime(LocalDateTime t) {
        return t == null ? "-" : t.toLocalDate() + " " + String.format("%02d:%02d", t.getHour(), t.getMinute());
    }
}
