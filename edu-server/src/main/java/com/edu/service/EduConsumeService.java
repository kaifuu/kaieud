package com.edu.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.edu.common.exception.BizException;
import com.edu.entity.EduConsume;
import com.edu.entity.EduContract;
import com.edu.mapper.EduConsumeMapper;
import com.edu.mapper.EduContractMapper;
import com.edu.security.AuthContext;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;

/**
 * 消课核心服务。
 * PRD固化规则：
 * 1. 课时扣减顺序：先扣购买课时，购买课时扣完再扣赠送课时；
 * 2. 单次消课课时支持小数(0.5)；
 * 3. 消课瞬间自动计提教师成本（成本报表以消课记录为准实时计算）；
 * 4. 撤销消课：归还课时、记录留痕；
 * 5. 已退费/已到期合同默认不允许消课。
 */
@Service
@RequiredArgsConstructor
public class EduConsumeService {

    private static final DateTimeFormatter NO_FMT = DateTimeFormatter.ofPattern("yyyyMMddHHmmss");

    private final EduConsumeMapper consumeMapper;
    private final EduContractMapper contractMapper;
    private final com.edu.mapper.EduScheduleMapper scheduleMapper;
    private final com.edu.mapper.EduStudentMapper studentMapper;
    private final com.edu.mapper.EduEnrollMapper enrollMapper;

    /** 消课：绑定合同，按 购买->赠送 顺序扣减 */
    @Transactional
    public EduConsume consume(Long contractId, Long scheduleId, BigDecimal hours, Integer consumeType, Long teacherId) {
        return consume(contractId, scheduleId, hours, consumeType, teacherId, "消课");
    }

    /** 消课（带扣课来源：消课/签到消课/到课/旷课） */
    @Transactional
    public EduConsume consume(Long contractId, Long scheduleId, BigDecimal hours, Integer consumeType, Long teacherId, String source) {
        return consume(contractId, scheduleId, hours, consumeType, teacherId, source, null);
    }

    /** 消课（带扣课来源与扣课说明） */
    @Transactional
    public EduConsume consume(Long contractId, Long scheduleId, BigDecimal hours, Integer consumeType,
                              Long teacherId, String source, String remark) {
        EduContract contract = contractMapper.selectById(contractId);
        if (contract == null || contract.getDeleted() == 1) throw new BizException("合同不存在");
        if (contract.getStatus() == 3) throw new BizException("合同已退费，课时已冻结，不允许消课");
        if (contract.getStatus() == 4) throw new BizException("合同已到期，默认不允许消课（可联系管理员处理）");
        if (hours == null || hours.compareTo(BigDecimal.ZERO) <= 0) throw new BizException("消课课时必须大于0");

        BigDecimal remainPurchased = contract.getPurchasedHours().subtract(nz(contract.getUsedPurchased()));
        BigDecimal remainGift = contract.getGiftHours().subtract(nz(contract.getUsedGift()));
        BigDecimal before = remainPurchased.add(remainGift);
        if (before.compareTo(hours) < 0) throw new BizException("剩余课时不足：当前剩余 " + before + " 课时");

        // 扣减顺序：先购买，后赠送
        BigDecimal usePurchased = remainPurchased.min(hours);
        BigDecimal useGift = hours.subtract(usePurchased);
        contract.setUsedPurchased(nz(contract.getUsedPurchased()).add(usePurchased));
        contract.setUsedGift(nz(contract.getUsedGift()).add(useGift));
        contractMapper.updateById(contract);

        EduConsume c = new EduConsume();
        c.setTenantId(contract.getTenantId());
        c.setConsumeNo("XC" + LocalDateTime.now().format(NO_FMT)
                + String.format("%03d", (int) (Math.random() * 1000)));
        c.setContractId(contractId);
        c.setScheduleId(scheduleId);
        c.setStudentId(contract.getStudentId());
        c.setCourseId(contract.getCourseId());
        c.setTeacherId(scheduleTeacherId(scheduleId));
        if (c.getTeacherId() == null) c.setTeacherId(teacherId);
        if (c.getTeacherId() == null) throw new BizException("消课必须关联授课老师");
        c.setClassType(contract.getClassType());
        c.setHours(hours);
        c.setBeforeHours(before);
        c.setAfterHours(before.subtract(hours));
        c.setConsumeType(consumeType == null ? 0 : consumeType);
        c.setSource(source == null ? "消课" : source);
        c.setRemark(remark);
        c.setOperator(operatorName());
        c.setCanceled(0);
        c.setDeleted(0);
        // 归属校区跟随学员；学管师(userType=8)录入的课消需总部教务审核后才计入成本
        com.edu.entity.EduStudent stu = studentMapper.selectById(c.getStudentId());
        c.setOrgId(stu != null && stu.getOrgId() != null ? stu.getOrgId() : 1L);
        com.edu.security.LoginUser lu = com.edu.security.AuthContext.get();
        c.setAuditStatus(lu != null && lu.getUserType() != null && lu.getUserType() == 8 ? 0 : 1);
        consumeMapper.insert(c);
        return c;
    }

    /**
     * 排课一键签到消课：一对一按学员合同消课；班课对班内所有在读学员合同逐一消课。
     * 返回成功消课的合同数。
     */
    @Transactional
    public int signBySchedule(Long scheduleId, BigDecimal hours) {
        return signBySchedule(scheduleId, hours, "签到消课");
    }

    /** 同上，带扣课来源（到课/旷课等，写入课时管理台账） */
    @Transactional
    public int signBySchedule(Long scheduleId, BigDecimal hours, String source) {
        return signBySchedule(scheduleId, hours, source, null);
    }

    /** 同上，带扣课来源与扣课说明（扣减少于应扣节数时记录原因） */
    @Transactional
    public int signBySchedule(Long scheduleId, BigDecimal hours, String source, String remark) {
        com.edu.entity.EduSchedule sch = scheduleMapper.selectById(scheduleId);
        if (sch == null) throw new BizException("排课记录不存在");
        java.util.List<Long> contractIds = new java.util.ArrayList<>();
        if (sch.getStudentId() != null) {
            // 一对一：该学员该课程的生效合同
            contractIds.addAll(activeContractIds(sch.getStudentId(), sch.getCourseId()));
        } else if (sch.getClazzId() != null) {
            // 班课：班内所有在读学员
            java.util.List<com.edu.entity.EduEnroll> enrolls = enrollMapper.selectList(
                    new LambdaQueryWrapper<com.edu.entity.EduEnroll>()
                            .eq(com.edu.entity.EduEnroll::getClazzId, sch.getClazzId())
                            .eq(com.edu.entity.EduEnroll::getStatus, 1));
            for (com.edu.entity.EduEnroll e : enrolls) {
                contractIds.addAll(activeContractIds(e.getStudentId(), sch.getCourseId()));
            }
        } else {
            throw new BizException("排课记录缺少学员/班级信息");
        }
        if (contractIds.isEmpty()) throw new BizException("未找到可消课的生效合同，请先为学员签订合同");
        int count = 0;
        for (Long cid : contractIds) {
            consume(cid, scheduleId, hours, 0, null, source, remark);
            count++;
        }
        return count;
    }

    /** 学员某课程下的生效合同（未退费未到期未删除，优先最近创建） */
    private java.util.List<Long> activeContractIds(Long studentId, Long courseId) {
        return contractMapper.selectList(new LambdaQueryWrapper<EduContract>()
                        .eq(EduContract::getStudentId, studentId)
                        .eq(EduContract::getCourseId, courseId)
                        .in(EduContract::getStatus, 0, 1, 2)
                        .eq(EduContract::getDeleted, 0)
                        .orderByDesc(EduContract::getId))
                .stream().map(EduContract::getId).collect(java.util.stream.Collectors.toList());
    }

    /** 撤销消课：归还课时（对称回滚购买/赠送），标记撤销留痕 */
    @Transactional
    public void cancel(Long consumeId) {
        EduConsume c = consumeMapper.selectById(consumeId);
        if (c == null) throw new BizException("消课记录不存在");
        if (c.getCanceled() == 1) throw new BizException("该记录已撤销");
        EduContract contract = contractMapper.selectById(c.getContractId());
        if (contract == null) throw new BizException("合同不存在");

        // 对称回滚：优先归还赠送（与扣减顺序相反），再归还购买
        BigDecimal usedGift = nz(contract.getUsedGift());
        BigDecimal returnGift = usedGift.min(c.getHours());
        contract.setUsedGift(usedGift.subtract(returnGift));
        contract.setUsedPurchased(nz(contract.getUsedPurchased()).subtract(c.getHours().subtract(returnGift)));
        contractMapper.updateById(contract);

        c.setCanceled(1);
        c.setCancelTime(LocalDateTime.now());
        consumeMapper.updateById(c);
    }

    /**
     * 课消审核（总部教务/运营）：通过=计入成本报表；驳回=归还课时并标记驳回。
     * 成本报表口径只统计 audit_status = 1 的记录。
     */
    @Transactional
    public void audit(Long consumeId, boolean pass, String remark, String auditor) {
        EduConsume c = consumeMapper.selectById(consumeId);
        if (c == null) throw new BizException("消课记录不存在");
        if (c.getCanceled() == 1) throw new BizException("该记录已撤销，无需审核");
        if (c.getAuditStatus() != null && c.getAuditStatus() != 0) throw new BizException("该记录已审核过");
        if (pass) {
            c.setAuditStatus(1);
        } else {
            // 驳回：归还课时（对称回滚），保留驳回痕迹
            EduContract contract = contractMapper.selectById(c.getContractId());
            if (contract != null) {
                BigDecimal usedGift = nz(contract.getUsedGift());
                BigDecimal returnGift = usedGift.min(c.getHours());
                contract.setUsedGift(usedGift.subtract(returnGift));
                contract.setUsedPurchased(nz(contract.getUsedPurchased()).subtract(c.getHours().subtract(returnGift)));
                contractMapper.updateById(contract);
            }
            c.setAuditStatus(2);
        }
        c.setAuditor(auditor);
        c.setAuditTime(LocalDateTime.now());
        c.setAuditRemark(remark);
        consumeMapper.updateById(c);
    }

    private BigDecimal nz(BigDecimal v) { return v == null ? BigDecimal.ZERO : v; }

    private Long scheduleTeacherId(Long scheduleId) {
        if (scheduleId == null) return null;
        com.edu.entity.EduSchedule sch = scheduleMapper.selectById(scheduleId);
        return sch == null ? null : sch.getTeacherId();
    }

    private String operatorName() {
        com.edu.security.LoginUser lu = AuthContext.get();
        // 操作人统一记录登录账号用户名（与排课操作日志口径一致）
        return lu == null ? "system" : lu.getUsername();
    }
}
