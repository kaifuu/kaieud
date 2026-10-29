package com.edu.controller;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.edu.common.Log;
import com.edu.common.PageResult;
import com.edu.common.Result;
import com.edu.common.SafeSort;
import com.edu.entity.SysNotice;
import com.edu.mapper.SysNoticeMapper;
import com.edu.security.AuthContext;
import com.edu.security.RequiresPerm;
import lombok.RequiredArgsConstructor;
import org.springframework.util.StringUtils;
import org.springframework.web.bind.annotation.*;

import java.time.LocalDateTime;
import java.util.List;

/** 通知管理：通知列表 + 右上角通知铃铛 */
@RestController
@RequestMapping("/sys/notice")
@RequiredArgsConstructor
public class SysNoticeController {

    private final SysNoticeMapper noticeMapper;

    /** 通知列表分页（通知管理TAB1） */
    @GetMapping("/page")
    @RequiresPerm("sys:notify")
    public Result<PageResult<SysNotice>> page(@RequestParam(defaultValue = "1") long page,
                                              @RequestParam(defaultValue = "10") long size,
                                              @RequestParam(required = false) String keyword,
                                              @RequestParam(required = false) Integer noticeType,
                                              @RequestParam(required = false) Integer status,
                                              @RequestParam(required = false) String sortBy,
                                              @RequestParam(required = false) String direction) {
        Page<SysNotice> p = noticeMapper.selectPage(new Page<>(page, size),
                new LambdaQueryWrapper<SysNotice>()
                        .like(StringUtils.hasText(keyword), SysNotice::getTitle, keyword)
                        .eq(noticeType != null, SysNotice::getNoticeType, noticeType)
                        .eq(status != null, SysNotice::getStatus, status)
                        .last(SafeSort.order(sortBy, direction, "id", "id", "title", "level", "status", "publishTime", "createTime")));
        return Result.ok(PageResult.of(p.getTotal(), p.getRecords()));
    }

    /** 右上角铃铛：当前用户最近已发布通知（按目标角色过滤） */
    @GetMapping("/recent")
    public Result<List<SysNotice>> recent(@RequestParam(defaultValue = "8") int limit) {
        String role = roleCode();
        List<SysNotice> list = noticeMapper.selectList(new LambdaQueryWrapper<SysNotice>()
                .eq(SysNotice::getStatus, 1)
                .and(w -> w.eq(SysNotice::getTarget, "ALL").or().eq(SysNotice::getTarget, role))
                .orderByDesc(SysNotice::getPublishTime)
                .last("LIMIT " + Math.min(limit, 20)));
        return Result.ok(list);
    }

    @PostMapping
    @RequiresPerm("sys:notify")
    @Log("新增/修改通知")
    public Result<Void> save(@RequestBody SysNotice notice) {
        notice.setTenantId(AuthContext.get().getTenantId());
        if (notice.getId() != null && notice.getStatus() != null && notice.getStatus() == 1
                && notice.getPublishTime() == null) {
            notice.setPublishTime(LocalDateTime.now());
        }
        if (notice.getId() != null) noticeMapper.updateById(notice);
        else noticeMapper.insert(notice);
        return Result.ok();
    }

    /** 发布通知 */
    @PostMapping("/{id}/publish")
    @RequiresPerm("sys:notify")
    @Log("发布通知")
    public Result<Void> publish(@PathVariable Long id) {
        SysNotice n = noticeMapper.selectById(id);
        if (n == null) return Result.error("通知不存在");
        n.setStatus(1);
        n.setPublishTime(LocalDateTime.now());
        noticeMapper.updateById(n);
        return Result.ok();
    }

    @DeleteMapping("/{id}")
    @RequiresPerm("sys:notify")
    @Log("删除通知")
    public Result<Void> delete(@PathVariable Long id) {
        noticeMapper.deleteById(id);
        return Result.ok();
    }

    private String roleCode() {
        Integer userType = AuthContext.get().getUserType();
        switch (userType == null ? 0 : userType) {
            case 2: return "EDU_ADMIN";
            case 3: return "CLASS_TEACHER";
            case 4: return "TEACHER";
            case 5: return "STUDENT";
            default: return "ALL";
        }
    }
}
