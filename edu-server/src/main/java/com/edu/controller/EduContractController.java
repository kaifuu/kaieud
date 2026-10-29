package com.edu.controller;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.edu.common.Log;
import com.edu.common.PageResult;
import com.edu.common.Result;
import com.edu.common.SafeSort;
import com.edu.entity.EduContract;
import com.edu.entity.EduPayment;
import com.edu.mapper.EduContractMapper;
import com.edu.mapper.EduPaymentMapper;
import com.edu.security.AuthContext;
import com.edu.security.RequiresPerm;
import lombok.Data;
import lombok.RequiredArgsConstructor;
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
    private final com.edu.mapper.EduStudentMapper studentMapper;
    private final com.edu.mapper.EduPackageMapper packageMapper;
    private final com.edu.service.DataScopeService dataScopeService;

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
        return Result.ok(c);
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

    /** 退费：填写退费课时、金额，剩余课时冻结 */
    @PostMapping("/{id}/refund")
    @Log("合同退费")
    public Result<Void> refund(@PathVariable Long id, @RequestBody RefundDTO dto) {
        EduContract c = contractMapper.selectById(id);
        if (c == null) return Result.error("合同不存在");
        if (c.getStatus() == 3) return Result.error("合同已是退费状态");
        c.setStatus(3);
        c.setRefundHours(dto.getRefundHours());
        c.setRefundAmount(dto.getRefundAmount());
        contractMapper.updateById(c);
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

    /** 逻辑删除 */
    @DeleteMapping("/{id}")
    @Log("删除合同")
    public Result<Void> delete(@PathVariable Long id) {
        EduContract c = contractMapper.selectById(id);
        if (c != null) { c.setDeleted(1); contractMapper.updateById(c); }
        return Result.ok();
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
    }
}
