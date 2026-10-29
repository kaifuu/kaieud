package com.edu.controller;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.edu.common.Log;
import com.edu.common.Result;
import com.edu.common.SafeSort;
import com.edu.entity.SysNotifyConfig;
import com.edu.mapper.SysNotifyConfigMapper;
import com.edu.security.AuthContext;
import com.edu.security.RequiresPerm;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

import java.util.List;

/** 通知配置（通知管理TAB2） */
@RestController
@RequestMapping("/sys/notify-config")
@RequiredArgsConstructor
@RequiresPerm("sys:notify")
public class SysNotifyConfigController {

    private final SysNotifyConfigMapper configMapper;

    @GetMapping("/list")
    public Result<List<SysNotifyConfig>> list(@RequestParam(required = false) String sortBy,
                                              @RequestParam(required = false) String direction) {
        return Result.ok(configMapper.selectList(new LambdaQueryWrapper<SysNotifyConfig>()
                .last(SafeSort.order(sortBy, direction, "id", "id", "name", "eventType", "channel", "enabled"))));
    }

    @PostMapping
    @Log("新增/修改通知配置")
    public Result<Void> save(@RequestBody SysNotifyConfig config) {
        config.setTenantId(AuthContext.get().getTenantId());
        if (config.getId() != null) configMapper.updateById(config);
        else configMapper.insert(config);
        return Result.ok();
    }

    @PostMapping("/{id}/toggle")
    @Log("启停通知配置")
    public Result<Void> toggle(@PathVariable Long id, @RequestParam Integer enabled) {
        SysNotifyConfig c = configMapper.selectById(id);
        if (c == null) return Result.error("配置不存在");
        c.setEnabled(enabled);
        configMapper.updateById(c);
        return Result.ok();
    }

    @DeleteMapping("/{id}")
    @Log("删除通知配置")
    public Result<Void> delete(@PathVariable Long id) {
        configMapper.deleteById(id);
        return Result.ok();
    }
}
