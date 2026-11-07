package com.edu.controller;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.edu.common.SafeSort;
import com.edu.common.Log;
import com.edu.common.PageResult;
import com.edu.common.Result;
import com.edu.entity.EduStudent;
import com.edu.mapper.EduStudentMapper;
import com.edu.security.AuthContext;
import com.edu.security.RequiresPerm;
import lombok.RequiredArgsConstructor;
import org.springframework.util.StringUtils;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

/** 学生管理 */
@RestController
@RequestMapping("/edu/student")
@RequiredArgsConstructor
@RequiresPerm("edu:student")
public class EduStudentController {

    private final EduStudentMapper studentMapper;
    private final com.edu.mapper.EduStudentLogMapper logMapper;
    private final com.edu.service.DataScopeService dataScopeService;
    private final com.edu.mapper.SysUserMapper userMapper;

    /** 学员下拉列表（合同/排课/消课等模块选择用） */
    @GetMapping("/list")
    public Result<?> list() {
        List<EduStudent> list = studentMapper.selectList(new LambdaQueryWrapper<EduStudent>()
                .eq(EduStudent::getDeleted, 0).orderByDesc(EduStudent::getId));
        fillEffectivePhone(list);
        return Result.ok(list);
    }

    /**
     * 填充有效联系电话 phone：家长电话 → 备用电话 → 关联账号(sys_user)手机号。
     * 用户管理补录的手机号在账号上，档案未同步时以此兜底，搜索与展示不再显示"无手机号"。
     */
    private void fillEffectivePhone(List<EduStudent> list) {
        List<Long> userIds = new java.util.ArrayList<>();
        for (EduStudent s : list)
            if (s.getUserId() != null && !StringUtils.hasText(s.getParentPhone())
                    && !StringUtils.hasText(s.getBackupPhone())) userIds.add(s.getUserId());
        Map<Long, String> userPhone = userIds.isEmpty() ? java.util.Collections.emptyMap()
                : userMapper.selectBatchIds(userIds).stream()
                    .filter(u -> StringUtils.hasText(u.getPhone()))
                    .collect(java.util.stream.Collectors.toMap(
                            com.edu.entity.SysUser::getId, com.edu.entity.SysUser::getPhone, (a, b) -> a));
        for (EduStudent s : list)
            s.setPhone(StringUtils.hasText(s.getParentPhone()) ? s.getParentPhone()
                    : StringUtils.hasText(s.getBackupPhone()) ? s.getBackupPhone()
                    : s.getUserId() == null ? null : userPhone.get(s.getUserId()));
    }

    /** 学员变更记录（供用户管理详情等扩展） */
    @GetMapping("/{id}/logs")
    public Result<?> logs(@PathVariable Long id) {
        return Result.ok(logMapper.selectList(
                new LambdaQueryWrapper<com.edu.entity.EduStudentLog>()
                        .eq(com.edu.entity.EduStudentLog::getStudentId, id)
                        .orderByDesc(com.edu.entity.EduStudentLog::getId)));
    }
}
