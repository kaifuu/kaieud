package com.edu.controller;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.edu.common.Log;
import com.edu.common.Result;
import com.edu.entity.SysOrg;
import com.edu.mapper.SysOrgMapper;
import com.edu.security.AuthContext;
import com.edu.security.RequiresPerm;
import lombok.Data;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

/** 组织(校区)管理 */
@RestController
@RequestMapping("/sys/org")
@RequiredArgsConstructor
@RequiresPerm("sys:org")
public class SysOrgController {

    private final SysOrgMapper orgMapper;

    @Data
    public static class OrgNode extends SysOrg {
        private List<OrgNode> children;
    }

    @GetMapping("/tree")
    public Result<List<OrgNode>> tree() {
        Long tenantId = AuthContext.get().getTenantId();
        boolean scoped = tenantId != null && tenantId != 0; // 超管查看全部
        List<SysOrg> all = orgMapper.selectList(new LambdaQueryWrapper<SysOrg>()
                .eq(scoped, SysOrg::getTenantId, tenantId).orderByAsc(SysOrg::getSort));
        Map<Long, List<SysOrg>> byParent = all.stream()
                .collect(Collectors.groupingBy(SysOrg::getParentId));
        return Result.ok(build(all, byParent, 0L));
    }

    private List<OrgNode> build(List<SysOrg> all, Map<Long, List<SysOrg>> byParent, Long parentId) {
        List<OrgNode> nodes = new ArrayList<>();
        for (SysOrg o : byParent.getOrDefault(parentId, new ArrayList<>())) {
            OrgNode n = new OrgNode();
            n.setId(o.getId()); n.setTenantId(o.getTenantId()); n.setParentId(o.getParentId());
            n.setName(o.getName()); n.setLeader(o.getLeader()); n.setPhone(o.getPhone());
            n.setSort(o.getSort()); n.setStatus(o.getStatus()); n.setCreateTime(o.getCreateTime());
            n.setChildren(build(all, byParent, o.getId()));
            nodes.add(n);
        }
        return nodes;
    }

    @PostMapping
    @Log("新增组织")
    public Result<Void> save(@RequestBody SysOrg org) {
        org.setTenantId(AuthContext.get().getTenantId());
        orgMapper.insert(org);
        return Result.ok();
    }

    @PutMapping
    @Log("修改组织")
    public Result<Void> update(@RequestBody SysOrg org) {
        orgMapper.updateById(org);
        return Result.ok();
    }

    @DeleteMapping("/{id}")
    @Log("删除组织")
    public Result<Void> delete(@PathVariable Long id) {
        Long cnt = orgMapper.selectCount(new LambdaQueryWrapper<SysOrg>().eq(SysOrg::getParentId, id));
        if (cnt > 0) return Result.error("存在下级组织，不允许删除");
        orgMapper.deleteById(id);
        return Result.ok();
    }
}
