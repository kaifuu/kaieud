package com.edu.controller;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.edu.common.Log;
import com.edu.common.PageResult;
import com.edu.common.Result;
import com.edu.common.SafeSort;
import com.edu.entity.EduConsume;
import com.edu.entity.EduSchedule;
import com.edu.mapper.EduConsumeMapper;
import com.edu.mapper.EduScheduleMapper;
import com.edu.security.RequiresPerm;
import com.edu.service.EduConsumeService;
import org.springframework.util.StringUtils;
import lombok.Data;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

import java.math.BigDecimal;
import java.util.List;

/** 签到消课：一键消课（绑定排课）、手动消课、撤销消课（留痕回滚） */
@RestController
@RequestMapping("/edu/consume")
@RequiredArgsConstructor
@RequiresPerm("edu:consume")
public class EduConsumeController {

    private final EduConsumeMapper consumeMapper;
    private final EduScheduleMapper scheduleMapper;
    private final EduConsumeService consumeService;
    private final com.edu.service.DataScopeService dataScopeService;

    @GetMapping("/page")
    public Result<PageResult<EduConsume>> page(@RequestParam(defaultValue = "1") long page,
                                               @RequestParam(defaultValue = "10") long size,
                                               @RequestParam(required = false) Long studentId,
                                               @RequestParam(required = false) Long teacherId,
                                               @RequestParam(required = false) Long courseId,
                                               @RequestParam(required = false) String begin,
                                               @RequestParam(required = false) String end,
                                               @RequestParam(required = false) Integer auditStatus,
                                               @RequestParam(required = false) String sortBy,
                                               @RequestParam(required = false) String direction) {
        LambdaQueryWrapper<EduConsume> qw = new LambdaQueryWrapper<EduConsume>()
                .eq(studentId != null, EduConsume::getStudentId, studentId)
                .eq(teacherId != null, EduConsume::getTeacherId, teacherId)
                .eq(courseId != null, EduConsume::getCourseId, courseId)
                .ge(StringUtils.hasText(begin), EduConsume::getCreateTime, begin + " 00:00:00")
                .le(StringUtils.hasText(end), EduConsume::getCreateTime, end + " 23:59:59")
                .eq(auditStatus != null, EduConsume::getAuditStatus, auditStatus)
                ;
        dataScopeService.applyByStudent(qw, com.edu.security.AuthContext.get(), "student_id");
        qw.last(SafeSort.order(sortBy, direction, "id", "id", "hours", "createTime"));
        Page<EduConsume> p = consumeMapper.selectPage(new Page<>(page, size), qw);
        return Result.ok(PageResult.of(p.getTotal(), p.getRecords()));
    }

    /** 课消审核DTO */
    @Data
    public static class AuditDTO {
        private Boolean pass;
        private String remark;
    }

    /** 课消审核（总部教务/运营）：通过=计入成本报表；驳回=归还课时并标记 */
    @PostMapping("/{id}/audit")
    @Log(value = "课消审核", type = 0)
    @RequiresPerm("edu:consume:audit")
    public Result<Void> audit(@PathVariable Long id, @RequestBody AuditDTO dto) {
        com.edu.security.LoginUser lu = com.edu.security.AuthContext.get();
        consumeService.audit(id, dto != null && Boolean.TRUE.equals(dto.getPass()),
                dto == null ? null : dto.getRemark(), lu == null ? "system" : lu.getRealName());
        return Result.ok();
    }

    @Data
    public static class ConsumeDTO {
        private Long contractId;
        private Long scheduleId;
        private Long teacherId;
        private BigDecimal hours;
        private Integer consumeType;
    }

    /** 手动消课（无排课补课场景） */
    @PostMapping("/manual")
    @Log("手动消课")
    public Result<EduConsume> manual(@RequestBody ConsumeDTO dto) {
        return Result.ok(consumeService.consume(dto.getContractId(), dto.getScheduleId(),
                dto.getHours(), dto.getConsumeType(), dto.getTeacherId()));
    }

    /** 一键签到消课：按排课记录消课，班课对班内所有在读合同逐一消课 */
    @PostMapping("/sign/{scheduleId}")
    @Log("排课一键签到消课")
    public Result<Integer> sign(@PathVariable Long scheduleId,
                                @RequestParam(required = false) BigDecimal hours) {
        EduSchedule sch = scheduleMapper.selectById(scheduleId);
        if (sch == null) return Result.error("排课记录不存在");
        if (sch.getStatus() == 2) return Result.error("该排课已取消，不能消课");
        int count = consumeService.signBySchedule(scheduleId, hours == null ? BigDecimal.ONE : hours);
        return Result.ok(count);
    }

    /** 撤销消课：归还课时并留痕 */
    @PostMapping("/{id}/cancel")
    @Log("撤销消课")
    public Result<Void> cancel(@PathVariable Long id) {
        consumeService.cancel(id);
        return Result.ok();
    }

    @GetMapping("/{id}")
    public Result<EduConsume> get(@PathVariable Long id) {
        return Result.ok(consumeMapper.selectById(id));
    }
}
