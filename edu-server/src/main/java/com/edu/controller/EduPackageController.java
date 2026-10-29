package com.edu.controller;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.edu.common.Log;
import com.edu.common.PageResult;
import com.edu.common.Result;
import com.edu.common.SafeSort;
import com.edu.entity.EduPackage;
import com.edu.mapper.EduPackageMapper;
import com.edu.security.AuthContext;
import com.edu.security.RequiresPerm;
import lombok.RequiredArgsConstructor;
import org.springframework.util.StringUtils;
import org.springframework.web.bind.annotation.*;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

/** 课时套餐库：套餐是模板，合同引用后可一客一价 */
@RestController
@RequestMapping("/edu/package")
@RequiredArgsConstructor
@RequiresPerm("edu:package")
public class EduPackageController {

    private final EduPackageMapper packageMapper;
    private final com.edu.mapper.EduContractMapper contractMapper;
    private final com.edu.mapper.EduStudentMapper studentMapper;

    /** 截止日期已过的启用套餐自动下线 */
    private void autoDisableExpired() {
        String today = java.time.LocalDate.now().toString();
        java.util.List<EduPackage> expired = packageMapper.selectList(
                new LambdaQueryWrapper<EduPackage>()
                        .eq(EduPackage::getEnabled, 1)
                        .isNotNull(EduPackage::getEndDate)
                        .lt(EduPackage::getEndDate, today));
        for (EduPackage p : expired) {
            p.setEnabled(0);
            packageMapper.updateById(p);
        }
    }

    @GetMapping("/page")
    public Result<PageResult<EduPackage>> page(@RequestParam(defaultValue = "1") long page,
                                               @RequestParam(defaultValue = "10") long size,
                                               @RequestParam(required = false) String keyword,
                                               @RequestParam(required = false) Integer classType,
                                               @RequestParam(required = false) String sortBy,
                                               @RequestParam(required = false) String direction) {
        autoDisableExpired();
        LambdaQueryWrapper<EduPackage> qw = new LambdaQueryWrapper<EduPackage>()
                .like(StringUtils.hasText(keyword), EduPackage::getName, keyword)
                .eq(classType != null, EduPackage::getClassType, classType)
                .eq(EduPackage::getDeleted, 0)
                ;
        qw.last(SafeSort.order(sortBy, direction, "id", "id", "name", "unitPrice", "hours", "createTime"));
        Page<EduPackage> p = packageMapper.selectPage(new Page<>(page, size), qw);
        return Result.ok(PageResult.of(p.getTotal(), p.getRecords()));
    }

    /** 合同选择套餐模板用：仅返回启用且未过期的套餐 */
    @GetMapping("/list")
    public Result<?> list() {
        autoDisableExpired();
        String today = java.time.LocalDate.now().toString();
        return Result.ok(packageMapper.selectList(new LambdaQueryWrapper<EduPackage>()
                .eq(EduPackage::getEnabled, 1).eq(EduPackage::getDeleted, 0)
                .and(w -> w.isNull(EduPackage::getEndDate).or().ge(EduPackage::getEndDate, today))));
    }

    /** 删除前检查合同引用：被引用的套餐只能下线，不能删除 */
    @DeleteMapping("/{id}")
    @Log("删除套餐")
    public Result<Void> delete(@PathVariable Long id) {
        Long refs = contractMapper.selectCount(new LambdaQueryWrapper<com.edu.entity.EduContract>()
                .eq(com.edu.entity.EduContract::getPackageId, id)
                .eq(com.edu.entity.EduContract::getDeleted, 0));
        if (refs > 0) return Result.error("该套餐已被 " + refs + " 份合同引用，不能删除，只能下线");
        EduPackage p = packageMapper.selectById(id);
        if (p != null) { p.setDeleted(1); packageMapper.updateById(p); }
        return Result.ok();
    }

    /** 套餐引用的合同列表（生效/截止日期等信息） */
    @GetMapping("/{id}/contracts")
    public Result<java.util.List<Map<String, Object>>> contracts(@PathVariable Long id) {
        List<com.edu.entity.EduContract> contracts = contractMapper.selectList(
                new LambdaQueryWrapper<com.edu.entity.EduContract>()
                        .eq(com.edu.entity.EduContract::getPackageId, id)
                        .orderByDesc(com.edu.entity.EduContract::getId));
        List<Map<String, Object>> result = new java.util.ArrayList<>();
        for (com.edu.entity.EduContract c : contracts) {
            Map<String, Object> m = new HashMap<>();
            m.put("contractNo", c.getContractNo());
            com.edu.entity.EduStudent st = studentMapper.selectById(c.getStudentId());
            m.put("studentName", st == null ? "-" : st.getName());
            m.put("classType", c.getClassType());
            m.put("purchasedHours", c.getPurchasedHours());
            m.put("giftHours", c.getGiftHours());
            m.put("unitPrice", c.getUnitPrice());
            m.put("totalAmount", c.getTotalAmount());
            m.put("startDate", c.getStartDate() == null ? "-" : c.getStartDate().toString());
            m.put("endDate", c.getEndDate() == null ? "-" : c.getEndDate().toString());
            m.put("status", c.getStatus());
            result.add(m);
        }
        return Result.ok(result);
    }

    @PostMapping
    @Log("新增/修改套餐")
    public Result<Void> save(@RequestBody EduPackage pkg) {
        pkg.setTenantId(AuthContext.get().getTenantId());
        pkg.setDeleted(0);
        // 截止日期已过的套餐不允许启用
        if (pkg.getEnabled() != null && pkg.getEnabled() == 1
                && pkg.getEndDate() != null
                && pkg.getEndDate().isBefore(java.time.LocalDate.now())) {
            return Result.error("截止日期已过，不能启用");
        }
        if (pkg.getId() != null) packageMapper.updateById(pkg);
        else {
            pkg.setCreateBy(AuthContext.get().getRealName());
            packageMapper.insert(pkg);
        }
        return Result.ok();
    }
}
