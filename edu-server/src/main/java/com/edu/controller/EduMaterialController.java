package com.edu.controller;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.edu.common.Log;
import com.edu.common.RepeatGuard;
import com.edu.common.Result;
import com.edu.entity.EduMaterial;
import com.edu.entity.EduMaterialLog;
import com.edu.entity.EduStudent;
import com.edu.mapper.EduMaterialLogMapper;
import com.edu.mapper.EduMaterialMapper;
import com.edu.mapper.EduStudentMapper;
import com.edu.security.AuthContext;
import com.edu.security.RequiresPerm;
import lombok.Data;
import lombok.RequiredArgsConstructor;
import org.springframework.util.StringUtils;
import org.springframework.web.bind.annotation.*;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * 教材杂费（办理中心-教材杂费）：项目维护 + 购买办理。
 * 复用办理中心权限（edu:enroll）。
 */
@RestController
@RequestMapping("/edu/material")
@RequiredArgsConstructor
@RequiresPerm("edu:enroll")
public class EduMaterialController {

    private final EduMaterialMapper materialMapper;
    private final EduMaterialLogMapper logMapper;
    private final EduStudentMapper studentMapper;
    private final RepeatGuard repeatGuard;

    /** 项目列表（上架项目，办理选择用） */
    @GetMapping("/list")
    public Result<List<EduMaterial>> list() {
        return Result.ok(materialMapper.selectList(new LambdaQueryWrapper<EduMaterial>()
                .eq(EduMaterial::getStatus, 1)
                .orderByAsc(EduMaterial::getSort).orderByAsc(EduMaterial::getId)));
    }

    /** 新增/编辑项目（选择弹窗内快捷维护） */
    @PostMapping("/save")
    @Log("保存教材杂费项目")
    public Result<EduMaterial> save(@RequestBody MaterialDTO dto) {
        if (!StringUtils.hasText(dto.getName())) return Result.error("请填写项目名称");
        if (dto.getPrice() == null || dto.getPrice().compareTo(BigDecimal.ZERO) < 0)
            return Result.error("价格不能为空或为负");
        EduMaterial m;
        if (dto.getId() != null) {
            m = materialMapper.selectById(dto.getId());
            if (m == null) return Result.error("项目不存在");
        } else {
            m = new EduMaterial();
            m.setTenantId(AuthContext.get().getTenantId());
            m.setStatus(1);
        }
        m.setName(dto.getName().trim());
        m.setPrice(dto.getPrice());
        m.setSort(dto.getSort());
        if (dto.getId() != null) materialMapper.updateById(m);
        else materialMapper.insert(m);
        return Result.ok(m);
    }

    /** 停用项目（选择弹窗内删除 = 停用，历史流水不受影响） */
    @DeleteMapping("/{id}")
    @Log("停用教材杂费项目")
    public Result<Void> disable(@PathVariable Long id) {
        EduMaterial m = materialMapper.selectById(id);
        if (m == null) return Result.error("项目不存在");
        m.setStatus(0);
        materialMapper.updateById(m);
        return Result.ok();
    }

    /** 购买办理：明细留档，应收=Σ价格×数量，实收=渠道合计（允许欠费） */
    @PostMapping("/handle")
    @Log("教材杂费办理")
    public Result<Map<String, Object>> handle(@RequestBody HandleDTO dto) {
        if (dto.getStudentId() == null) return Result.error("请选择学员");
        if (dto.getItems() == null || dto.getItems().isEmpty()) return Result.error("请选择教材杂费项目");
        EduStudent s = studentMapper.selectById(dto.getStudentId());
        if (s == null || s.getDeleted() == 1) return Result.error("学员不存在或已删除");
        // 重复提交防护：同一用户5秒内完全相同的办理请求只放行一次
        com.edu.security.LoginUser u = AuthContext.get();
        String fp = u.getTenantId() + ":" + u.getId() + ":material:" + dto.getStudentId()
                + ":" + dto.getItems().size() + ":" + dto.getPaidAmount() + ":" + dto.getItems().hashCode();
        if (!repeatGuard.tryAcquire(fp))
            return Result.error("教材杂费已提交，请勿重复提交");
        // 应收小计 = Σ价格×数量（服务端按明细重算，不信任前端合计）
        BigDecimal amount = BigDecimal.ZERO;
        for (ItemDTO it : dto.getItems()) {
            if (it.getPrice() == null || it.getQty() == null || it.getQty() < 1) continue;
            amount = amount.add(it.getPrice().multiply(BigDecimal.valueOf(it.getQty())));
        }
        if (amount.compareTo(BigDecimal.ZERO) <= 0) return Result.error("请填写有效的价格与数量");
        BigDecimal paid = dto.getPaidAmount() == null ? BigDecimal.ZERO : dto.getPaidAmount();
        if (paid.compareTo(amount) > 0) return Result.error("实收不能超过应收小计");

        EduMaterialLog log = new EduMaterialLog();
        log.setTenantId(s.getTenantId());
        log.setStudentId(dto.getStudentId());
        log.setItems(dto.getItemsJson());
        log.setAmount(amount);
        log.setPaidAmount(paid);
        log.setPayDetail(dto.getPayDetail());
        log.setCampus(dto.getCampus());
        log.setSource(dto.getSource());
        if (dto.getHandleDate() != null && !dto.getHandleDate().isEmpty()) {
            try { log.setHandleDate(LocalDate.parse(dto.getHandleDate())); }
            catch (Exception ignore) { /* 非法日期忽略 */ }
        } else {
            log.setHandleDate(LocalDate.now());
        }
        log.setOrderTaker(dto.getOrderTaker());
        log.setConsultant(dto.getConsultant());
        log.setOperator(AuthContext.get().getRealName());
        String userRemark = dto.getRemark() == null ? "" : dto.getRemark().trim();
        log.setRemark("教材杂费 ¥" + amount.stripTrailingZeros().toPlainString()
                + (userRemark.isEmpty() ? "" : "：" + userRemark));
        logMapper.insert(log);

        Map<String, Object> r = new HashMap<>();
        r.put("id", log.getId());
        r.put("amount", amount);
        r.put("paid", paid);
        r.put("arrears", amount.subtract(paid).max(BigDecimal.ZERO));
        return Result.ok(r);
    }

    /** 办理流水编辑（办理记录-教材杂费）：仅经办信息可改 */
    @PutMapping("/log")
    @Log("编辑教材杂费流水")
    public Result<Void> updateLog(@RequestBody LogUpdateDTO dto) {
        if (dto.getId() == null) return Result.error("缺少流水ID");
        EduMaterialLog l = logMapper.selectById(dto.getId());
        if (l == null) return Result.error("流水不存在");
        if (dto.getCampus() != null) l.setCampus(dto.getCampus());
        if (dto.getSource() != null) l.setSource(dto.getSource());
        if (dto.getHandleDate() != null) l.setHandleDate(dto.getHandleDate());
        if (dto.getOrderTaker() != null) l.setOrderTaker(dto.getOrderTaker());
        if (dto.getConsultant() != null) l.setConsultant(dto.getConsultant());
        logMapper.updateById(l);
        return Result.ok();
    }

    @Data
    public static class MaterialDTO {
        private Long id;
        private String name;
        private BigDecimal price;
        private Integer sort;
    }

    @Data
    public static class ItemDTO {
        private Long materialId;
        private String name;
        private BigDecimal price;
        private Integer qty;
        /** 是否领用 */
        private Boolean got;
    }

    @Data
    public static class HandleDTO {
        private Long studentId;
        private List<ItemDTO> items;
        /** 明细JSON留档（与 items 一致） */
        private String itemsJson;
        private BigDecimal paidAmount;
        private String payDetail;
        private String campus;
        private String source;
        private String handleDate;
        private String orderTaker;
        private String consultant;
        private String remark;
    }

    @Data
    public static class LogUpdateDTO {
        private Long id;
        private String campus;
        private String source;
        private LocalDate handleDate;
        private String orderTaker;
        private String consultant;
    }
}
