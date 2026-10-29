package com.edu.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.edu.common.exception.BizException;
import com.edu.entity.EduEnroll;
import com.edu.entity.EduLeave;
import com.edu.entity.EduSchedule;
import com.edu.mapper.EduEnrollMapper;
import com.edu.mapper.EduLeaveMapper;
import com.edu.mapper.EduScheduleMapper;
import com.edu.security.AuthContext;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

/** 学生请假：申请 -> 审批(取消课次) -> 可补课时 */
@Service
@RequiredArgsConstructor
public class EduLeaveService {

    private final EduLeaveMapper leaveMapper;
    private final EduScheduleMapper scheduleMapper;
    private final EduEnrollMapper enrollMapper;

    /** 提交请假申请（学生或代为申请） */
    @Transactional
    public void apply(Long studentId, Long scheduleId, String reason) {
        EduSchedule schedule = scheduleMapper.selectById(scheduleId);
        if (schedule == null) throw new BizException("课次不存在");
        EduLeave leave = new EduLeave();
        leave.setTenantId(AuthContext.get().getTenantId());
        leave.setStudentId(studentId);
        leave.setScheduleId(scheduleId);
        leave.setReason(reason);
        leave.setStatus(0);
        leaveMapper.insert(leave);
    }

    /** 审批：同意后自动取消对应课次；返回该学生对应的报名ID用于补课时 */
    @Transactional
    public void approve(Long id, boolean pass) {
        EduLeave leave = mustGet(id);
        if (leave.getStatus() != 0) throw new BizException("该申请已审批");
        leave.setStatus(pass ? 1 : 2);
        leaveMapper.updateById(leave);
        if (pass) {
            EduSchedule s = scheduleMapper.selectById(leave.getScheduleId());
            if (s != null && s.getStatus() == 0) {
                s.setStatus(2);
                s.setCancelReason("学生请假，待补课");
                scheduleMapper.updateById(s);
            }
        }
    }

    private EduLeave mustGet(Long id) {
        EduLeave l = leaveMapper.selectById(id);
        if (l == null) throw new BizException("请假申请不存在");
        return l;
    }
}
