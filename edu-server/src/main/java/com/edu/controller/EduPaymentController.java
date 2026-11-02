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
import com.edu.mapper.EduPaymentLogMapper;
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
    private final EduPaymentLogMapper paymentLogMapper;
    private final com.edu.mapper.SysDictMapper dictMapper;
    private final EduEnrollMapper enrollMapper;
    private final com.edu.mapper.EduContractMapper contractMapper;
    private final com.edu.mapper.EduStudentMapper studentMapper;
    private final com.edu.service.DataScopeService dataScopeService;

    @GetMapping("/page")
    public Result<PageResult<EduPayment>> page(@RequestParam(defaultValue = "1") long page,
                                               @RequestParam(defaultValue = "10") long size,
                                               @RequestParam(required = false) Long studentId,
                                               @RequestParam(required = false) Long courseId,
                                               @RequestParam(required = false) String contractNo,
 @RequestParam(required = false) String sortBy,
 @RequestParam(required = false) String direction) {
        LambdaQueryWrapper<EduPayment> qw = new LambdaQueryWrapper<EduPayment>()
                .eq(studentId != null, EduPayment::getStudentId, studentId)
                .eq(courseId != null, EduPayment::getCourseId, courseId);
        // 合同号模糊筛选：先在合同管理匹配编号，再按命中合同收窄缴费记录
        if (contractNo != null && !contractNo.trim().isEmpty()) {
            java.util.List<Long> contractIds = new java.util.ArrayList<>();
            for (com.edu.entity.EduContract c : contractMapper.selectList(
                    new LambdaQueryWrapper<com.edu.entity.EduContract>()
                            .like(com.edu.entity.EduContract::getContractNo, contractNo.trim())))
                contractIds.add(c.getId());
            if (contractIds.isEmpty()) return Result.ok(PageResult.of(0, new java.util.ArrayList<>()));
            qw.in(EduPayment::getContractId, contractIds);
        }
        qw.last(SafeSort.order(sortBy, direction, "id", "id", "amount", "discountAmount", "payType", "payTime"));
        // 数据权限：校区角色仅见本校区学员的缴费，学管师仅见所负责学员
        dataScopeService.applyByStudent(qw, AuthContext.get(), "student_id");
        Page<EduPayment> p = paymentMapper.selectPage(new Page<>(page, size), qw);
        return Result.ok(PageResult.of(p.getTotal(), p.getRecords()));
    }

    /** 新增缴费：同步累计报名/合同的已缴金额 */
    @PostMapping
    @Log("新增缴费")
    @Transactional
    public Result<Void> save(@RequestBody EduPayment payment) {
        if (payment.getAmount() == null || payment.getAmount().compareTo(BigDecimal.ZERO) <= 0)
            return Result.error("实缴金额必须大于 0");
        String over = checkOverpay(payment.getContractId(), null, payment.getAmount());
        if (over != null) return Result.error(over);
        payment.setTenantId(AuthContext.get().getTenantId());
        payment.setPaymentNo("PAY" + LocalDateTime.now().format(NO_FMT)
                + String.format("%03d", (int) (Math.random() * 1000)));
        payment.setPayTime(LocalDateTime.now());
        // 经办人：表单未指定时默认当前登录人
        if (payment.getOperator() == null || payment.getOperator().trim().isEmpty())
            payment.setOperator(AuthContext.get().getRealName());
        payment.setUpdateBy(AuthContext.get().getRealName());
        // 归属校区跟随学员
        com.edu.entity.EduStudent stu = studentMapper.selectById(payment.getStudentId());
        payment.setOrgId(stu != null && stu.getOrgId() != null ? stu.getOrgId() : 1L);
        // 课程兜底：未传时取合同课程（course_id 非空列，防直连API缺参落库失败）
        if (payment.getCourseId() == null && payment.getContractId() != null) {
            com.edu.entity.EduContract ct = contractMapper.selectById(payment.getContractId());
            if (ct != null) payment.setCourseId(ct.getCourseId());
        }
        if (payment.getCourseId() == null) return Result.error("缺少课程信息，请重选关联合同");
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
        syncContractPaid(payment.getContractId());
        insertLog(1, payment, payment.getAmount(), null,
                "新增缴费 " + fmt(payment.getAmount()) + " 元（" + payLabel(payment.getPayType()) + "）");
        return Result.ok();
    }

    /**
     * 修改缴费：金额/优惠/支付方式/备注/经办人可改，学员与合同不可换；
     * 金额变化后按缴费流水重算合同已缴与缴费状态。
     */
    @PutMapping
    @Log("修改缴费")
    @Transactional
    public Result<Void> update(@RequestBody EduPayment payment) {
        EduPayment old = paymentMapper.selectById(payment.getId());
        if (old == null) return Result.error("缴费记录不存在");
        if (payment.getAmount() == null || payment.getAmount().compareTo(BigDecimal.ZERO) <= 0)
            return Result.error("实缴金额必须大于 0");
        String over = checkOverpay(old.getContractId(), payment.getId(), payment.getAmount());
        if (over != null) return Result.error(over);
        BigDecimal oldAmount = old.getAmount() == null ? BigDecimal.ZERO : old.getAmount();
        // 留痕快照：覆盖前留存旧值（变更明细对比用）
        Integer oldPayType = old.getPayType();
        String oldOperator = old.getOperator();
        String oldRemark = old.getRemark();
        String oldVoucher = old.getVoucherUrl();
        old.setAmount(payment.getAmount());
        old.setDiscountAmount(payment.getDiscountAmount());
        old.setPayType(payment.getPayType());
        old.setRemark(payment.getRemark());
        old.setOperator(payment.getOperator());
        old.setVoucherUrl(payment.getVoucherUrl());
        old.setUpdateBy(AuthContext.get().getRealName());
        paymentMapper.updateById(old);
        // 报名已缴随金额差调整（走报名口径的历史数据）
        if (old.getEnrollId() != null) {
            EduEnroll enroll = enrollMapper.selectById(old.getEnrollId());
            if (enroll != null) {
                BigDecimal diff = (payment.getAmount() == null ? BigDecimal.ZERO : payment.getAmount())
                        .subtract(oldAmount);
                if (diff.compareTo(BigDecimal.ZERO) != 0) {
                    BigDecimal paid = enroll.getPaidAmount() == null
                            ? BigDecimal.ZERO : enroll.getPaidAmount();
                    enroll.setPaidAmount(paid.add(diff));
                    enrollMapper.updateById(enroll);
                }
            }
        }
        syncContractPaid(old.getContractId());
        insertLog(2, old, old.getAmount(), oldAmount,
                diffDetail(payment, oldAmount, oldPayType, oldOperator, oldRemark, oldVoucher));
        return Result.ok();
    }

    /**
     * 删除缴费：删除流水并留痕（操作记录TAB可查），
     * 合同已缴/状态按剩余流水重算，走报名口径的已缴同步扣减。
     */
    @DeleteMapping("/{id}")
    @Log("删除缴费")
    @Transactional
    public Result<Void> delete(@PathVariable Long id) {
        EduPayment old = paymentMapper.selectById(id);
        if (old == null) return Result.error("缴费记录不存在");
        BigDecimal amt = old.getAmount() == null ? BigDecimal.ZERO : old.getAmount();
        paymentMapper.deleteById(id);
        if (old.getEnrollId() != null) {
            EduEnroll enroll = enrollMapper.selectById(old.getEnrollId());
            if (enroll != null && enroll.getPaidAmount() != null) {
                enroll.setPaidAmount(enroll.getPaidAmount().subtract(amt).max(BigDecimal.ZERO));
                enrollMapper.updateById(enroll);
            }
        }
        syncContractPaid(old.getContractId());
        insertLog(3, old, null, amt,
                "删除缴费 " + fmt(amt) + " 元（" + payLabel(old.getPayType()) + "）");
        return Result.ok();
    }

    /**
     * 合同缴费汇总（新增/修改缴费表单展示与校验）：
     * 已缴金额=该合同全部缴费记录之和（修改时可排除本条，避免自含）；
     * 待缴纳金额=合同金额-已缴汇总。
     */
    @GetMapping("/contract-summary")
    public Result<java.util.Map<String, Object>> contractSummary(@RequestParam Long contractId,
                                                                 @RequestParam(required = false) Long excludePaymentId) {
        BigDecimal paid = BigDecimal.ZERO;
        for (EduPayment p : paymentMapper.selectList(new LambdaQueryWrapper<EduPayment>()
                .eq(EduPayment::getContractId, contractId)
                .ne(excludePaymentId != null, EduPayment::getId, excludePaymentId)))
            paid = paid.add(p.getAmount() == null ? BigDecimal.ZERO : p.getAmount());
        com.edu.entity.EduContract c = contractMapper.selectById(contractId);
        BigDecimal total = c == null || c.getTotalAmount() == null ? BigDecimal.ZERO : c.getTotalAmount();
        java.util.Map<String, Object> m = new java.util.HashMap<>();
        m.put("totalAmount", total);
        m.put("paidAmount", paid);
        m.put("remainAmount", total.subtract(paid).max(BigDecimal.ZERO));
        return Result.ok(m);
    }

    /**
     * 超缴校验（新增/修改保存前服务端强制执行，前端校验可被绕过）：
     * 实缴金额不能超过合同待缴纳金额（合同总额-其他缴费之和，修改时排除本条）；
     * 返回 null 表示通过，否则为拒绝保存的原因。
     */
    private String checkOverpay(Long contractId, Long excludePaymentId, BigDecimal amount) {
        if (contractId == null) return null;
        com.edu.entity.EduContract c = contractMapper.selectById(contractId);
        if (c == null) return null;
        BigDecimal total = c.getTotalAmount() == null ? BigDecimal.ZERO : c.getTotalAmount();
        BigDecimal paid = BigDecimal.ZERO;
        for (EduPayment p : paymentMapper.selectList(new LambdaQueryWrapper<EduPayment>()
                .eq(EduPayment::getContractId, contractId)
                .ne(excludePaymentId != null, EduPayment::getId, excludePaymentId)))
            paid = paid.add(p.getAmount() == null ? BigDecimal.ZERO : p.getAmount());
        BigDecimal remain = total.subtract(paid).max(BigDecimal.ZERO);
        if (amount.compareTo(remain) > 0)
            return "实缴金额 " + amount.stripTrailingZeros().toPlainString() + " 元超过待缴纳金额 "
                    + remain.stripTrailingZeros().toPlainString() + " 元（合同总额 "
                    + total.stripTrailingZeros().toPlainString() + "，已缴 "
                    + paid.stripTrailingZeros().toPlainString() + "），无法保存";
        return null;
    }

    /**
     * 操作记录（缴费管理"操作记录"TAB，只增不改，仅查询）：
     * 新增/修改留痕全量落 edu_payment_log，支持类型/学生/单号/操作人/时间段筛选。
     */
    @GetMapping("/logs")
    public Result<PageResult<com.edu.entity.EduPaymentLog>> logs(@RequestParam(defaultValue = "1") long page,
                                                                 @RequestParam(defaultValue = "10") long size,
                                                                 @RequestParam(required = false) Integer action,
                                                                 @RequestParam(required = false) Long studentId,
                                                                 @RequestParam(required = false) String paymentNo,
                                                                 @RequestParam(required = false) String operator,
                                                                 @RequestParam(required = false) String begin,
                                                                 @RequestParam(required = false) String end,
                                                                 @RequestParam(required = false) String sortBy,
                                                                 @RequestParam(required = false) String direction) {
        // like 的取值参数是急切求值，先归一为安全值再传（null 直接 like 会 NPE）
        String no = paymentNo == null ? "" : paymentNo.trim();
        String op = operator == null ? "" : operator.trim();
        LambdaQueryWrapper<com.edu.entity.EduPaymentLog> qw =
                new LambdaQueryWrapper<com.edu.entity.EduPaymentLog>()
                        .eq(action != null, com.edu.entity.EduPaymentLog::getAction, action)
                        .eq(studentId != null, com.edu.entity.EduPaymentLog::getStudentId, studentId)
                        .like(!no.isEmpty(), com.edu.entity.EduPaymentLog::getPaymentNo, no)
                        .like(!op.isEmpty(), com.edu.entity.EduPaymentLog::getOperator, op)
                        .ge(begin != null && !begin.isEmpty(), com.edu.entity.EduPaymentLog::getCreateTime,
                                begin + " 00:00:00")
                        .le(end != null && !end.isEmpty(), com.edu.entity.EduPaymentLog::getCreateTime,
                                end + " 23:59:59");
        qw.last(SafeSort.order(sortBy, direction, "id", "id", "amount", "createTime"));
        // 数据权限：与缴费列表同口径，校区角色仅见本校区学员
        dataScopeService.applyByStudent(qw, AuthContext.get(), "student_id");
        Page<com.edu.entity.EduPaymentLog> p = paymentLogMapper.selectPage(new Page<>(page, size), qw);
        return Result.ok(PageResult.of(p.getTotal(), p.getRecords()));
    }

    /** 写操作留痕（与缴费同事务：主表成功，留痕才落库） */
    private void insertLog(int action, EduPayment p, BigDecimal amount, BigDecimal amountBefore, String detail) {
        com.edu.entity.EduPaymentLog l = new com.edu.entity.EduPaymentLog();
        l.setTenantId(p.getTenantId());
        l.setPaymentId(p.getId());
        l.setPaymentNo(p.getPaymentNo());
        l.setAction(action);
        l.setStudentId(p.getStudentId());
        l.setAmount(amount);
        l.setAmountBefore(amountBefore);
        l.setDetail(detail != null && detail.length() > 500 ? detail.substring(0, 500) : detail);
        l.setOperator(AuthContext.get().getRealName());
        l.setOrgId(p.getOrgId());
        l.setCreateTime(LocalDateTime.now());
        paymentLogMapper.insert(l);
    }

    /** 修改留痕明细：仅记录发生变化的字段（金额/支付方式/经办人/备注/凭证） */
    private String diffDetail(EduPayment nu, BigDecimal oldAmount, Integer oldPayType,
                              String oldOperator, String oldRemark, String oldVoucher) {
        StringBuilder sb = new StringBuilder();
        if (nu.getAmount().compareTo(oldAmount) != 0)
            sb.append("金额 ").append(fmt(oldAmount)).append("→").append(fmt(nu.getAmount()));
        if (nu.getPayType() != null && !nu.getPayType().equals(oldPayType))
            sb.append(join(sb)).append("支付方式 ").append(payLabel(oldPayType))
                    .append("→").append(payLabel(nu.getPayType()));
        if (nu.getOperator() != null && !nu.getOperator().trim().isEmpty()
                && !nu.getOperator().equals(oldOperator))
            sb.append(join(sb)).append("经办人 ").append(oldOperator == null || oldOperator.isEmpty()
                    ? "空" : oldOperator).append("→").append(nu.getOperator());
        String oldR = oldRemark == null ? "" : oldRemark;
        String newR = nu.getRemark() == null ? "" : nu.getRemark();
        if (!newR.equals(oldR))
            sb.append(join(sb)).append("备注 ").append(oldR.isEmpty() ? "空" : clip(oldR))
                    .append("→").append(clip(newR));
        String oldV = oldVoucher == null ? "" : oldVoucher;
        String newV = nu.getVoucherUrl() == null ? "" : nu.getVoucherUrl();
        if (!newV.equals(oldV))
            sb.append(join(sb)).append("收款凭证 ").append(oldV.isEmpty() ? "无" : "已上传")
                    .append("→").append(newV.isEmpty() ? "移除" : "已更换");
        return sb.length() == 0 ? "无字段变更" : sb.toString();
    }

    private String join(StringBuilder sb) { return sb.length() > 0 ? "；" : ""; }

    private String clip(String s) { return s.length() > 30 ? s.substring(0, 30) + "…" : s; }

    private String fmt(BigDecimal v) {
        return v == null ? "0" : v.stripTrailingZeros().toPlainString();
    }

    /** 支付方式名称：系统字典 pay_channel，缺省回退数值 */
    private String payLabel(Integer payType) {
        if (payType == null) return "未设置";
        for (com.edu.entity.SysDict d : dictMapper.selectList(
                new LambdaQueryWrapper<com.edu.entity.SysDict>()
                        .eq(com.edu.entity.SysDict::getDictType, "pay_channel")
                        .eq(com.edu.entity.SysDict::getValue, String.valueOf(payType))))
            return d.getLabel();
        return String.valueOf(payType);
    }

    /**
     * 按缴费流水重算合同已缴金额与缴费状态（幂等，历史数据自愈）：
     * 已缴=该合同全部缴费之和；状态 0待缴费/1部分缴费/2已结清，退费/到期状态不覆盖。
     */
    private void syncContractPaid(Long contractId) {
        if (contractId == null) return;
        com.edu.entity.EduContract c = contractMapper.selectById(contractId);
        if (c == null) return;
        BigDecimal paid = BigDecimal.ZERO;
        for (EduPayment p : paymentMapper.selectList(new LambdaQueryWrapper<EduPayment>()
                .eq(EduPayment::getContractId, contractId)))
            paid = paid.add(p.getAmount() == null ? BigDecimal.ZERO : p.getAmount());
        c.setPaidAmount(paid);
        if (c.getStatus() == null || c.getStatus() <= 2) {
            BigDecimal total = c.getTotalAmount() == null ? BigDecimal.ZERO : c.getTotalAmount();
            c.setStatus(paid.compareTo(BigDecimal.ZERO) <= 0 ? 0
                    : paid.compareTo(total) >= 0 ? 2 : 1);
        }
        contractMapper.updateById(c);
    }
}
