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
    private final com.edu.mapper.EduMaterialLogMapper materialLogMapper;
    private final com.edu.mapper.EduSupplementLogMapper supplementLogMapper;

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
     * 转课（办理中心-转课）：把在读报名单的剩余课时按单转出，学费按单价折算
     * 自动转入学员账户余额（退费入账），后续报名可用余额抵扣。
     * 全部剩余转出后报名单置为已转课(4)，且该课程无其他在读单时取消未来排课；
     * 部分转出仅扣减总课时，在读状态与排课保持不变。
     */
    @Transactional
    public java.util.Map<String, Object> transfer(com.edu.controller.EduEnrollController.TransferDTO dto) {
        if (dto.getStudentId() == null || dto.getItems() == null || dto.getItems().isEmpty())
            throw new BizException("请选择要转出的报名单并填写转出课时");
        BigDecimal total = BigDecimal.ZERO;
        int closed = 0, count = 0;
        java.util.List<String> parts = new java.util.ArrayList<>();
        String campus = null;
        for (com.edu.controller.EduEnrollController.TransferDTO.ItemDTO item : dto.getItems()) {
            if (item.getEnrollId() == null || item.getHours() == null || item.getHours() < 1) continue;
            EduEnroll e = mustGet(item.getEnrollId());
            if (!dto.getStudentId().equals(e.getStudentId()))
                throw new BizException("报名单 EN" + e.getId() + " 不属于该学员");
            if (e.getStatus() == null || e.getStatus() != 1)
                throw new BizException("报名单 EN" + e.getId() + " 不在读，无法转出");
            int used = e.getUsedHours() == null ? 0 : e.getUsedHours();
            int totalHours = e.getTotalHours() == null ? 0 : e.getTotalHours();
            int remaining = totalHours - used;
            if (remaining <= 0) throw new BizException("报名单 EN" + e.getId() + " 已无剩余课时");
            if (item.getHours() > remaining)
                throw new BizException("EN" + e.getId() + " 本次转出课时不能超过剩余课时（" + remaining + "）");
            // 转出金额按实收口径折算：订单实收 × 转出课时 ÷ 购买课时（报名欠费时实收低，转出同步降低）
            BigDecimal paid = e.getPaidAmount() == null ? BigDecimal.ZERO : e.getPaidAmount();
            BigDecimal amt = totalHours > 0
                    ? paid.multiply(BigDecimal.valueOf(item.getHours()))
                          .divide(BigDecimal.valueOf(totalHours), 2, BigDecimal.ROUND_HALF_UP)
                    : BigDecimal.ZERO;

            // 扣减总课时；剩余全部转出时报名单置为已转课
            e.setTotalHours(totalHours - item.getHours());
            boolean outAll = e.getTotalHours() - used <= 0;
            if (outAll) {
                e.setStatus(4);
                closed++;
                String old = e.getRemark() == null ? "" : e.getRemark().trim();
                e.setRemark("转课转出全部剩余课时" + (old.isEmpty() ? "" : "；" + old));
            }
            enrollMapper.updateById(e);

            // 全部转出且该课程无其他在读单：取消该课程未来排课（该单已置4，计数不含自身）
            if (outAll && e.getCourseId() != null) {
                Long others = enrollMapper.selectCount(new LambdaQueryWrapper<EduEnroll>()
                        .eq(EduEnroll::getStudentId, e.getStudentId())
                        .eq(EduEnroll::getCourseId, e.getCourseId())
                        .eq(EduEnroll::getStatus, 1));
                if (others == 0) {
                    EduSchedule upd = new EduSchedule();
                    upd.setStatus(2);
                    upd.setCancelReason("转课自动取消");
                    scheduleMapper.update(upd, new LambdaQueryWrapper<EduSchedule>()
                            .eq(EduSchedule::getStudentId, e.getStudentId())
                            .eq(EduSchedule::getCourseId, e.getCourseId())
                            .eq(EduSchedule::getStatus, 0));
                }
            }

            writeLogPublic(e, "TRANSFER", "转出 " + item.getHours() + " 课时（实收折算，订单实收 ¥"
                    + paid.stripTrailingZeros().toPlainString() + "/" + totalHours + "课时）= ¥"
                    + amt.stripTrailingZeros().toPlainString()
                    + (outAll ? "（已全部转出）" : "，剩余 " + (e.getTotalHours() - used) + " 课时"));
            total = total.add(amt);
            count++;
            parts.add("EN" + e.getId() + " 转出" + item.getHours() + "课时¥"
                    + amt.stripTrailingZeros().toPlainString());
            if (campus == null) campus = e.getCampus();
        }
        if (count == 0) throw new BizException("请填写有效的转出课时");

        // 转出学费自动转入学员账户余额（退费入账）
        EduAccountLog acc = new EduAccountLog();
        acc.setTenantId(AuthContext.get().getTenantId());
        acc.setStudentId(dto.getStudentId());
        acc.setType(2);
        acc.setAmount(total);
        acc.setCampus(campus);
        acc.setSource(dto.getSource());
        if (dto.getHandleDate() != null && !dto.getHandleDate().isEmpty()) {
            try { acc.setHandleDate(java.time.LocalDate.parse(dto.getHandleDate())); }
            catch (Exception ignore) { /* 非法日期忽略 */ }
        }
        String userRemark = dto.getRemark() == null ? "" : dto.getRemark().trim();
        acc.setRemark("转课转出学费入账：" + String.join("；", parts)
                + (userRemark.isEmpty() ? "" : "；" + userRemark));
        acc.setOperator(AuthContext.get().getRealName());
        accountLogMapper.insert(acc);

        java.util.Map<String, Object> r = new java.util.HashMap<>();
        r.put("count", count);
        r.put("closed", closed);
        r.put("hours", dto.getItems().stream()
                .filter(i -> i.getHours() != null && i.getHours() > 0)
                .mapToInt(com.edu.controller.EduEnrollController.TransferDTO.ItemDTO::getHours).sum());
        r.put("total", total);
        return r;
    }

    /**
     * 退费（办理中心-退费）：把在读报名单的剩余课时按单退出，金额按口径折算
     * 自动转结到学员账户余额（退费入账）：默认按实收口径（实收×课时÷购买课时），
     * 按原价退费时按原单价×课时（优惠部分一并退）。
     * 全部剩余退出后报名单置为已退订(3)并取消未来排课（该课程无其他在读单时）；
     * 部分退出仅扣减总课时，在读状态与排课保持不变。
     */
    @Transactional
    public java.util.Map<String, Object> refund(com.edu.controller.EduEnrollController.RefundDTO dto) {
        if (dto.getStudentId() == null || dto.getItems() == null || dto.getItems().isEmpty())
            throw new BizException("请选择要退费的报名单并填写退出课时");
        BigDecimal total = BigDecimal.ZERO;
        int closed = 0, count = 0;
        java.util.List<String> parts = new java.util.ArrayList<>();
        String campus = null;
        for (com.edu.controller.EduEnrollController.RefundDTO.ItemDTO item : dto.getItems()) {
            if (item.getEnrollId() == null || item.getHours() == null || item.getHours() < 1) continue;
            EduEnroll e = mustGet(item.getEnrollId());
            if (!dto.getStudentId().equals(e.getStudentId()))
                throw new BizException("报名单 EN" + e.getId() + " 不属于该学员");
            if (e.getStatus() == null || e.getStatus() != 1)
                throw new BizException("报名单 EN" + e.getId() + " 不在读，无法退费");
            int used = e.getUsedHours() == null ? 0 : e.getUsedHours();
            int totalHours = e.getTotalHours() == null ? 0 : e.getTotalHours();
            int remaining = totalHours - used;
            if (remaining <= 0) throw new BizException("报名单 EN" + e.getId() + " 已无剩余课时");
            if (item.getHours() > remaining)
                throw new BizException("EN" + e.getId() + " 本次退出课时不能超过剩余课时（" + remaining + "）");
            // 退费金额：按原价退费=原单价×课时（优惠一并退）；默认按实收口径折算
            BigDecimal amt;
            if (item.getByOriginal() != null && item.getByOriginal()) {
                amt = (e.getPrice() == null ? BigDecimal.ZERO : e.getPrice())
                        .multiply(BigDecimal.valueOf(item.getHours()));
            } else {
                BigDecimal paid = e.getPaidAmount() == null ? BigDecimal.ZERO : e.getPaidAmount();
                amt = totalHours > 0
                        ? paid.multiply(BigDecimal.valueOf(item.getHours()))
                              .divide(BigDecimal.valueOf(totalHours), 2, BigDecimal.ROUND_HALF_UP)
                        : BigDecimal.ZERO;
            }

            // 扣减总课时；剩余全部退出时报名单置为已退订
            e.setTotalHours(totalHours - item.getHours());
            boolean outAll = e.getTotalHours() - used <= 0;
            if (outAll) {
                e.setStatus(3);
                closed++;
                String old = e.getRemark() == null ? "" : e.getRemark().trim();
                e.setRemark("退费退出全部剩余课时" + (old.isEmpty() ? "" : "；" + old));
            }
            enrollMapper.updateById(e);

            // 全部退出且该课程无其他在读单：取消该课程未来排课
            if (outAll && e.getCourseId() != null) {
                Long others = enrollMapper.selectCount(new LambdaQueryWrapper<EduEnroll>()
                        .eq(EduEnroll::getStudentId, e.getStudentId())
                        .eq(EduEnroll::getCourseId, e.getCourseId())
                        .eq(EduEnroll::getStatus, 1));
                if (others == 0) {
                    EduSchedule upd = new EduSchedule();
                    upd.setStatus(2);
                    upd.setCancelReason("退费自动取消");
                    scheduleMapper.update(upd, new LambdaQueryWrapper<EduSchedule>()
                            .eq(EduSchedule::getStudentId, e.getStudentId())
                            .eq(EduSchedule::getCourseId, e.getCourseId())
                            .eq(EduSchedule::getStatus, 0));
                }
            }

            writeLogPublic(e, "REFUND", "退出 " + item.getHours() + " 课时（"
                    + (item.getByOriginal() != null && item.getByOriginal() ? "按原价" : "实收折算")
                    + "）= ¥" + amt.stripTrailingZeros().toPlainString()
                    + (outAll ? "（已全部退出，已退订）" : "，剩余 " + (e.getTotalHours() - used) + " 课时"));
            total = total.add(amt);
            count++;
            parts.add("EN" + e.getId() + " 退" + item.getHours() + "课时¥"
                    + amt.stripTrailingZeros().toPlainString());
            if (campus == null) campus = e.getCampus();
        }
        if (count == 0) throw new BizException("请填写有效的退出课时");

        // 结算：实退合计（渠道退款，不超过应退）＋差额自动转结账户余额（退费入账）
        BigDecimal paidOut = dto.getPaidOut() == null ? BigDecimal.ZERO : dto.getPaidOut().max(BigDecimal.ZERO);
        if (paidOut.compareTo(total) > 0)
            throw new BizException("实退金额 ¥" + paidOut.toPlainString() + " 不能超过应退金额 ¥" + total.toPlainString());
        BigDecimal toBalance = total.subtract(paidOut);

        EduAccountLog acc = new EduAccountLog();
        acc.setTenantId(AuthContext.get().getTenantId());
        acc.setStudentId(dto.getStudentId());
        acc.setType(2);
        acc.setAmount(toBalance);
        acc.setCampus(dto.getCampus() != null && !dto.getCampus().isEmpty() ? dto.getCampus() : campus);
        if (dto.getPayDetail() != null && !dto.getPayDetail().isEmpty())
            acc.setPayDetail(dto.getPayDetail());
        acc.setSource(dto.getSource());
        if (dto.getHandleDate() != null && !dto.getHandleDate().isEmpty()) {
            try { acc.setHandleDate(java.time.LocalDate.parse(dto.getHandleDate())); }
            catch (Exception ignore) { /* 非法日期忽略 */ }
        }
        String userRemark = dto.getRemark() == null ? "" : dto.getRemark().trim();
        acc.setRemark("退费转结入账：" + String.join("；", parts)
                + "；应退 ¥" + total.stripTrailingZeros().toPlainString()
                + "，实退 ¥" + paidOut.stripTrailingZeros().toPlainString()
                + "，转结余额 ¥" + toBalance.stripTrailingZeros().toPlainString()
                + (userRemark.isEmpty() ? "" : "；" + userRemark));
        acc.setOperator(AuthContext.get().getRealName());
        accountLogMapper.insert(acc);

        java.util.Map<String, Object> r = new java.util.HashMap<>();
        r.put("count", count);
        r.put("closed", closed);
        r.put("hours", dto.getItems().stream()
                .filter(i -> i.getHours() != null && i.getHours() > 0)
                .mapToInt(com.edu.controller.EduEnrollController.RefundDTO.ItemDTO::getHours).sum());
        r.put("total", total);
        r.put("paidOut", paidOut);
        r.put("toBalance", toBalance);
        return r;
    }

    /**
     * 补费（办理中心-补费）：给欠费的报名单/教材杂费单追加收款。
     * 逐项校验欠费上限后把本次补费金额累加到原单实收，写补费流水留档；
     * 实收（渠道）可小于补费金额合计，差额继续挂欠费。
     */
    @Transactional
    public java.util.Map<String, Object> supplement(com.edu.controller.EduEnrollController.SupplementDTO dto) {
        if (dto.getStudentId() == null || dto.getItems() == null || dto.getItems().isEmpty())
            throw new BizException("请选择要补费的记录并填写金额");
        BigDecimal total = BigDecimal.ZERO;
        int count = 0;
        java.util.List<String> parts = new java.util.ArrayList<>();
        for (com.edu.controller.EduEnrollController.SupplementDTO.ItemDTO item : dto.getItems()) {
            if (item.getRefId() == null || item.getAmount() == null
                    || item.getAmount().compareTo(BigDecimal.ZERO) <= 0) continue;
            BigDecimal amt = item.getAmount();
            if ("material".equals(item.getKind())) {
                com.edu.entity.EduMaterialLog ml = materialLogMapper.selectById(item.getRefId());
                if (ml == null || !dto.getStudentId().equals(ml.getStudentId()))
                    throw new BizException("教材杂费单 MT" + item.getRefId() + " 不存在或不属于该学员");
                BigDecimal due = (ml.getAmount() == null ? BigDecimal.ZERO : ml.getAmount())
                        .subtract(ml.getPaidAmount() == null ? BigDecimal.ZERO : ml.getPaidAmount());
                if (due.compareTo(BigDecimal.ZERO) <= 0) throw new BizException("教材杂费单 MT" + item.getRefId() + " 无欠费");
                if (amt.compareTo(due) > 0)
                    throw new BizException("MT" + item.getRefId() + " 本次补费不能超过欠费 ¥" + due.toPlainString());
                ml.setPaidAmount((ml.getPaidAmount() == null ? BigDecimal.ZERO : ml.getPaidAmount()).add(amt));
                materialLogMapper.updateById(ml);
            } else {
                EduEnroll e = mustGet(item.getRefId());
                if (!dto.getStudentId().equals(e.getStudentId()))
                    throw new BizException("报名单 EN" + e.getId() + " 不属于该学员");
                BigDecimal due = (e.getTotalAmount() == null ? BigDecimal.ZERO : e.getTotalAmount())
                        .subtract(e.getPaidAmount() == null ? BigDecimal.ZERO : e.getPaidAmount());
                if (e.getStatus() == null || e.getStatus() != 1 || due.compareTo(BigDecimal.ZERO) <= 0)
                    throw new BizException("报名单 EN" + e.getId() + " 不在读或无欠费");
                if (amt.compareTo(due) > 0)
                    throw new BizException("EN" + e.getId() + " 本次补费不能超过欠费 ¥" + due.toPlainString());
                e.setPaidAmount((e.getPaidAmount() == null ? BigDecimal.ZERO : e.getPaidAmount()).add(amt));
                enrollMapper.updateById(e);
                writeLogPublic(e, "SUPPLEMENT", "补费 ¥" + amt.stripTrailingZeros().toPlainString()
                        + "，欠费 ¥" + due.subtract(amt).stripTrailingZeros().toPlainString());
            }
            total = total.add(amt);
            count++;
            parts.add((("material".equals(item.getKind())) ? "MT" : "EN") + item.getRefId()
                    + " 补¥" + amt.stripTrailingZeros().toPlainString());
        }
        if (count == 0) throw new BizException("请填写有效的补费金额");
        BigDecimal paid = dto.getPaidAmount() == null ? BigDecimal.ZERO : dto.getPaidAmount();
        if (paid.compareTo(total) > 0) throw new BizException("实收不能超过补费金额合计");

        com.edu.entity.EduSupplementLog log = new com.edu.entity.EduSupplementLog();
        log.setTenantId(AuthContext.get().getTenantId());
        log.setStudentId(dto.getStudentId());
        log.setItems(dto.getItemsJson());
        log.setAmount(total);
        log.setPaidAmount(paid);
        log.setPayDetail(dto.getPayDetail());
        log.setCampus(dto.getCampus());
        log.setSource(dto.getSource());
        if (dto.getHandleDate() != null && !dto.getHandleDate().isEmpty()) {
            try { log.setHandleDate(java.time.LocalDate.parse(dto.getHandleDate())); }
            catch (Exception ignore) { /* 非法日期忽略 */ }
        } else {
            log.setHandleDate(java.time.LocalDate.now());
        }
        log.setOrderTaker(dto.getOrderTaker());
        log.setConsultant(dto.getConsultant());
        log.setOperator(AuthContext.get().getRealName());
        String userRemark = dto.getRemark() == null ? "" : dto.getRemark().trim();
        log.setRemark("补费 ¥" + total.stripTrailingZeros().toPlainString() + "："
                + String.join("；", parts) + (userRemark.isEmpty() ? "" : "；" + userRemark));
        supplementLogMapper.insert(log);

        java.util.Map<String, Object> r = new java.util.HashMap<>();
        r.put("count", count);
        r.put("total", total);
        r.put("paid", paid);
        r.put("arrears", total.subtract(paid).max(BigDecimal.ZERO));
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
