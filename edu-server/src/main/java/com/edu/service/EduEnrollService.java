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
    private final EduPackageMapper packageMapper;
    private final com.edu.mapper.EduCoursePriceMapper coursePriceMapper;
    private final com.edu.mapper.EduEnrollLogMapper enrollLogMapper;
    private final com.edu.mapper.EduCourseCampusMapper courseCampusMapper;
    private final com.edu.mapper.EduAccountLogMapper accountLogMapper;

    /**
     * 学生订阅班课（报名/续报、选课中心）：优先按套餐订阅，课程/课时/单价随套餐带出；
     * 按课程订阅时可选定价行（edu_course_price）与份数，未选则回退课程默认/首条定价。
     */
    @Transactional
    public EduEnroll subscribe(com.edu.controller.EduEnrollController.SubscribeDTO dto) {
        EduEnroll enroll = doSubscribe(dto);
        writeLog(enroll, "CREATE", dto.getPayInfo());
        return enroll;
    }

    /** 订阅核心（不写日志）：转课复用，落库后按结算结果补写 CREATE 日志 */
    private EduEnroll doSubscribe(com.edu.controller.EduEnrollController.SubscribeDTO dto) {
        Long studentId = dto.getStudentId();
        Long packageId = dto.getPackageId();
        Long courseId = dto.getCourseId();
        Long clazzId = dto.getClazzId();
        Long teacherId = dto.getTeacherId();
        // 套餐定价（课程以套餐关联为准，赠课不计费、套餐为一口价不打折）
        Integer totalHours = null;
        BigDecimal price = null, discount = null, totalAmount = null;
        if (packageId != null) {
            EduPackage pkg = packageMapper.selectById(packageId);
            if (pkg == null || pkg.getDeleted() == 1 || pkg.getEnabled() != 1)
                throw new BizException("套餐不存在或已停用");
            courseId = pkg.getCourseId();
            BigDecimal hours = pkg.getHours() == null ? BigDecimal.ZERO : pkg.getHours();
            BigDecimal gift = pkg.getGiftHours() == null ? BigDecimal.ZERO : pkg.getGiftHours();
            totalHours = hours.add(gift).intValue();
            price = pkg.getUnitPrice() == null ? BigDecimal.ZERO : pkg.getUnitPrice();
            discount = BigDecimal.valueOf(100);
            totalAmount = price.multiply(hours);
        }

        // 新版套餐可不关联课程（按科目/授课模式/年级售卖）；按课程订阅时课程必须存在且上架
        EduCourse course = courseId == null ? null : courseMapper.selectById(courseId);
        if (courseId != null && course == null) throw new BizException("课程不存在");
        if (packageId == null && (course.getStatus() == null || course.getStatus() != 1))
            throw new BizException("课程已下架");

        if (clazzId != null) {
            EduClazz clazz = clazzMapper.selectById(clazzId);
            if (clazz == null) throw new BizException("班级不存在");
            // 校验容量
            Long cnt = enrollMapper.selectCount(new LambdaQueryWrapper<EduEnroll>()
                    .eq(EduEnroll::getClazzId, clazzId).eq(EduEnroll::getStatus, 1));
            if (cnt >= clazz.getCapacity()) throw new BizException("班级名额已满");
        }

        // 报名校区（一单一校区）：取课程中间表关联校区；显式提交时校验归属，未提交时取首条关联校区
        String campus = null;
        if (courseId != null) {
            java.util.List<String> cs = courseCampusMapper.selectList(
                            new LambdaQueryWrapper<com.edu.entity.EduCourseCampus>()
                                    .eq(com.edu.entity.EduCourseCampus::getCourseId, courseId)
                                    .orderByAsc(com.edu.entity.EduCourseCampus::getId))
                    .stream().map(com.edu.entity.EduCourseCampus::getCampus)
                    .collect(java.util.stream.Collectors.toList());
            campus = dto.getCampus();
            if (campus != null && !campus.isEmpty()) {
                if (!cs.contains(campus))
                    throw new BizException("校区「" + campus + "」未开设该课程");
            } else {
                campus = cs.isEmpty() ? null : cs.get(0);
            }
        }

        // 防止重复订阅（在读状态不可重复，退订/结课后可续报）：
        // 同一学生+课程+校区判重（不同校区可各报一条），无课程套餐按套餐判重
        Long dup = enrollMapper.selectCount(new LambdaQueryWrapper<EduEnroll>()
                .eq(EduEnroll::getStudentId, studentId)
                .eq(courseId != null, EduEnroll::getCourseId, courseId)
                .eq(courseId == null, EduEnroll::getPackageId, packageId)
                .eq(courseId != null && campus != null, EduEnroll::getCampus, campus)
                .isNull(courseId != null && campus == null, EduEnroll::getCampus)
                .eq(EduEnroll::getStatus, 1));
        if (dup > 0) throw new BizException("该学生已订阅此课程（" + (campus == null ? "未分区" : campus) + "），请勿重复订阅");

        EduEnroll enroll = new EduEnroll();
        enroll.setTenantId(AuthContext.get().getTenantId());
        enroll.setStudentId(studentId);
        enroll.setCourseId(courseId);
        enroll.setCampus(campus);
        enroll.setPackageId(packageId);
        enroll.setClazzId(clazzId);
        enroll.setUsedHours(0);
        if (packageId != null) {
            enroll.setTotalHours(totalHours);
            enroll.setPrice(price);
            enroll.setDiscount(discount);
            enroll.setTotalAmount(totalAmount);
        } else {
            totalHours = course.getTotalHours();
            price = course.getPrice();
            if (dto.getPriceId() != null) {
                // 按所选定价行 × 份数计价（仅按课时模式定价行）
                com.edu.entity.EduCoursePrice pr = coursePriceMapper.selectById(dto.getPriceId());
                if (pr == null || !courseId.equals(pr.getCourseId()) || pr.getChargeMode() != 1)
                    throw new BizException("定价标准不存在或不属于该课程");
                int qty = dto.getQty() == null || dto.getQty() < 1 ? 1 : dto.getQty();
                totalHours = (int) Math.round(pr.getHours().doubleValue() * qty);
                price = pr.getTotalAmount().divide(pr.getHours(), 2, BigDecimal.ROUND_HALF_UP);
                totalAmount = pr.getTotalAmount().multiply(BigDecimal.valueOf(qty));
            } else if (totalHours == null || totalHours == 0 || price == null
                    || BigDecimal.ZERO.compareTo(price) == 0) {
                // 课程无基础定价时（新版定价存 edu_course_price），按首条按课时定价行取数
                com.edu.entity.EduCoursePrice first = coursePriceMapper.selectOne(
                        new LambdaQueryWrapper<com.edu.entity.EduCoursePrice>()
                                .eq(com.edu.entity.EduCoursePrice::getCourseId, courseId)
                                .eq(com.edu.entity.EduCoursePrice::getChargeMode, 1)
                                .orderByAsc(com.edu.entity.EduCoursePrice::getSort).last("LIMIT 1"));
                if (first != null && first.getHours() != null
                        && first.getHours().compareTo(BigDecimal.ZERO) > 0) {
                    totalHours = (int) Math.round(first.getHours().doubleValue());
                    price = first.getTotalAmount().divide(first.getHours(), 2, BigDecimal.ROUND_HALF_UP);
                    totalAmount = first.getTotalAmount();
                }
            }
            if (totalHours == null) totalHours = 0;
            if (price == null) price = BigDecimal.ZERO;
            enroll.setTotalHours(totalHours);
            enroll.setPrice(price);
            enroll.setDiscount(BigDecimal.valueOf(100));
            enroll.setTotalAmount(totalAmount != null ? totalAmount
                    : price.multiply(BigDecimal.valueOf(totalHours)));
        }
        // 未指定老师时取课程默认授课老师（无关联课程时留空，后续排课指派）
        enroll.setTeacherId(teacherId != null ? teacherId
                : (course != null ? course.getTeacherId() : null));
        // 本次实收（支付信息按比例分摊到各课程，未传为 0）
        enroll.setPaidAmount(dto.getPaidAmount() == null ? BigDecimal.ZERO : dto.getPaidAmount());
        // 支付明细JSON留档
        if (dto.getPayDetail() != null && !dto.getPayDetail().isEmpty())
            enroll.setPayDetail(dto.getPayDetail());
        enroll.setStatus(1);
        // 优惠：小计减优惠（下限0），金额留档
        if (dto.getDiscountAmount() != null && dto.getDiscountAmount().compareTo(BigDecimal.ZERO) > 0) {
            BigDecimal before = enroll.getTotalAmount() == null ? BigDecimal.ZERO : enroll.getTotalAmount();
            BigDecimal after = before.subtract(dto.getDiscountAmount());
            if (after.compareTo(BigDecimal.ZERO) < 0) after = BigDecimal.ZERO;
            enroll.setTotalAmount(after);
            enroll.setDiscountAmount(dto.getDiscountAmount());
        }
        // 优惠明细留档（编辑回显不合并多行优惠）
        if (dto.getDiscountDetail() != null && !dto.getDiscountDetail().isEmpty())
            enroll.setDiscountDetail(dto.getDiscountDetail());
        // 有效期（勾选后指定）与对内备注
        if (dto.getValidDate() != null && !dto.getValidDate().isEmpty()) {
            try { enroll.setValidDate(java.time.LocalDate.parse(dto.getValidDate())); }
            catch (Exception ignore) { /* 非法日期忽略 */ }
        }
        enroll.setRemark(dto.getRemark());
        // 经办信息：来源/经办人（默认当前登录人）/经办日期（默认今天）/采单员/咨询顾问
        enroll.setSource(dto.getSource());
        enroll.setHandler(dto.getHandler() == null || dto.getHandler().isEmpty()
                ? AuthContext.get().getRealName() : dto.getHandler());
        if (dto.getHandleDate() != null && !dto.getHandleDate().isEmpty()) {
            try { enroll.setHandleDate(java.time.LocalDate.parse(dto.getHandleDate())); }
            catch (Exception ignore) { /* 非法日期忽略 */ }
        } else {
            enroll.setHandleDate(java.time.LocalDate.now());
        }
        enroll.setOrderTaker(dto.getOrderTaker());
        enroll.setConsultant(dto.getConsultant());
        enrollMapper.insert(enroll);
        return enroll;
    }

    /** 对外暴露：controller update 也调日志 */
    public void writeLogPublic(EduEnroll enroll, String action, String payInfo) {
        writeLog(enroll, action, payInfo);
    }

    /** 报名/续报操作日志：记录每次新增/修改 */
    private void writeLog(EduEnroll enroll, String action, String payInfo) {
        try {
            com.edu.entity.EduEnrollLog log = new com.edu.entity.EduEnrollLog();
            log.setTenantId(enroll.getTenantId() == null ? 0L : enroll.getTenantId());
            log.setEnrollId(enroll.getId());
            log.setAction(action);
            log.setContent(buildContent(enroll));
            log.setTotalAmount(enroll.getTotalAmount());
            log.setPaidAmount(enroll.getPaidAmount());
            log.setPayInfo(payInfo == null ? "合计实收 ¥" + (enroll.getPaidAmount() == null
                    ? "0.00" : enroll.getPaidAmount().toPlainString()) : payInfo);
            log.setBalanceChange(BigDecimal.ZERO);
            log.setOperator(AuthContext.get().getRealName());
            enrollLogMapper.insert(log);
        } catch (Exception ignore) { /* 日志失败不影响主流程 */ }
    }

    /** 操作内容：课程名，价格×数量=总价 */
    private String buildContent(EduEnroll e) {
        String name = e.getPackageName();
        if (name == null || name.isEmpty()) {
            EduCourse c = e.getCourseId() == null ? null : courseMapper.selectById(e.getCourseId());
            name = c == null ? "-" : c.getName();
        }
        return name + "，" + e.getTotalHours() + "课时×¥" + (e.getPrice() == null ? "0" : e.getPrice().stripTrailingZeros().toPlainString())
                + "=" + (e.getTotalAmount() == null ? "0" : e.getTotalAmount().stripTrailingZeros().toPlainString()) + "元"
                + (e.getDiscountAmount() != null && e.getDiscountAmount().compareTo(BigDecimal.ZERO) > 0
                        ? "，优惠¥" + e.getDiscountAmount().stripTrailingZeros().toPlainString() : "");
    }

    /**
     * 删除报名记录（报名/续报编辑页移除课程卡片时调用）：
     * 取消该生该课程未来待上的排课，删除报名记录及其操作日志。
     */
    @Transactional
    public void deleteEnroll(Long id) {
        EduEnroll enroll = mustGet(id);
        // 取消该生该课程未来待上的排课
        if (enroll.getCourseId() != null) {
            EduSchedule upd = new EduSchedule();
            upd.setStatus(2);
            upd.setCancelReason("删除报名单自动取消");
            scheduleMapper.update(upd, new LambdaQueryWrapper<EduSchedule>()
                    .eq(EduSchedule::getStudentId, enroll.getStudentId())
                    .eq(EduSchedule::getCourseId, enroll.getCourseId())
                    .eq(EduSchedule::getStatus, 0));
        }
        enrollMapper.deleteById(id);
        enrollLogMapper.delete(new LambdaQueryWrapper<com.edu.entity.EduEnrollLog>()
                .eq(com.edu.entity.EduEnrollLog::getEnrollId, id));
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

    /**
     * 转课（办理中心-转课）：把一条在读报名单转为另一门课程。
     * 原单已付金额抵扣新课程应收，多退少补：需补差时按渠道实收收款（可欠费）；
     * 抵扣超出转入应收的部分自动退入学员账户余额（退费入账）。
     * 原单置为已转课(4)并取消原课程未来排课（转入同课程时保留），两单备注互相关联。
     */
    @Transactional
    public java.util.Map<String, Object> transfer(com.edu.controller.EduEnrollController.TransferDTO dto) {
        if (dto.getStudentId() == null || dto.getFromEnrollId() == null || dto.getCourseId() == null)
            throw new BizException("学员、转出报名单与转入课程为必填项");
        EduEnroll from = mustGet(dto.getFromEnrollId());
        if (!dto.getStudentId().equals(from.getStudentId()))
            throw new BizException("转出报名单不属于该学员");
        if (from.getStatus() == null || from.getStatus() != 1)
            throw new BizException("仅在读状态的报名单可转课");
        BigDecimal deduct = from.getPaidAmount() == null ? BigDecimal.ZERO : from.getPaidAmount();
        BigDecimal received = dto.getPaidAmount() == null ? BigDecimal.ZERO : dto.getPaidAmount();

        // 1. 原单先置为已转课（转出后同课程可再报，避免订阅判重拦截）
        from.setStatus(4);
        enrollMapper.updateById(from);

        // 2. 新单走订阅流程（计价/校区/班级容量/判重校验复用）
        com.edu.controller.EduEnrollController.SubscribeDTO sub = new com.edu.controller.EduEnrollController.SubscribeDTO();
        sub.setStudentId(dto.getStudentId());
        sub.setCourseId(dto.getCourseId());
        sub.setCampus(dto.getCampus());
        sub.setClazzId(dto.getClazzId());
        sub.setTeacherId(dto.getTeacherId());
        sub.setPriceId(dto.getPriceId());
        sub.setQty(dto.getQty());
        sub.setDiscountAmount(dto.getDiscountAmount());
        sub.setValidDate(dto.getValidDate());
        String userRemark = dto.getRemark() == null ? "" : dto.getRemark().trim();
        sub.setRemark("转课自 EN" + from.getId() + (userRemark.isEmpty() ? "" : "：" + userRemark));
        sub.setSource(dto.getSource());
        sub.setHandler(dto.getHandler());
        sub.setHandleDate(dto.getHandleDate());
        sub.setOrderTaker(dto.getOrderTaker());
        sub.setConsultant(dto.getConsultant());
        EduEnroll neu = doSubscribe(sub);

        // 3. 实收结算：原单抵扣＋渠道实收；超出应收的部分退入账户余额，不足留欠费（可走补费）
        BigDecimal due = neu.getTotalAmount() == null ? BigDecimal.ZERO : neu.getTotalAmount();
        BigDecimal paidTotal = deduct.add(received);
        BigDecimal paidCapped = paidTotal.min(due).max(BigDecimal.ZERO);
        BigDecimal refund = paidTotal.subtract(due).max(BigDecimal.ZERO);
        BigDecimal arrears = due.subtract(paidTotal).max(BigDecimal.ZERO);
        neu.setPaidAmount(paidCapped);
        if (dto.getPayDetail() != null && !dto.getPayDetail().isEmpty())
            neu.setPayDetail(dto.getPayDetail());
        enrollMapper.updateById(neu);

        // 4. 原单收尾：备注关联新单；转入课程与原课程不同时取消原课程未来排课
        String fromRemark = from.getRemark() == null ? "" : from.getRemark().trim();
        from.setRemark("已转课至 EN" + neu.getId() + (fromRemark.isEmpty() ? "" : "；" + fromRemark));
        enrollMapper.updateById(from);
        if (from.getCourseId() != null && !from.getCourseId().equals(neu.getCourseId())) {
            EduSchedule upd = new EduSchedule();
            upd.setStatus(2);
            upd.setCancelReason("转课自动取消");
            scheduleMapper.update(upd, new LambdaQueryWrapper<EduSchedule>()
                    .eq(EduSchedule::getStudentId, from.getStudentId())
                    .eq(EduSchedule::getCourseId, from.getCourseId())
                    .eq(EduSchedule::getStatus, 0));
        }

        // 5. 操作日志：新单 CREATE（补差明细＋抵扣说明，实收为结算后金额）、原单 TRANSFER（转出留痕）
        writeLogPublic(neu, "CREATE", (dto.getPayInfo() == null || dto.getPayInfo().isEmpty()
                ? "合计实收 ¥" : dto.getPayInfo())
                + "，原单 EN" + from.getId() + " 抵扣 ¥" + deduct.stripTrailingZeros().toPlainString());
        writeLogPublic(from, "TRANSFER", "转出抵扣 ¥" + deduct.stripTrailingZeros().toPlainString()
                + "，转入 EN" + neu.getId());

        // 6. 退差入账：抵扣超出转入应收的部分自动退入学员账户余额（退费入账）
        if (refund.compareTo(BigDecimal.ZERO) > 0) {
            EduAccountLog acc = new EduAccountLog();
            acc.setTenantId(neu.getTenantId() == null ? 0L : neu.getTenantId());
            acc.setStudentId(dto.getStudentId());
            acc.setType(2);
            acc.setAmount(refund);
            acc.setCampus(neu.getCampus());
            acc.setSource(dto.getSource());
            acc.setRemark("转课退差入账：EN" + from.getId() + " → EN" + neu.getId());
            acc.setOperator(AuthContext.get().getRealName());
            accountLogMapper.insert(acc);
        }

        java.util.Map<String, Object> r = new java.util.HashMap<>();
        r.put("enrollId", neu.getId());
        r.put("fromId", from.getId());
        r.put("deduct", deduct);
        r.put("due", due);
        r.put("received", received);
        r.put("refund", refund);
        r.put("arrears", arrears);
        return r;
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

    /** 学生在读报名记录（已退订/已转课不在此列） */
    public List<EduEnroll> myEnrolls(Long studentId) {
        return enrollMapper.selectList(new LambdaQueryWrapper<EduEnroll>()
                .eq(EduEnroll::getStudentId, studentId)
                .notIn(EduEnroll::getStatus, 3, 4)
                .orderByDesc(EduEnroll::getId));
    }
}
