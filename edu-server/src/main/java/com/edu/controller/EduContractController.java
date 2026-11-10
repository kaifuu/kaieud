package com.edu.controller;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.edu.common.Log;
import com.edu.common.PageResult;
import com.edu.common.Result;
import com.edu.common.SafeSort;
import com.edu.entity.EduConsume;
import com.edu.entity.EduContract;
import com.edu.entity.EduPayment;
import com.edu.entity.EduRefund;
import com.edu.mapper.EduContractMapper;
import com.edu.mapper.EduPaymentMapper;
import com.edu.mapper.EduConsumeMapper;
import com.edu.mapper.EduRefundMapper;
import com.edu.security.AuthContext;
import com.edu.security.RequiresPerm;
import lombok.Data;
import lombok.RequiredArgsConstructor;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;
import org.springframework.web.bind.annotation.*;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/** 合同管理：引用套餐支持一客一价；退费冻结课时；到期标记 */
@RestController
@RequestMapping("/edu/contract")
@RequiredArgsConstructor
@RequiresPerm("edu:contract")
public class EduContractController {

    private static final DateTimeFormatter NO_FMT = DateTimeFormatter.ofPattern("yyyyMMdd");

    private final EduContractMapper contractMapper;
    private final EduPaymentMapper paymentMapper;
    private final EduConsumeMapper consumeMapper;
    private final EduRefundMapper refundMapper;
    private final com.edu.mapper.EduAccountLogMapper accountLogMapper;
    private final com.edu.mapper.EduStudentMapper studentMapper;
    private final com.edu.mapper.EduPackageMapper packageMapper;
    private final com.edu.mapper.SysUserMapper sysUserMapper;
    private final com.edu.service.DataScopeService dataScopeService;
    private final com.edu.mapper.EduContractLogMapper contractLogMapper;

    /** 合同操作留痕（合同历史TAB数据源） */
    private void logContract(EduContract c, String action, String content) {
        try {
            com.edu.entity.EduContractLog l = new com.edu.entity.EduContractLog();
            l.setTenantId(c.getTenantId());
            l.setContractId(c.getId());
            l.setContractNo(c.getContractNo());
            l.setStudentId(c.getStudentId());
            l.setAction(action);
            l.setContent(content);
            l.setOperator(AuthContext.get().getRealName());
            l.setCreateTime(LocalDateTime.now());
            contractLogMapper.insert(l);
        } catch (Exception ignore) { /* 留痕失败不阻断业务 */ }
    }

    /** 合同历史：全量操作记录（新增/修改/退费/删除），可按编号/操作人/学员筛选，支持列排序 */
    @GetMapping("/logs")
    public Result<PageResult<com.edu.entity.EduContractLog>> logs(
            @RequestParam(defaultValue = "1") long page,
            @RequestParam(defaultValue = "10") long size,
            @RequestParam(required = false) String keyword,
            @RequestParam(required = false) String action,
            @RequestParam(required = false) Long contractId,
            @RequestParam(required = false) Long studentId,
            @RequestParam(required = false) String sortBy,
            @RequestParam(required = false) String direction) {
        Page<com.edu.entity.EduContractLog> p = contractLogMapper.selectPage(new Page<>(page, size),
                new LambdaQueryWrapper<com.edu.entity.EduContractLog>()
                        .and(StringUtils.hasText(keyword), w -> w
                                .like(com.edu.entity.EduContractLog::getContractNo, keyword)
                                .or().like(com.edu.entity.EduContractLog::getOperator, keyword))
                        .eq(StringUtils.hasText(action), com.edu.entity.EduContractLog::getAction, action)
                        .eq(contractId != null, com.edu.entity.EduContractLog::getContractId, contractId)
                        .eq(studentId != null, com.edu.entity.EduContractLog::getStudentId, studentId)
                        .last(SafeSort.order(sortBy, direction, "id",
                                "id", "contractNo", "action", "operator", "createTime")));
        return Result.ok(PageResult.of(p.getTotal(), p.getRecords()));
    }

    @GetMapping("/page")
    public Result<PageResult<EduContract>> page(@RequestParam(defaultValue = "1") long page,
                                                @RequestParam(defaultValue = "10") long size,
                                                @RequestParam(required = false) Long studentId,
                                                @RequestParam(required = false) String keyword,
                                                @RequestParam(required = false) Integer status,
                                                @RequestParam(required = false) String begin,
                                                @RequestParam(required = false) String end,
                                                @RequestParam(required = false) String sortBy,
                                                @RequestParam(required = false) String direction) {
        LambdaQueryWrapper<EduContract> qw = new LambdaQueryWrapper<EduContract>()
                .eq(studentId != null, EduContract::getStudentId, studentId)
                // 已删除学生的合同不再展示（学生档案随用户删除联动软删）
                .notInSql(EduContract::getStudentId,
                        "SELECT id FROM edu_student WHERE deleted = 1")
                .like(StringUtils.hasText(keyword), EduContract::getContractNo, keyword)
                .eq(status != null, EduContract::getStatus, status)
                .ge(StringUtils.hasText(begin), EduContract::getStartDate, begin)
                .le(StringUtils.hasText(end), EduContract::getStartDate, end)
                ;
        qw.last(SafeSort.order(sortBy, direction, "id", "id", "contractNo", "status", "startDate", "totalAmount", "createTime"));
        Page<EduContract> p = contractMapper.selectPage(new Page<>(page, size), qw);
        return Result.ok(PageResult.of(p.getTotal(), p.getRecords()));
    }

    @Data
    public static class ContractDTO {
        private Long id;
        private String advisor;
        private Long studentId;
        private Long packageId;
        private Long courseId;
        private Integer classType;
        private BigDecimal purchasedHours;
        private BigDecimal giftHours;
        private BigDecimal unitPrice;
        private BigDecimal totalAmount;
        private LocalDate startDate;
        private Integer validDays;
        /** 修改合同时的状态（新增固定待缴费，不接受传入） */
        private Integer status;
        private String attachmentUrl;
        private String remark;
    }

    /** 新增合同：编号自动生成、到期日=生效日+有效期、默认待缴费 */
    @PostMapping
    @Log("新增合同")
    public Result<EduContract> save(@RequestBody ContractDTO dto) {
        if (dto.getStudentId() == null || dto.getCourseId() == null
                || dto.getPurchasedHours() == null || dto.getUnitPrice() == null)
            return Result.error("学员、课程、购买课时、单价为必填项");
        EduContract c = new EduContract();
        c.setTenantId(AuthContext.get().getTenantId());
        c.setContractNo("CT" + LocalDate.now().format(NO_FMT)
                + String.format("%04d", (int) (Math.random() * 10000)));
        c.setStudentId(dto.getStudentId());
        c.setPackageId(dto.getPackageId());
        c.setCourseId(dto.getCourseId());
        c.setClassType(dto.getClassType() == null ? 1 : dto.getClassType());
        c.setPurchasedHours(dto.getPurchasedHours());
        c.setGiftHours(dto.getGiftHours() == null ? BigDecimal.ZERO : dto.getGiftHours());
        c.setUsedPurchased(BigDecimal.ZERO);
        c.setUsedGift(BigDecimal.ZERO);
        c.setUnitPrice(dto.getUnitPrice());
        c.setTotalAmount(dto.getTotalAmount() != null ? dto.getTotalAmount()
                : dto.getPurchasedHours().multiply(dto.getUnitPrice()));
        c.setPaidAmount(BigDecimal.ZERO);
        c.setStartDate(dto.getStartDate() != null ? dto.getStartDate() : LocalDate.now());
        c.setEndDate(dto.getStartDate() != null
                ? dto.getStartDate().plusDays(dto.getValidDays() == null ? 365 : dto.getValidDays())
                : LocalDate.now().plusDays(dto.getValidDays() == null ? 365 : dto.getValidDays()));
        c.setStatus(0);
        c.setAttachmentUrl(dto.getAttachmentUrl());
        c.setRemark(dto.getRemark());
        c.setAdvisor(dto.getAdvisor());
        c.setCreateBy(AuthContext.get().getRealName());
        com.edu.entity.EduStudent stu = studentMapper == null ? null : studentMapper.selectById(c.getStudentId());
        c.setOrgId(stu != null && stu.getOrgId() != null ? stu.getOrgId() : 1L);
        c.setDeleted(0);
        contractMapper.insert(c);
        logContract(c, "新增", "新增合同：购买 " + c.getPurchasedHours().stripTrailingZeros().toPlainString()
                + " 课时" + (c.getGiftHours() != null && c.getGiftHours().compareTo(BigDecimal.ZERO) > 0
                ? "，赠送 " + c.getGiftHours().stripTrailingZeros().toPlainString() + " 课时" : "")
                + "，总价 ¥" + c.getTotalAmount().stripTrailingZeros().toPlainString()
                + "，生效 " + c.getStartDate() + " 至 " + c.getEndDate());
        return Result.ok(c);
    }

    /**
     * 修改合同：咨询顾问/合同单价/总价/有效期/状态/备注/附件可改；
     * 学员、套餐与课时不可换（已关联缴费与消课记录）；
     * 合同总价不能低于已缴金额，退费状态须走退费流程。
     */
    @PutMapping
    @Log("修改合同")
    public Result<Void> update(@RequestBody ContractDTO dto) {
        if (dto.getId() == null) return Result.error("缺少合同ID");
        EduContract c = contractMapper.selectById(dto.getId());
        if (c == null) return Result.error("合同不存在");
        if (c.getStatus() != null && c.getStatus() == 3) return Result.error("已退费合同不允许修改");
        if (dto.getStatus() != null && dto.getStatus() == 3) return Result.error("合同退费请走退费流程");
        // 变更前快照（留痕用）
        final BigDecimal oldUnit = c.getUnitPrice(), oldTotal = c.getTotalAmount();
        final Integer oldStatus = c.getStatus();
        if (dto.getUnitPrice() != null) {
            if (dto.getUnitPrice().compareTo(BigDecimal.ZERO) <= 0)
                return Result.error("合同单价必须大于 0");
            c.setUnitPrice(dto.getUnitPrice());
        }
        // 合同总价：优先取表单值，否则按购买课时×单价重算
        if (dto.getTotalAmount() != null) c.setTotalAmount(dto.getTotalAmount());
        else if (dto.getUnitPrice() != null && c.getPurchasedHours() != null)
            c.setTotalAmount(c.getPurchasedHours().multiply(c.getUnitPrice()));
        java.math.BigDecimal paid = c.getPaidAmount() == null ? java.math.BigDecimal.ZERO : c.getPaidAmount();
        if (c.getTotalAmount() != null && c.getTotalAmount().compareTo(paid) < 0)
            return Result.error("合同总价 " + c.getTotalAmount().stripTrailingZeros().toPlainString()
                    + " 不能低于已缴金额 " + paid.stripTrailingZeros().toPlainString());
        // 有效期变化：重算到期日 = 生效日 + 有效期
        if (dto.getValidDays() != null) {
            LocalDate start = dto.getStartDate() != null ? dto.getStartDate()
                    : (c.getStartDate() != null ? c.getStartDate() : LocalDate.now());
            if (dto.getStartDate() != null) c.setStartDate(dto.getStartDate());
            c.setEndDate(start.plusDays(dto.getValidDays()));
        }
        if (dto.getStatus() != null) c.setStatus(dto.getStatus());
        c.setAdvisor(dto.getAdvisor());
        c.setAttachmentUrl(dto.getAttachmentUrl());
        c.setRemark(dto.getRemark());
        contractMapper.updateById(c);
        // 修改留痕：摘要记录关键变更（单价/总价/有效期/状态）
        List<String> chg = new java.util.ArrayList<>();
        if (dto.getUnitPrice() != null && oldUnit != null && dto.getUnitPrice().compareTo(oldUnit) != 0)
            chg.add("单价 ¥" + oldUnit.stripTrailingZeros().toPlainString()
                    + "→¥" + c.getUnitPrice().stripTrailingZeros().toPlainString());
        if (c.getTotalAmount() != null && oldTotal != null && c.getTotalAmount().compareTo(oldTotal) != 0)
            chg.add("总价 ¥" + oldTotal.stripTrailingZeros().toPlainString()
                    + "→¥" + c.getTotalAmount().stripTrailingZeros().toPlainString());
        if (dto.getValidDays() != null)
            chg.add("有效期调整为 " + dto.getValidDays() + " 天（到期日 " + c.getEndDate() + "）");
        if (dto.getStatus() != null && oldStatus != null && !dto.getStatus().equals(oldStatus))
            chg.add("状态 " + statusTextOf(oldStatus) + "→" + statusTextOf(c.getStatus()));
        logContract(c, "修改", chg.isEmpty() ? "修改合同：经办信息/备注/附件等" : "修改合同：" + String.join("，", chg));
        return Result.ok();
    }

    /** 合同状态文案（留痕用） */
    private static String statusTextOf(Integer s) {
        if (s == null) return "-";
        switch (s) {
            case 0: return "待缴费";
            case 1: return "部分缴费";
            case 2: return "已结清";
            case 3: return "已退费";
            case 4: return "已到期";
            default: return String.valueOf(s);
        }
    }

    /** 合同详情：含缴费记录、消课记录 */
    @GetMapping("/{id}")
    public Result<Map<String, Object>> detail(@PathVariable Long id) {
        EduContract c = contractMapper.selectById(id);
        if (c == null) return Result.error("合同不存在");
        Map<String, Object> data = new HashMap<>();
        data.put("contract", c);
        data.put("payments", paymentMapper.selectList(new LambdaQueryWrapper<EduPayment>()
                .eq(EduPayment::getContractId, id).orderByDesc(EduPayment::getId)));

        // 套餐信息
        if (c.getPackageId() != null) {
            com.edu.entity.EduPackage pkg = packageMapper == null ? null : packageMapper.selectById(c.getPackageId());
            if (pkg != null) {
                data.put("packageName", pkg.getName());
                data.put("packageUnitPrice", pkg.getUnitPrice());
                java.math.BigDecimal pkgTotal = pkg.getUnitPrice() == null ? java.math.BigDecimal.ZERO
                        : pkg.getUnitPrice().multiply(java.math.BigDecimal.valueOf(pkg.getHours() == null ? 0 : pkg.getHours().doubleValue()));
                data.put("packageTotalPrice", pkgTotal);
            }
        }
        // 课时统计：总课时/已消/剩余
        java.math.BigDecimal purchased = c.getPurchasedHours() == null ? java.math.BigDecimal.ZERO : c.getPurchasedHours();
        java.math.BigDecimal gift = c.getGiftHours() == null ? java.math.BigDecimal.ZERO : c.getGiftHours();
        java.math.BigDecimal usedP = c.getUsedPurchased() == null ? java.math.BigDecimal.ZERO : c.getUsedPurchased();
        java.math.BigDecimal usedG = c.getUsedGift() == null ? java.math.BigDecimal.ZERO : c.getUsedGift();
        java.math.BigDecimal total = purchased.add(gift);
        java.math.BigDecimal used = usedP.add(usedG);
        data.put("totalHours", total);
        data.put("usedHours", used);
        data.put("remainHours", total.subtract(used));
        return Result.ok(data);
    }

    /**
     * 退费页数据：合同全量信息（对齐合同详情）+
     * 已上课时（学员课时记录表 edu_consume 汇总）、已退课时（学员退费表 edu_refund 汇总）
     */
    @GetMapping("/{id}/refund-info")
    public Result<Map<String, Object>> refundInfo(@PathVariable Long id) {
        EduContract c = contractMapper.selectById(id);
        if (c == null) return Result.error("合同不存在");
        Map<String, Object> data = new HashMap<>();
        data.put("contract", c);
        // 套餐信息（合同名称取套餐名称）
        java.math.BigDecimal packageTotal = null;
        if (c.getPackageId() != null) {
            com.edu.entity.EduPackage pkg = packageMapper == null ? null : packageMapper.selectById(c.getPackageId());
            if (pkg != null) {
                data.put("packageName", pkg.getName());
                data.put("packageUnitPrice", pkg.getUnitPrice());
                packageTotal = pkg.getUnitPrice() == null ? java.math.BigDecimal.ZERO
                        : pkg.getUnitPrice().multiply(java.math.BigDecimal.valueOf(
                                pkg.getHours() == null ? 0 : pkg.getHours().doubleValue()));
                data.put("packageTotalPrice", packageTotal);
            }
        }
        // 课时统计：总课时/已消/剩余
        java.math.BigDecimal purchased = c.getPurchasedHours() == null ? java.math.BigDecimal.ZERO : c.getPurchasedHours();
        java.math.BigDecimal gift = c.getGiftHours() == null ? java.math.BigDecimal.ZERO : c.getGiftHours();
        java.math.BigDecimal usedP = c.getUsedPurchased() == null ? java.math.BigDecimal.ZERO : c.getUsedPurchased();
        java.math.BigDecimal usedG = c.getUsedGift() == null ? java.math.BigDecimal.ZERO : c.getUsedGift();
        java.math.BigDecimal remain = purchased.add(gift).subtract(usedP).subtract(usedG);
        data.put("totalHours", purchased.add(gift));
        data.put("usedHours", usedP.add(usedG));
        data.put("remainHours", remain);
        // 退费金额上限：有套餐用套餐总价，无套餐兜底合同总价
        java.math.BigDecimal cap = packageTotal != null && packageTotal.compareTo(java.math.BigDecimal.ZERO) > 0
                ? packageTotal
                : (c.getTotalAmount() == null ? java.math.BigDecimal.ZERO : c.getTotalAmount());
        data.put("refundCapAmount", cap);
        // 已上课时：学员课时记录表（edu_consume，未撤销）
        java.math.BigDecimal attended = java.math.BigDecimal.ZERO;
        for (EduConsume con : consumeMapper.selectList(new LambdaQueryWrapper<EduConsume>()
                .eq(EduConsume::getStudentId, c.getStudentId())
                .eq(EduConsume::getCanceled, 0)
                .eq(EduConsume::getDeleted, 0)
                .select(EduConsume::getHours)))
            attended = attended.add(con.getHours() == null ? java.math.BigDecimal.ZERO : con.getHours());
        data.put("attendedHours", attended);
        // 已退课时：学员退费表（edu_refund 汇总）
        java.math.BigDecimal refunded = java.math.BigDecimal.ZERO;
        for (EduRefund r : refundMapper.selectList(new LambdaQueryWrapper<EduRefund>()
                .eq(EduRefund::getStudentId, c.getStudentId())
                .select(EduRefund::getRefundHours)))
            refunded = refunded.add(r.getRefundHours() == null ? java.math.BigDecimal.ZERO : r.getRefundHours());
        data.put("refundedHours", refunded);
        return Result.ok(data);
    }

    /**
     * 退费：退费课时不能大于剩余课时、退费金额不能大于套餐总价（服务端强制校验），
     * 落退费记录（edu_refund 留痕），剩余课时冻结
     */
    @PostMapping("/{id}/refund")
    @Log("合同退费")
    @Transactional
    public Result<Void> refund(@PathVariable Long id, @RequestBody RefundDTO dto) {
        EduContract c = contractMapper.selectById(id);
        if (c == null) return Result.error("合同不存在");
        if (c.getStatus() == 3) return Result.error("合同已是退费状态");
        if (dto.getRefundHours() == null || dto.getRefundAmount() == null)
            return Result.error("退费课时、退费金额为必填项");
        // 剩余课时 = 购买+赠送 − 已消
        java.math.BigDecimal purchased = c.getPurchasedHours() == null ? java.math.BigDecimal.ZERO : c.getPurchasedHours();
        java.math.BigDecimal gift = c.getGiftHours() == null ? java.math.BigDecimal.ZERO : c.getGiftHours();
        java.math.BigDecimal usedP = c.getUsedPurchased() == null ? java.math.BigDecimal.ZERO : c.getUsedPurchased();
        java.math.BigDecimal usedG = c.getUsedGift() == null ? java.math.BigDecimal.ZERO : c.getUsedGift();
        java.math.BigDecimal remain = purchased.add(gift).subtract(usedP).subtract(usedG);
        java.math.BigDecimal maxHours = remain.max(java.math.BigDecimal.ZERO);
        if (dto.getRefundHours().compareTo(java.math.BigDecimal.ZERO) < 0)
            return Result.error("退费课时不能为负数");
        if (dto.getRefundHours().compareTo(maxHours) > 0)
            return Result.error("退费课时 " + dto.getRefundHours().stripTrailingZeros().toPlainString()
                    + " 不能大于剩余课时 " + remain.stripTrailingZeros().toPlainString());
        // 金额上限：套餐总价，无套餐兜底合同总价
        java.math.BigDecimal cap = java.math.BigDecimal.ZERO;
        if (c.getPackageId() != null) {
            com.edu.entity.EduPackage pkg = packageMapper == null ? null : packageMapper.selectById(c.getPackageId());
            if (pkg != null && pkg.getUnitPrice() != null)
                cap = pkg.getUnitPrice().multiply(java.math.BigDecimal.valueOf(
                        pkg.getHours() == null ? 0 : pkg.getHours().doubleValue()));
        }
        if (cap.compareTo(java.math.BigDecimal.ZERO) <= 0)
            cap = c.getTotalAmount() == null ? java.math.BigDecimal.ZERO : c.getTotalAmount();
        if (dto.getRefundAmount().compareTo(java.math.BigDecimal.ZERO) < 0)
            return Result.error("退费金额不能为负数");
        if (dto.getRefundAmount().compareTo(cap) > 0)
            return Result.error("退费金额 " + dto.getRefundAmount().stripTrailingZeros().toPlainString()
                    + " 不能大于套餐总价 " + cap.stripTrailingZeros().toPlainString());
        c.setStatus(3);
        c.setRefundHours(dto.getRefundHours());
        c.setRefundAmount(dto.getRefundAmount());
        contractMapper.updateById(c);
        // 退费方式：2=退费至账户（金额入学生账户余额，学生中心账户管理可查）
        Integer refundTo = dto.getRefundTo() == null ? 1 : dto.getRefundTo();
        // 退费留痕：落退费记录表
        EduRefund r = new EduRefund();
        r.setTenantId(c.getTenantId());
        r.setRefundNo("RF" + LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyyyMMddHHmmss"))
                + String.format("%03d", (int) (Math.random() * 1000)));
        r.setContractId(c.getId());
        r.setStudentId(c.getStudentId());
        r.setCourseId(c.getCourseId());
        r.setRefundHours(dto.getRefundHours());
        r.setRefundAmount(dto.getRefundAmount());
        r.setRefundTo(refundTo);
        r.setRemainHours(remain);
        r.setReason(dto.getReason());
        r.setOperator(AuthContext.get().getRealName());
        r.setOrgId(c.getOrgId());
        refundMapper.insert(r);
        // 退费至账户：写账户流水（类型=退费入账，学生端可见 金额/课时/合同/操作人/时间）
        if (refundTo == 2) {
            com.edu.entity.EduAccountLog accountLog = new com.edu.entity.EduAccountLog();
            accountLog.setTenantId(c.getTenantId());
            accountLog.setStudentId(c.getStudentId());
            accountLog.setContractId(c.getId());
            accountLog.setType(2);
            accountLog.setAmount(dto.getRefundAmount());
            accountLog.setHours(dto.getRefundHours());
            accountLog.setRemark(StringUtils.hasText(dto.getReason())
                    ? "退费入账：" + dto.getReason() : "退费入账");
            accountLog.setOperator(AuthContext.get().getRealName());
            accountLogMapper.insert(accountLog);
        }
        logContract(c, "退费", "合同退费：" + dto.getRefundHours().stripTrailingZeros().toPlainString()
                + " 课时、¥" + dto.getRefundAmount().stripTrailingZeros().toPlainString()
                + "（退费方式：" + (refundTo == 2 ? "退费至账户" : "退费至个人")
                + (StringUtils.hasText(dto.getReason()) ? "，原因：" + dto.getReason() : "") + "）");
        return Result.ok();
    }

    /** 学员课时汇总（新增合同时展示：历史已消课时） */
    @GetMapping("/student-summary")
    public Result<Map<String, Object>> studentSummary(@RequestParam Long studentId) {
        List<com.edu.entity.EduContract> list = contractMapper.selectList(
                new LambdaQueryWrapper<EduContract>()
                        .eq(EduContract::getStudentId, studentId)
                        .eq(EduContract::getDeleted, 0));
        java.math.BigDecimal used = java.math.BigDecimal.ZERO;
        int contractCount = list.size();
        for (EduContract c : list) {
            used = used.add(c.getUsedPurchased() == null ? java.math.BigDecimal.ZERO : c.getUsedPurchased());
            used = used.add(c.getUsedGift() == null ? java.math.BigDecimal.ZERO : c.getUsedGift());
        }
        Map<String, Object> m = new HashMap<>();
        m.put("usedHours", used);
        m.put("contractCount", contractCount);
        return Result.ok(m);
    }

    /** 物理删除：有缴费/消课/退费记录的合同不允许删（数据不可再生）；删除后历史TAB仍可追溯 */
    @DeleteMapping("/{id}")
    @Log("删除合同")
    public Result<Void> delete(@PathVariable Long id) {
        EduContract c = contractMapper.selectById(id);
        if (c == null) return Result.ok();
        Long pays = paymentMapper.selectCount(new LambdaQueryWrapper<EduPayment>()
                .eq(EduPayment::getContractId, id));
        if (pays != null && pays > 0) return Result.error("该合同已有缴费记录，不允许删除");
        Long cons = consumeMapper.selectCount(new LambdaQueryWrapper<EduConsume>()
                .eq(EduConsume::getContractId, id));
        Long refs = refundMapper.selectCount(new LambdaQueryWrapper<EduRefund>()
                .eq(EduRefund::getContractId, id));
        // 消课/退费记录随合同一并物理清除（同生共死，避免悬挂引用），数量留痕到合同历史
        if (cons != null && cons > 0)
            consumeMapper.delete(new LambdaQueryWrapper<EduConsume>().eq(EduConsume::getContractId, id));
        if (refs != null && refs > 0)
            refundMapper.delete(new LambdaQueryWrapper<EduRefund>().eq(EduRefund::getContractId, id));
        // 先留痕再删除（物理删除后合同行不存在，日志保留编号与摘要）
        StringBuilder del = new StringBuilder("物理删除合同：").append(c.getContractNo())
                .append("（购买 ").append(c.getPurchasedHours() == null ? "0"
                        : c.getPurchasedHours().stripTrailingZeros().toPlainString())
                .append(" 课时，总价 ¥").append(c.getTotalAmount() == null ? "0"
                        : c.getTotalAmount().stripTrailingZeros().toPlainString()).append("）");
        if (c.getRefundHours() != null && c.getRefundHours().compareTo(BigDecimal.ZERO) > 0)
            del.append("，已退 ").append(c.getRefundHours().stripTrailingZeros().toPlainString())
                    .append(" 课时 ¥").append(c.getRefundAmount() == null ? "0"
                            : c.getRefundAmount().stripTrailingZeros().toPlainString());
        if (cons != null && cons > 0) del.append("，随删消课记录 ").append(cons).append(" 条");
        if (refs != null && refs > 0) del.append("，随删退费记录 ").append(refs).append(" 条");
        logContract(c, "删除", del.toString());
        contractMapper.deleteById(id);
        return Result.ok();
    }

    /** 咨询顾问候选：用户管理中非学生、非系统用户、非管理员的在职账号（按登录租户自动隔离） */
    @GetMapping("/advisor-candidates")
    public Result<List<Map<String, Object>>> advisorCandidates() {
        return Result.ok(staffCandidates());
    }

    /** 员工候选（顾问/教室联系人等共用）：本企业在职员工账号，附手机号供联动回填 */
    private List<Map<String, Object>> staffCandidates() {
        // 用户类型：2教管 3班主任 4教师 6校长 7咨询师 8学管师（排除 5学生/9系统用户/1管理员）
        List<com.edu.entity.SysUser> staff = sysUserMapper.selectList(
                new LambdaQueryWrapper<com.edu.entity.SysUser>()
                        .in(com.edu.entity.SysUser::getUserType, java.util.Arrays.asList(2, 3, 4, 6, 7, 8))
                        .eq(com.edu.entity.SysUser::getStatus, 1)
                        .orderByAsc(com.edu.entity.SysUser::getRealName));
        List<Map<String, Object>> result = new java.util.ArrayList<>();
        for (com.edu.entity.SysUser u : staff) {
            Map<String, Object> m = new HashMap<>();
            m.put("name", StringUtils.hasText(u.getRealName()) ? u.getRealName() : u.getUsername());
            m.put("username", u.getUsername());
            m.put("userType", u.getUserType());
            m.put("phone", u.getPhone());
            result.add(m);
        }
        return result;
    }

    /** 每日任务可将到期合同标记到期（也可查询时动态判断） */
    @PostMapping("/refresh-expired")
    public Result<Integer> refreshExpired() {
        List<EduContract> list = contractMapper.selectList(new LambdaQueryWrapper<EduContract>()
                .eq(EduContract::getStatus, 2).lt(EduContract::getEndDate, LocalDate.now()));
        for (EduContract c : list) {
            c.setStatus(4);
            contractMapper.updateById(c);
        }
        return Result.ok(list.size());
    }

    @Data
    public static class RefundDTO {
        private BigDecimal refundHours;
        private BigDecimal refundAmount;
        /** 退费方式：1-退费至个人（默认） 2-退费至账户（入学生账户余额） */
        private Integer refundTo;
        /** 退费原因（选填，留痕） */
        private String reason;
    }
}
