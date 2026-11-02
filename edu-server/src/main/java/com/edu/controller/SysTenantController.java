package com.edu.controller;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.edu.common.SafeSort;
import com.edu.common.Log;
import com.edu.common.PageResult;
import com.edu.common.Result;
import com.edu.common.exception.BizException;
import com.edu.entity.SysTenant;
import com.edu.mapper.SysPurgeLogMapper;
import com.edu.mapper.SysTenantMapper;
import com.edu.security.AuthContext;
import com.edu.security.LoginUser;
import com.edu.security.PurgeSecretUtil;
import com.edu.security.RequiresPerm;
import com.edu.service.TenantProvisionService;
import lombok.RequiredArgsConstructor;
import org.springframework.util.StringUtils;
import org.springframework.web.bind.annotation.*;

import java.util.List;

/**
 * 租户管理（仅平台管理员）：每个企业租户一套独立的 组织/用户/角色/菜单，
 * 创建租户即复制平台模板并生成租户管理员账号（账号=租户编码，初始密码 123456）。
 */
@RestController
@RequestMapping("/sys/tenant")
@RequiredArgsConstructor
@RequiresPerm("sys:tenant")
public class SysTenantController {

    private final SysTenantMapper tenantMapper;
    private final SysPurgeLogMapper purgeLogMapper;
    private final TenantProvisionService provisionService;
    private final PurgeSecretUtil purgeSecretUtil;

    /** 仅平台账号（无租户）可操作平台运营（企业/品牌/字典），企业管理员不可越权 */
    private void platformOnly() {
        Long tid = AuthContext.get().getTenantId();
        if (tid != null && tid != 0) throw new BizException("仅平台运营可执行该操作");
    }

    @GetMapping("/page")
    public Result<PageResult<SysTenant>> page(@RequestParam(defaultValue = "1") long page,
                                              @RequestParam(defaultValue = "10") long size,
                                              @RequestParam(required = false) String keyword,
 @RequestParam(required = false) String sortBy,
 @RequestParam(required = false) String direction) {
        platformOnly();   // 企业视角不可见其他公司名单
        LambdaQueryWrapper<SysTenant> qw = new LambdaQueryWrapper<SysTenant>()
                .like(StringUtils.hasText(keyword), SysTenant::getName, keyword).or()
                .like(StringUtils.hasText(keyword), SysTenant::getCode, keyword)
                .last(SafeSort.order(sortBy, direction, "id", "id", "name", "code", "createTime"));
        Page<SysTenant> p = tenantMapper.selectPage(new Page<>(page, size), qw);
        return Result.ok(PageResult.of(p.getTotal(), p.getRecords()));
    }

    @GetMapping("/list")
    public Result<?> list() {
        platformOnly();
        return Result.ok(tenantMapper.selectList(
                new LambdaQueryWrapper<SysTenant>().eq(SysTenant::getStatus, 1)));
    }

    /** 新增租户：编码即租户管理员账号，创建后自动开通整套功能（模板复制+管理员） */
    @PostMapping
    @Log("新增租户")
    public Result<SysTenant> save(@RequestBody SysTenant tenant) {
        platformOnly();
        if (!StringUtils.hasText(tenant.getCode()))
            return Result.error("租户编码不能为空（将作为租户管理员账号）");
        Long cnt = tenantMapper.selectCount(new LambdaQueryWrapper<SysTenant>()
                .eq(SysTenant::getCode, tenant.getCode()));
        if (cnt > 0) throw new BizException("租户编码已存在");
        if (tenant.getStatus() == null) tenant.setStatus(1);
        tenantMapper.insert(tenant);
        provisionService.provision(tenant.getId());
        return Result.ok(tenantMapper.selectById(tenant.getId()));
    }

    @PutMapping
    @Log("修改租户")
    public Result<Void> update(@RequestBody SysTenant tenant) {
        platformOnly();
        tenant.setCode(null); // 编码=管理员账号，不允许改
        tenantMapper.updateById(tenant);
        return Result.ok();
    }

    /** 删除租户（软删除可恢复）：停用其全部用户，数据保留；回收站可一键恢复 */
    @DeleteMapping("/{id}")
    @Log("删除租户")
    public Result<Void> delete(@PathVariable Long id) {
        platformOnly();
        if (id == 0L) throw new BizException("平台租户不允许删除");
        if (id == 1L) throw new BizException("默认租户不允许删除");
        provisionService.purge(id);
        tenantMapper.deleteById(id);   // @TableLogic 软删除
        return Result.ok();
    }

    /** 恢复租户：取消软删 + 启用用户 + 幂等补齐默认数据（菜单/角色/管理员） */
    @PostMapping("/{id}/restore")
    @Log("恢复租户")
    public Result<Void> restore(@PathVariable Long id) {
        platformOnly();
        if (id == 0L) throw new BizException("平台租户无需恢复，请检查数据");
        provisionService.restore(id);
        return Result.ok();
    }

    /**
     * 物理删除企业数据（不可恢复）：需输入超级秘钥（yml 3DES 密文存储，运行时解密比对）；
     * 仅回收站中的企业可执行；操作写入 sys_purge_log 审计（谁/何时/清了哪个企业/各表行数）。
     */
    @PostMapping("/{id}/purge")
    @Log("物理删除企业数据")
    public Result<String> purge(@PathVariable Long id, @RequestBody PurgeDTO dto) {
        platformOnly();
        if (!purgeSecretUtil.matches(dto == null ? null : dto.getSecret()))
            return Result.error("超级秘钥不正确");
        LoginUser lu = AuthContext.get();
        String detail = provisionService.physicalPurge(id,
                lu.getUsername() + "/" + lu.getRealName());
        return Result.ok(detail);
    }

    /** 物理删除审计记录（清除历史） */
    @GetMapping("/purge-logs")
    public Result<List<com.edu.entity.SysPurgeLog>> purgeLogs() {
        platformOnly();
        return Result.ok(purgeLogMapper.selectList(
                new LambdaQueryWrapper<com.edu.entity.SysPurgeLog>()
                        .orderByDesc(com.edu.entity.SysPurgeLog::getId)));
    }

    @lombok.Data
    public static class PurgeDTO {
        /** 超级秘钥（前端弹窗输入，不落任何日志） */
        private String secret;
    }

    /** 回收站：已软删除的租户列表 */
    @GetMapping("/recycle")
    public Result<List<SysTenant>> recycle() {
        platformOnly();
        return Result.ok(tenantMapper.deletedList());
    }

    @PostMapping("/{id}/toggle")
    @Log("启用/禁用租户")
    public Result<Void> toggle(@PathVariable Long id, @RequestParam boolean enable) {
        platformOnly();
        SysTenant tenant = tenantMapper.selectById(id);
        if (tenant == null) throw new BizException("租户不存在");
        tenant.setStatus(enable ? 1 : 0);
        tenantMapper.updateById(tenant);
        return Result.ok();
    }

    /** 开通/重新初始化租户（存量租户补开通用，幂等） */
    @PostMapping("/{id}/provision")
    @Log("开通租户功能")
    public Result<Void> provision(@PathVariable Long id) {
        platformOnly();
        provisionService.provision(id);
        return Result.ok();
    }
}
