package com.edu.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.edu.common.exception.BizException;
import com.edu.entity.EduAbsence;
import com.edu.entity.EduSchedule;
import com.edu.mapper.EduAbsenceMapper;
import com.edu.mapper.EduScheduleMapper;
import com.edu.security.AuthContext;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.List;

/**
 * 旷课审批流：
 * 批准 → 课次置「旷课已免扣」，不扣课时；
 * 驳回 → 立即扣课（1节，走统一扣课路径，台账+合同+报名口径一致）；
 * 待审批超过72小时 → 定时任务自动扣课。
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class EduAbsenceService {

    private final EduAbsenceMapper absenceMapper;
    private final EduScheduleMapper scheduleMapper;
    private final EduScheduleService scheduleService;

    /** 批准：免扣课 */
    @Transactional
    public void approve(Long id, String remark) {
        EduAbsence a = mustGet(id);
        if (a.getStatus() != 0) throw new BizException("该申请已处理，无需重复审批");
        EduSchedule s = scheduleMapper.selectById(a.getScheduleId());
        if (s != null && s.getStatus() != null && s.getStatus() == 5) {
            s.setStatus(6);
            s.setUpdateTime(LocalDateTime.now());
            scheduleMapper.updateById(s);
        }
        a.setStatus(1);
        a.setApprover(operatorName());
        a.setApproveTime(LocalDateTime.now());
        a.setApproveRemark(remark);
        absenceMapper.updateById(a);
    }

    /** 驳回：立即扣课（扣课记录同步进入课时管理）；扣课失败则报错，申请保持待审批 */
    @Transactional
    public void reject(Long id, String remark) {
        EduAbsence a = mustGet(id);
        if (a.getStatus() != 0) throw new BizException("该申请已处理，无需重复审批");
        String fail = deduct(a);
        if (fail != null) throw new BizException(fail); // 事务回滚，课次/台账不落库
        a.setStatus(2);
        a.setApprover(operatorName());
        a.setApproveTime(LocalDateTime.now());
        a.setApproveRemark(remark);
        a.setDeductTime(LocalDateTime.now());
        absenceMapper.updateById(a);
    }

    /** 待审批超过72小时：每小时扫描一次，自动扣课 */
    @Scheduled(cron = "0 0 * * * ?")
    @Transactional
    public void autoDeductTimeout() {
        List<EduAbsence> timeout = absenceMapper.selectList(new LambdaQueryWrapper<EduAbsence>()
                .eq(EduAbsence::getStatus, 0)
                .le(EduAbsence::getDeadline, LocalDateTime.now()));
        for (EduAbsence a : timeout) {
            String fail = deduct(a);
            a.setStatus(3);
            a.setApprover("系统自动");
            a.setApproveTime(LocalDateTime.now());
            a.setApproveRemark(fail == null ? "超过72小时未审批，系统自动扣课"
                    : ("超时自动扣课失败：" + fail));
            if (fail == null) a.setDeductTime(LocalDateTime.now());
            absenceMapper.updateById(a);
            if (fail != null) log.warn("旷课申请[{}]自动扣课失败：{}", a.getId(), fail);
        }
    }

    /** 执行扣课：课次置「旷课已扣课」，走统一扣课路径 */
    private String deduct(EduAbsence a) {
        try {
            EduSchedule s = scheduleMapper.selectById(a.getScheduleId());
            if (s == null) return "课次不存在";
            if (s.getStatus() == null || s.getStatus() != 5) return "课次状态已变化，无法扣课";
            scheduleService.deductHours(s, 1, "旷课");
            s.setStatus(7);
            s.setUpdateTime(LocalDateTime.now());
            scheduleMapper.updateById(s);
            return null;
        } catch (BizException e) {
            return e.getMessage();
        }
    }

    private EduAbsence mustGet(Long id) {
        EduAbsence a = absenceMapper.selectById(id);
        if (a == null) throw new BizException("旷课申请不存在");
        return a;
    }

    private String operatorName() {
        com.edu.security.LoginUser lu = AuthContext.get();
        return lu == null ? "system" : lu.getRealName();
    }
}
