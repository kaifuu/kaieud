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

    /**
     * 学生订阅班课（报名/续报、选课中心）：优先按套餐订阅，课程/课时/单价随套餐带出；
     * 按课程订阅时可选定价行（edu_course_price）与份数，未选则回退课程默认/首条定价。
     */
    @Transactional
    public EduEnroll subscribe(com.edu.controller.EduEnrollController.SubscribeDTO dto) {
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

        // 防止重复订阅（在读状态不可重复，退订/结课后可续报）：关联课程按课程判重，无课程套餐按套餐判重
        Long dup = enrollMapper.selectCount(new LambdaQueryWrapper<EduEnroll>()
                .eq(EduEnroll::getStudentId, studentId)
                .eq(courseId != null, EduEnroll::getCourseId, courseId)
                .eq(courseId == null, EduEnroll::getPackageId, packageId)
                .eq(EduEnroll::getStatus, 1));
        if (dup > 0) throw new BizException("该学生已订阅此课程，请勿重复订阅");

        EduEnroll enroll = new EduEnroll();
        enroll.setTenantId(AuthContext.get().getTenantId());
        enroll.setStudentId(studentId);
        enroll.setCourseId(courseId);
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
        enroll.setStatus(1);
        // 优惠：小计减优惠（下限0），金额留档
        if (dto.getDiscountAmount() != null && dto.getDiscountAmount().compareTo(BigDecimal.ZERO) > 0) {
            BigDecimal before = enroll.getTotalAmount() == null ? BigDecimal.ZERO : enroll.getTotalAmount();
            BigDecimal after = before.subtract(dto.getDiscountAmount());
            if (after.compareTo(BigDecimal.ZERO) < 0) after = BigDecimal.ZERO;
            enroll.setTotalAmount(after);
            enroll.setDiscountAmount(dto.getDiscountAmount());
        }
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
