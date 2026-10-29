package com.edu.controller;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.edu.common.Log;
import com.edu.common.Result;
import com.edu.entity.SysConfig;
import com.edu.mapper.SysConfigMapper;
import com.edu.security.RequiresPerm;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

import java.util.HashMap;
import java.util.Arrays;
import java.util.List;
import java.util.Map;

/** 系统基础设置 */
@RestController
@RequestMapping("/sys/config")
@RequiredArgsConstructor
public class SysConfigController {

    private final SysConfigMapper configMapper;

    /** 全量配置（基础设置页面用） */
    @GetMapping("/all")
    @RequiresPerm("sys:config")
    public Result<Map<String, String>> all() {
        Map<String, String> result = new HashMap<>();
        configMapper.selectList(null).forEach((c) -> result.put(c.getConfigKey(), c.getConfigValue()));
        return Result.ok(result);
    }

    /** 公开配置：登录页渲染用（仅返回安全白名单键），无需登录 */
    @GetMapping("/public")
    public Result<Map<String, String>> publicConfig() {
        Map<String, String> result = new HashMap<>();
        configMapper.selectList(new LambdaQueryWrapper<SysConfig>().in(SysConfig::getConfigKey,
                Arrays.asList("company_name", "icp_no", "login_title", "logo_url")))
                .forEach((c) -> result.put(c.getConfigKey(), c.getConfigValue()));
        return Result.ok(result);
    }

    /** 批量保存（基础设置页面整体提交） */
    @PostMapping("/save")
    @RequiresPerm("sys:config")
    @Log("保存基础设置")
    public Result<Void> save(@RequestBody Map<String, String> configs) {
        for (Map.Entry<String, String> e : configs.entrySet()) {
            SysConfig exist = configMapper.selectOne(new LambdaQueryWrapper<SysConfig>()
                    .eq(SysConfig::getConfigKey, e.getKey()));
            if (exist != null) {
                exist.setConfigValue(e.getValue());
                configMapper.updateById(exist);
            } else {
                SysConfig c = new SysConfig();
                c.setConfigKey(e.getKey());
                c.setConfigValue(e.getValue());
                configMapper.insert(c);
            }
        }
        return Result.ok();
    }
}
