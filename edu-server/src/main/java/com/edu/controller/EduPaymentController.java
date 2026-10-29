package com.edu.controller;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.edu.common.SafeSort;
import com.edu.common.Log;
import com.edu.common.PageResult;
import com.edu.common.Result;
import com.edu.entity.EduEnroll;
import com.edu.entity.EduPayment;
import com.edu.mapper.EduEnrollMapper;
import com.edu.mapper.EduPaymentMapper;
import com.edu.security.AuthContext;
import com.edu.security.RequiresPerm;
import lombok.RequiredArgsConstructor;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.*;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;

/** 缴费管理 */
@RestController
@RequestMapping("/edu/payment")
@RequiredArgsConstructor
@RequiresPerm("edu:payment")
public class EduPaymentController {

    private static final DateTimeFormatter NO_FMT = DateTimeFormatter.ofPattern("yyyyMMddHHmmss");

    private final EduPaymentMapper paymentMapper;
    private final EduEnrollMapper enrollMapper;
    private final com.edu.mapper.EduStudentMapper studentMapper;
    private final com.edu.service.DataScopeService dataScopeService;

    @GetMapping("/page")
    public Result<PageResult<EduPayment>> page(@RequestParam(defaultValue = "1") long page,
                                               @RequestParam(defaultValue = "10") long size,
                                               @RequestParam(required = false) Long studentId,
                                               @RequestParam(required = false) Long courseId,
 @RequestParam(required = false) String sortBy,
 @RequestParam(required = false) String direction) {
        LambdaQueryWrapper<EduPayment> qw = new LambdaQueryWrapper<EduPayment>()
                .eq(studentId != null, EduPayment::getStudentId, studentId)
                .eq(courseId != null, EduPayment::getCourseId, courseId)
                .last(SafeSort.order(sortBy, direction, "id", "id", "amount", "discountAmount", "payType", "payTime"));
        // 数据权限：校区角色仅见本校区学员的缴费，学管师仅见所负责学员
        dataScopeService.applyByStudent(qw, AuthContext.get(), "student_id");
        Page<EduPayment> p = paymentMapper.selectPage(new Page<>(page, size), qw);
        return Result.ok(PageResult.of(p.getTotal(), p.getRecords()));
    }

    /** 新增缴费：同步累计报名合同的已缴金额 */
    @PostMapping
    @Log("新增缴费")
    @Transactional
    public Result<Void> save(@RequestBody EduPayment payment) {
        payment.setTenantId(AuthContext.get().getTenantId());
        payment.setPaymentNo("PAY" + LocalDateTime.now().format(NO_FMT)
                + String.format("%03d", (int) (Math.random() * 1000)));
        payment.setPayTime(LocalDateTime.now());
        payment.setOperator(AuthContext.get().getRealName());
        // 归属校区跟随学员
        com.edu.entity.EduStudent stu = studentMapper.selectById(payment.getStudentId());
        payment.setOrgId(stu != null && stu.getOrgId() != null ? stu.getOrgId() : 1L);
        paymentMapper.insert(payment);

        if (payment.getEnrollId() != null) {
            EduEnroll enroll = enrollMapper.selectById(payment.getEnrollId());
            if (enroll != null) {
                BigDecimal paid = enroll.getPaidAmount() == null
                        ? BigDecimal.ZERO : enroll.getPaidAmount();
                enroll.setPaidAmount(paid.add(payment.getAmount()));
                enrollMapper.updateById(enroll);
            }
        }
        return Result.ok();
    }
}
