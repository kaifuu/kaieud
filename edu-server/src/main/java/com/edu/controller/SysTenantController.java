package com.edu.controller;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.edu.common.SafeSort;
import com.edu.common.Log;
import com.edu.common.PageResult;
import com.edu.common.Result;
import com.edu.common.exception.BizException;
import com.edu.entity.SysTenant;
import com.edu.mapper.SysTenantMapper;
import com.edu.security.RequiresPerm;
import lombok.RequiredArgsConstructor;
import org.springframework.util.StringUtils;
import org.springframework.web.bind.annotation.*;

/** 租户管理（仅超级管理员） */
@RestController
@RequestMapping("/sys/tenant")
@RequiredArgsConstructor
@RequiresPerm("sys:tenant")
public class SysTenantController {

    private final SysTenantMapper tenantMapper;

    @GetMapping("/page")
    public Result<PageResult<SysTenant>> page(@RequestParam(defaultValue = "1") long page,
                                              @RequestParam(defaultValue = "10") long size,
                                              @RequestParam(required = false) String keyword,
 @RequestParam(required = false) String sortBy,
 @RequestParam(required = false) String direction) {
        LambdaQueryWrapper<SysTenant> qw = new LambdaQueryWrapper<SysTenant>()
                .like(StringUtils.hasText(keyword), SysTenant::getName, keyword).or()
                .like(StringUtils.hasText(keyword), SysTenant::getCode, keyword)
                .last(SafeSort.order(sortBy, direction, "id", "id", "name", "code", "createTime"));
        Page<SysTenant> p = tenantMapper.selectPage(new Page<>(page, size), qw);
        return Result.ok(PageResult.of(p.getTotal(), p.getRecords()));
    }

    @GetMapping("/list")
    public Result<?> list() {
        return Result.ok(tenantMapper.selectList(
                new LambdaQueryWrapper<SysTenant>().eq(SysTenant::getStatus, 1)));
    }

    @PostMapping
    @Log("新增租户")
    public Result<Void> save(@RequestBody SysTenant tenant) {
        Long cnt = tenantMapper.selectCount(new LambdaQueryWrapper<SysTenant>()
                .eq(SysTenant::getCode, tenant.getCode()));
        if (cnt > 0) throw new BizException("租户编码已存在");
        tenantMapper.insert(tenant);
        return Result.ok();
    }

    @PutMapping
    @Log("修改租户")
    public Result<Void> update(@RequestBody SysTenant tenant) {
        tenantMapper.updateById(tenant);
        return Result.ok();
    }

    @DeleteMapping("/{id}")
    @Log("删除租户")
    public Result<Void> delete(@PathVariable Long id) {
        if (id == 1L) throw new BizException("默认租户不允许删除");
        tenantMapper.deleteById(id);
        return Result.ok();
    }
}
