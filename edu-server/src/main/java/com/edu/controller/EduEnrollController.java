package com.edu.controller;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.edu.common.SafeSort;
import com.edu.common.Log;
import com.edu.common.PageResult;
import com.edu.common.Result;
import com.edu.entity.EduEnroll;
import com.edu.mapper.EduEnrollMapper;
import com.edu.security.RequiresPerm;
import com.edu.service.EduEnrollService;
import lombok.Data;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

/** 报名管理：订阅/退订班课、指派老师、补课时 */
@RestController
@RequestMapping("/edu/enroll")
@RequiredArgsConstructor
@RequiresPerm("edu:enroll")
public class EduEnrollController {

    private final EduEnrollMapper enrollMapper;
    private final EduEnrollService enrollService;
    private final com.edu.mapper.EduPackageMapper packageMapper;
    private final com.edu.mapper.EduAccountLogMapper accountMapper;
    private final com.edu.mapper.EduEnrollLogMapper enrollLogMapper;
    private final com.edu.mapper.EduCoursePriceMapper coursePriceMapper;
    private final com.edu.mapper.EduCourseCampusMapper courseCampusMapper;

    @GetMapping("/page")
    public Result<PageResult<EduEnroll>> page(@RequestParam(defaultValue = "1") long page,
                                              @RequestParam(defaultValue = "10") long size,
                                              @RequestParam(required = false) Long studentId,
                                              @RequestParam(required = false) Long teacherId,
                                              @RequestParam(required = false) Integer status,
 @RequestParam(required = false) String sortBy,
 @RequestParam(required = false) String direction) {
        Page<EduEnroll> p = enrollMapper.selectPage(new Page<>(page, size),
                new LambdaQueryWrapper<EduEnroll>()
                        .eq(studentId != null, EduEnroll::getStudentId, studentId)
                        .eq(teacherId != null, EduEnroll::getTeacherId, teacherId)
                        .eq(status != null, EduEnroll::getStatus, status)
                        .last(SafeSort.order(sortBy, direction, "id", "id", "price", "discount", "totalAmount", "usedHours", "status", "createTime")));
        // 填充套餐名称（按课程订阅/套餐已删除时为空）
        java.util.List<Long> pkgIds = p.getRecords().stream()
                .map(EduEnroll::getPackageId).filter(java.util.Objects::nonNull)
                .collect(java.util.stream.Collectors.toList());
        if (!pkgIds.isEmpty()) {
            java.util.Map<Long, String> names = packageMapper.selectBatchIds(pkgIds).stream()
                    .collect(java.util.stream.Collectors.toMap(
                            com.edu.entity.EduPackage::getId, com.edu.entity.EduPackage::getName, (a, b) -> a));
            p.getRecords().forEach(e -> e.setPackageName(names.get(e.getPackageId())));
        }
        return Result.ok(PageResult.of(p.getTotal(), p.getRecords()));
    }

    @Data
    public static class SubscribeDTO {
        private Long studentId;
        /** 报名/续报按套餐订阅：课程/课时/单价随套餐带出 */
        private Long packageId;
        /** 未选套餐时的入口：按课程默认售价 */
        private Long courseId;
        /** 报名校区（edu_course_campus 校区名，一单一校区；未传时取课程首个关联校区） */
        private String campus;
        private Long clazzId;
        private Long teacherId;
        /** 按课程定价行订阅（edu_course_price，按课时模式） */
        private Long priceId;
        /** 份数（数量），默认 1 */
        private Integer qty;
        /** 优惠金额（合计） */
        private java.math.BigDecimal discountAmount;
        /** 优惠明细JSON [{type,amount}]（编辑回显用，不合并） */
        private String discountDetail;
        /** 本次实收（支付信息分摊到该课程） */
        private java.math.BigDecimal paidAmount;
        /** 有效期（yyyy-MM-dd，勾选后传） */
        private String validDate;
        /** 备注（对内） */
        private String remark;
        /** 销售来源 */
        private String source;
        /** 经办人（默认当前登录人） */
        private String handler;
        /** 经办日期（yyyy-MM-dd，默认今天） */
        private String handleDate;
        /** 采单员 */
        private String orderTaker;
        /** 咨询顾问 */
        private String consultant;
        /** 支付信息（日志用） */
        private String payInfo;
        /** 支付明细JSON [{channel,account,serial,amount}] */
        private String payDetail;
    }

    /** 订阅班课/课程（套餐优先） */
    @PostMapping("/subscribe")
    @Log("订阅班课")
    public Result<EduEnroll> subscribe(@RequestBody SubscribeDTO dto) {
        return Result.ok(enrollService.subscribe(dto));
    }

    /** 转课：在读报名单转另一门课程（原单已付抵扣，多退少补） */
    @Data
    public static class TransferDTO {
        private Long studentId;
        /** 转出报名单ID（须为该学员在读记录） */
        private Long fromEnrollId;
        /** 转入课程 */
        private Long courseId;
        /** 报名校区（edu_course_campus 校区名） */
        private String campus;
        private Long clazzId;
        private Long teacherId;
        /** 定价行ID（edu_course_price，按课时模式） */
        private Long priceId;
        /** 份数，默认 1 */
        private Integer qty;
        /** 优惠金额 */
        private java.math.BigDecimal discountAmount;
        /** 渠道实收合计（补差部分，多账户明细见 payDetail） */
        private java.math.BigDecimal paidAmount;
        /** 有效期（yyyy-MM-dd，勾选后传） */
        private String validDate;
        private String remark;
        private String source;
        private String handler;
        private String handleDate;
        private String orderTaker;
        private String consultant;
        /** 支付信息（日志用） */
        private String payInfo;
        /** 支付明细JSON [{channel,account,serial,amount}] */
        private String payDetail;
    }

    /** 转课办理：原单置为已转课并创建转入报名单，差额多退少补 */
    @PostMapping("/transfer")
    @Log("转课")
    public Result<?> transfer(@RequestBody TransferDTO dto) {
        return Result.ok(enrollService.transfer(dto));
    }

    /** 修改报名单：全字段（定价/数量/优惠/老师/班级/支付/经办） */
    @Data
    public static class UpdateDTO {
        private Long id;
        private Long courseId;
        /** 报名校区（一单一校区，edu_course_campus 校区名） */
        private String campus;
        private Long clazzId;
        private Long teacherId;
        /** 定价行ID（edu_course_price） */
        private Long priceId;
        /** 份数 */
        private Integer qty;
        /** 优惠金额 */
        private java.math.BigDecimal discountAmount;
        /** 优惠明细JSON [{type,amount}]（编辑回显用，不合并） */
        private String discountDetail;
        private java.math.BigDecimal paidAmount;
        private String remark;
        private String source;
        private String orderTaker;
        private String consultant;
        private String validDate;
        private String handleDate;
        private String handler;
        /** 1在读 2已结课 3已退订（退订请走取消订阅） */
        private Integer status;
        /** 支付信息（日志用） */
        private String payInfo;
        /** 支付明细JSON */
        private String payDetail;
    }

    @PutMapping
    @Log("修改报名单")
    public Result<Void> update(@RequestBody UpdateDTO dto) {
        if (dto.getId() == null) return Result.error("缺少订单ID");
        EduEnroll e = enrollMapper.selectById(dto.getId());
        if (e == null) return Result.error("报名单不存在");

        // 课程/班级/老师
        if (dto.getCourseId() != null) e.setCourseId(dto.getCourseId());
        // 报名校区：显式提交时校验属于课程的开课校区
        if (dto.getCampus() != null && !dto.getCampus().isEmpty() && e.getCourseId() != null) {
            Long cnt = courseCampusMapper.selectCount(new LambdaQueryWrapper<com.edu.entity.EduCourseCampus>()
                    .eq(com.edu.entity.EduCourseCampus::getCourseId, e.getCourseId())
                    .eq(com.edu.entity.EduCourseCampus::getCampus, dto.getCampus()));
            if (cnt == 0) return Result.error("校区「" + dto.getCampus() + "」未开设该课程");
            e.setCampus(dto.getCampus());
        }
        if (dto.getClazzId() != null) e.setClazzId(dto.getClazzId());
        if (dto.getTeacherId() != null) e.setTeacherId(dto.getTeacherId());

        // 定价重算：定价行×数量得到原价（未减优惠）；未重算时以记录原价（现应收+已优惠）为基准
        java.math.BigDecimal original = null;
        if (dto.getPriceId() != null) {
            com.edu.entity.EduCoursePrice pr = coursePriceMapper.selectById(dto.getPriceId());
            if (pr != null && pr.getChargeMode() == 1) {
                int qty = dto.getQty() == null || dto.getQty() < 1 ? 1 : dto.getQty();
                e.setTotalHours((int) Math.round(pr.getHours().doubleValue() * qty));
                e.setPrice(pr.getTotalAmount().divide(pr.getHours(), 2, java.math.BigDecimal.ROUND_HALF_UP));
                original = pr.getTotalAmount().multiply(java.math.BigDecimal.valueOf(qty));
            }
        } else if (dto.getQty() != null && dto.getQty() >= 1 && e.getPrice() != null
                && e.getTotalHours() != null) {
            // 无定价行时按数量重算
            e.setTotalHours(e.getTotalHours() * dto.getQty());
            original = e.getPrice().multiply(java.math.BigDecimal.valueOf(e.getTotalHours()));
        }
        if (original == null)
            original = (e.getTotalAmount() == null ? java.math.BigDecimal.ZERO : e.getTotalAmount())
                    .add(e.getDiscountAmount() == null ? java.math.BigDecimal.ZERO : e.getDiscountAmount());
        // 优惠：显式传值时按新优惠重算（0=清除优惠），未传时保持记录原优惠
        java.math.BigDecimal discount = dto.getDiscountAmount() != null
                ? dto.getDiscountAmount().max(java.math.BigDecimal.ZERO)
                : (e.getDiscountAmount() == null ? java.math.BigDecimal.ZERO : e.getDiscountAmount());
        java.math.BigDecimal after = original.subtract(discount);
        if (after.compareTo(java.math.BigDecimal.ZERO) < 0) after = java.math.BigDecimal.ZERO;
        e.setTotalAmount(after);
        e.setDiscountAmount(discount);

        if (dto.getPaidAmount() != null) e.setPaidAmount(dto.getPaidAmount());
        // 优惠明细留档：显式传值时覆盖（空数组=清除），未传时保持原样
        if (dto.getDiscountDetail() != null) e.setDiscountDetail(dto.getDiscountDetail());
        if (dto.getPayDetail() != null) e.setPayDetail(dto.getPayDetail());
        if (dto.getRemark() != null) e.setRemark(dto.getRemark());
        if (dto.getSource() != null) e.setSource(dto.getSource());
        if (dto.getOrderTaker() != null) e.setOrderTaker(dto.getOrderTaker());
        if (dto.getConsultant() != null) e.setConsultant(dto.getConsultant());
        if (dto.getValidDate() != null && !dto.getValidDate().isEmpty()) {
            try { e.setValidDate(java.time.LocalDate.parse(dto.getValidDate())); } catch (Exception ignore) { }
        }
        if (dto.getHandleDate() != null && !dto.getHandleDate().isEmpty()) {
            try { e.setHandleDate(java.time.LocalDate.parse(dto.getHandleDate())); } catch (Exception ignore) { }
        }
        if (dto.getHandler() != null && !dto.getHandler().isEmpty()) e.setHandler(dto.getHandler());
        if (dto.getStatus() != null && dto.getStatus() != 3) e.setStatus(dto.getStatus());
        enrollMapper.updateById(e);
        enrollService.writeLogPublic(e, "UPDATE", dto.getPayInfo());
        return Result.ok();
    }

    /** 报名单操作日志列表 */
    @GetMapping("/{id}/logs")
    public Result<?> logs(@PathVariable Long id) {
        return Result.ok(enrollLogMapper.selectList(
                new LambdaQueryWrapper<com.edu.entity.EduEnrollLog>()
                        .eq(com.edu.entity.EduEnrollLog::getEnrollId, id)
                        .orderByDesc(com.edu.entity.EduEnrollLog::getId)));
    }

    @Data
    public static class CreditDTO {
        private Long studentId;
        private java.math.BigDecimal amount;
        private String remark;
    }

    /** 报名实收超额自动充入学员账户（充值流水） */
    @PostMapping("/credit")
    @Log("报名超额充入账户")
    public Result<Void> credit(@RequestBody CreditDTO dto) {
        if (dto.getStudentId() == null || dto.getAmount() == null
                || dto.getAmount().compareTo(java.math.BigDecimal.ZERO) <= 0)
            return Result.error("学员与金额为必填项");
        com.edu.entity.EduAccountLog log = new com.edu.entity.EduAccountLog();
        log.setTenantId(com.edu.security.AuthContext.get().getTenantId());
        log.setStudentId(dto.getStudentId());
        log.setType(1);
        log.setAmount(dto.getAmount());
        log.setRemark(dto.getRemark() == null || dto.getRemark().isEmpty()
                ? "报名实收超额自动充入" : dto.getRemark());
        log.setOperator(com.edu.security.AuthContext.get().getRealName());
        accountMapper.insert(log);
        return Result.ok();
    }

    /** 删除报名单（编辑页移除课程）：取消未来排课并删除记录与日志 */
    @DeleteMapping("/{id}")
    @Log("删除报名单")
    public Result<Void> delete(@PathVariable Long id) {
        enrollService.deleteEnroll(id);
        return Result.ok();
    }

    /** 取消订阅 */
    @PostMapping("/{id}/unsubscribe")
    @Log("取消订阅班课")
    public Result<Void> unsubscribe(@PathVariable Long id) {
        enrollService.unsubscribe(id);
        return Result.ok();
    }

    /** 为学生指派/更换上课老师 */
    @Data
    public static class AssignDTO {
        private Long teacherId;
    }

    @PostMapping("/{id}/assign-teacher")
    @Log("指派授课老师")
    public Result<Void> assignTeacher(@PathVariable Long id, @RequestBody AssignDTO dto) {
        enrollService.assignTeacher(id, dto.getTeacherId());
        return Result.ok();
    }

    /** 补充因请假取消的课时次数 */
    @Data
    public static class CompensateHoursDTO {
        private Integer hours;
    }

    @PostMapping("/{id}/compensate-hours")
    @Log("补充课时次数")
    public Result<Void> compensateHours(@PathVariable Long id, @RequestBody CompensateHoursDTO dto) {
        enrollService.compensateHours(id, dto.getHours() == null ? 1 : dto.getHours());
        return Result.ok();
    }
}
