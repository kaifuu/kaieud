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
        private Long clazzId;
        private Long teacherId;
        /** 按课程定价行订阅（edu_course_price，按课时模式） */
        private Long priceId;
        /** 份数（数量），默认 1 */
        private Integer qty;
        /** 优惠金额（合计） */
        private java.math.BigDecimal discountAmount;
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
    }

    /** 订阅班课/课程（套餐优先） */
    @PostMapping("/subscribe")
    @Log("订阅班课")
    public Result<EduEnroll> subscribe(@RequestBody SubscribeDTO dto) {
        return Result.ok(enrollService.subscribe(dto));
    }

    /** 修改报名单：实收/备注/经办信息/有效期/状态 */
    @Data
    public static class UpdateDTO {
        private Long id;
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
    }

    @PutMapping
    @Log("修改报名单")
    public Result<Void> update(@RequestBody UpdateDTO dto) {
        if (dto.getId() == null) return Result.error("缺少订单ID");
        EduEnroll e = enrollMapper.selectById(dto.getId());
        if (e == null) return Result.error("报名单不存在");
        if (dto.getPaidAmount() != null) e.setPaidAmount(dto.getPaidAmount());
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
        return Result.ok();
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
