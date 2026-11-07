package com.edu.controller;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.edu.common.Log;
import com.edu.common.PageResult;
import com.edu.common.Result;
import com.edu.common.SafeSort;
import com.edu.entity.EduAccountLog;
import com.edu.entity.EduContract;
import com.edu.entity.EduStudent;
import com.edu.mapper.EduAccountLogMapper;
import com.edu.mapper.EduContractMapper;
import com.edu.mapper.EduStudentMapper;
import com.edu.security.AuthContext;
import com.edu.security.RequiresPerm;
import lombok.Data;
import lombok.RequiredArgsConstructor;
import org.springframework.util.StringUtils;
import org.springframework.web.bind.annotation.*;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.*;
import java.util.stream.Collectors;

/**
 * 充值管理（教务）：学生账户充值（必须关联合同并注明原因，典型场景：
 * 1对4课程学员请假后转1对1补课，单价差异需补差价入账）与流水查询。
 */
@RestController
@RequestMapping("/edu/account")
@RequiredArgsConstructor
@RequiresPerm("edu:account")
public class EduAccountController {

    private final EduAccountLogMapper accountMapper;
    private final EduStudentMapper studentMapper;
    private final EduContractMapper contractMapper;

    /** 流水分页：按学生/类型/时间筛选，附学生姓名与合同信息 */
    @GetMapping("/page")
    public Result<PageResult<Map<String, Object>>> page(@RequestParam(defaultValue = "1") long page,
                                                        @RequestParam(defaultValue = "10") long size,
                                                        @RequestParam(required = false) Long studentId,
                                                        @RequestParam(required = false) Integer type,
                                                        @RequestParam(required = false) String begin,
                                                        @RequestParam(required = false) String end,
                                                        @RequestParam(required = false) String sortBy,
                                                        @RequestParam(required = false) String direction) {
        Page<EduAccountLog> p = accountMapper.selectPage(new Page<>(page, size),
                new LambdaQueryWrapper<EduAccountLog>()
                        .eq(studentId != null, EduAccountLog::getStudentId, studentId)
                        // 已删除学生的账户流水不再展示
                        .notInSql(EduAccountLog::getStudentId,
                                "SELECT id FROM edu_student WHERE deleted = 1")
                        .eq(type != null, EduAccountLog::getType, type)
                        .ge(StringUtils.hasText(begin), EduAccountLog::getCreateTime, begin + " 00:00:00")
                        .le(StringUtils.hasText(end), EduAccountLog::getCreateTime, end + " 23:59:59")
                        .last(SafeSort.order(sortBy, direction, "id", "id", "amount", "createTime")));
        return Result.ok(PageResult.of(p.getTotal(), enrich(p.getRecords())));
    }

    /** 汇总（当前筛选范围）：累计充值 / 退费入账 / 账户余额合计 */
    @GetMapping("/summary")
    public Result<Map<String, Object>> summary(@RequestParam(required = false) Long studentId) {
        List<EduAccountLog> all = accountMapper.selectList(new LambdaQueryWrapper<EduAccountLog>()
                .eq(studentId != null, EduAccountLog::getStudentId, studentId));
        BigDecimal recharge = sumOf(all, 1), refund = sumOf(all, 2);
        Map<String, Object> m = new HashMap<>();
        m.put("rechargeTotal", recharge);
        m.put("refundInTotal", refund);
        m.put("balanceTotal", recharge.add(refund));
        return Result.ok(m);
    }

    /**
     * 充值：必须关联合同（合同须属于该学生）并填写原因（如 1对4转1对1补差价）。
     */
    @PostMapping("/recharge")
    @Log("学生账户充值")
    public Result<Void> recharge(@RequestBody RechargeDTO dto) {
        if (dto.getStudentId() == null || dto.getContractId() == null)
            return Result.error("学生与关联合同为必填项");
        if (dto.getAmount() == null || dto.getAmount().compareTo(BigDecimal.ZERO) <= 0)
            return Result.error("充值金额必须大于 0");
        if (!StringUtils.hasText(dto.getRemark()))
            return Result.error("请填写充值原因（如：1对4转1对1补差价）");
        EduContract c = contractMapper.selectById(dto.getContractId());
        if (c == null) return Result.error("合同不存在");
        if (!dto.getStudentId().equals(c.getStudentId()))
            return Result.error("所选合同不属于该学生，请重新选择");

        EduAccountLog log = new EduAccountLog();
        log.setTenantId(c.getTenantId());
        log.setStudentId(dto.getStudentId());
        log.setContractId(dto.getContractId());
        log.setType(1);
        log.setAmount(dto.getAmount());
        log.setRemark(dto.getRemark());
        log.setOperator(AuthContext.get().getRealName());
        accountMapper.insert(log);
        return Result.ok();
    }

    /**
     * 无合同余额充值（报名/续报办理中心-余额充值入口）：预存学费到学员账户，
     * 后续报名时以余额抵扣；多账户支付明细与经办信息随单独立字段留档。
     */
    @PostMapping("/recharge-balance")
    @Log("学员余额充值")
    public Result<Void> rechargeBalance(@RequestBody BalanceRechargeDTO dto) {
        if (dto.getStudentId() == null) return Result.error("请选择学员");
        if (dto.getAmount() == null || dto.getAmount().compareTo(BigDecimal.ZERO) <= 0)
            return Result.error("充值金额必须大于 0");
        if (dto.getPayDetail() != null && dto.getPayDetail().length() > 1000)
            return Result.error("支付明细过长，请减少支付账户条数");
        EduStudent s = studentMapper.selectById(dto.getStudentId());
        if (s == null || s.getDeleted() == 1) return Result.error("学员不存在或已删除");

        EduAccountLog log = new EduAccountLog();
        log.setTenantId(s.getTenantId());
        log.setStudentId(dto.getStudentId());
        log.setType(1);
        log.setAmount(dto.getAmount());
        log.setCampus(dto.getCampus());
        log.setPayDetail(dto.getPayDetail());
        log.setSource(dto.getSource());
        log.setHandleDate(dto.getHandleDate());
        log.setOrderTaker(dto.getOrderTaker());
        log.setConsultant(dto.getConsultant());
        // 备注：余额充值：原因（支付渠道/校区等已有独立字段，不再并入备注）
        StringBuilder remark = new StringBuilder("余额充值");
        if (StringUtils.hasText(dto.getRemark())) remark.append("：").append(dto.getRemark());
        log.setRemark(remark.toString());
        log.setOperator(AuthContext.get().getRealName());
        accountMapper.insert(log);
        return Result.ok();
    }

    /** 流水附学生姓名/合同编号与课程 */
    private List<Map<String, Object>> enrich(List<EduAccountLog> records) {
        if (records.isEmpty()) return new ArrayList<>();
        Set<Long> sids = records.stream().map(EduAccountLog::getStudentId).collect(Collectors.toSet());
        Map<Long, String> sName = new HashMap<>();
        for (EduStudent s : studentMapper.selectBatchIds(sids)) sName.put(s.getId(), s.getName());
        Set<Long> cids = records.stream().map(EduAccountLog::getContractId)
                .filter(Objects::nonNull).collect(Collectors.toSet());
        Map<Long, EduContract> cMap = cids.isEmpty() ? new HashMap<>()
                : contractMapper.selectBatchIds(cids).stream()
                    .collect(Collectors.toMap(EduContract::getId, c -> c, (a, b) -> a));
        List<Map<String, Object>> rows = new ArrayList<>();
        for (EduAccountLog l : records) {
            Map<String, Object> m = new LinkedHashMap<>();
            m.put("id", l.getId());
            m.put("studentName", sName.getOrDefault(l.getStudentId(), "-"));
            m.put("type", l.getType());
            m.put("amount", l.getAmount());
            m.put("hours", l.getHours());
            m.put("campus", l.getCampus());
            m.put("payDetail", l.getPayDetail());
            m.put("source", l.getSource());
            m.put("handleDate", l.getHandleDate());
            m.put("orderTaker", l.getOrderTaker());
            m.put("consultant", l.getConsultant());
            EduContract c = l.getContractId() == null ? null : cMap.get(l.getContractId());
            m.put("contractNo", c == null ? "-" : c.getContractNo());
            m.put("remark", l.getRemark());
            m.put("operator", l.getOperator());
            m.put("createTime", l.getCreateTime());
            rows.add(m);
        }
        return rows;
    }

    private BigDecimal sumOf(List<EduAccountLog> list, int type) {
        BigDecimal s = BigDecimal.ZERO;
        for (EduAccountLog l : list)
            if (l.getType() != null && l.getType() == type && l.getAmount() != null)
                s = s.add(l.getAmount());
        return s;
    }

    @Data
    public static class RechargeDTO {
        private Long studentId;
        private Long contractId;
        private BigDecimal amount;
        private String remark;
    }

    /** 无合同余额充值（办理中心入口） */
    @Data
    public static class BalanceRechargeDTO {
        private Long studentId;
        /** 充值金额（支付账户实收合计） */
        private BigDecimal amount;
        /** 支付明细JSON [{channel,account,serial,amount}]（多账户支付留档） */
        private String payDetail;
        /** 经办校区 */
        private String campus;
        /** 销售来源 */
        private String source;
        /** 经办日期 */
        private LocalDate handleDate;
        /** 采单员 */
        private String orderTaker;
        /** 咨询顾问 */
        private String consultant;
        private String remark;
    }
}
