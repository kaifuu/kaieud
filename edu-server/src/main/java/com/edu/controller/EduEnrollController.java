package com.edu.controller;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.edu.common.SafeSort;
import com.edu.common.Log;
import com.edu.common.PageResult;
import com.edu.common.Result;
import com.edu.entity.EduEnroll;
import com.edu.mapper.EduEnrollMapper;
import com.edu.security.RequiresPerm;
import com.edu.service.EduEnrollService;
import lombok.Data;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

/** 报名管理：订阅/退订班课、指派老师、补课时 */
@RestController
@RequestMapping("/edu/enroll")
@RequiredArgsConstructor
@RequiresPerm("edu:enroll")
public class EduEnrollController {

    private final EduEnrollMapper enrollMapper;
    private final EduEnrollService enrollService;
    private final com.edu.mapper.EduPackageMapper packageMapper;
    private final com.edu.mapper.EduAccountLogMapper accountMapper;
    private final com.edu.mapper.EduEnrollLogMapper enrollLogMapper;
    private final com.edu.mapper.EduCoursePriceMapper coursePriceMapper;
    private final com.edu.mapper.EduCourseCampusMapper courseCampusMapper;
    private final com.edu.common.RepeatGuard repeatGuard;
    private final com.edu.mapper.EduPaymentMapper paymentMapper;
    private final com.edu.mapper.EduScoreLogMapper scoreMapper;
    private final com.edu.mapper.EduMaterialLogMapper materialLogMapper;
    private final com.edu.mapper.EduSupplementLogMapper supplementLogMapper;

    /**
     * 办理中心统一记录列表：报名/续报（edu_enroll）+ 余额充值/退费/转课（账户流水）
     * + 补费（edu_payment），按经办日期倒序。行上 kind 标记来源，供前端「编辑」
     * 跳转对应办理页：enroll=报名/续报编辑、log=按类型编辑流水经办信息、payment=缴费管理。
     */
    @GetMapping("/handle-records")
    public Result<?> handleRecords(@RequestParam(defaultValue = "1") long page,
                                   @RequestParam(defaultValue = "200") long size,
                                   @RequestParam(required = false) String type,
                                   @RequestParam(required = false) Long studentId) {
        java.util.List<java.util.Map<String, Object>> rows = new java.util.ArrayList<>();
        // 1) 报名单：在读/已结课=报名；已转课=转课转出单；退费退出的=退费单
        for (EduEnroll e : enrollMapper.selectList(
                new LambdaQueryWrapper<EduEnroll>()
                        .eq(studentId != null, EduEnroll::getStudentId, studentId)
                        .orderByDesc(EduEnroll::getId))) {
            String t = e.getStatus() != null && e.getStatus() == 4 ? "转课"
                    : e.getStatus() != null && e.getStatus() == 3
                      && e.getRemark() != null && e.getRemark().startsWith("退费退出") ? "退费" : "报名";
            java.util.Map<String, Object> m = new java.util.LinkedHashMap<>();
            m.put("rid", "EN" + e.getId());
            m.put("kind", "enroll");
            m.put("id", e.getId());
            m.put("refId", e.getId());
            m.put("type", t);
            m.put("studentId", e.getStudentId());
            m.put("courseId", e.getCourseId());
            m.put("packageName", e.getPackageName());
            m.put("totalHours", e.getTotalHours());
            m.put("price", e.getPrice());
            m.put("discountAmount", e.getDiscountAmount());
            m.put("totalAmount", e.getTotalAmount());
            m.put("paidAmount", e.getPaidAmount());
            m.put("payDetail", e.getPayDetail());
            m.put("campus", e.getCampus());
            m.put("clazzId", e.getClazzId());
            m.put("teacherId", e.getTeacherId());
            m.put("validDate", e.getValidDate());
            m.put("handleDate", e.getHandleDate());
            m.put("handler", e.getHandler());
            m.put("orderTaker", e.getOrderTaker());
            m.put("consultant", e.getConsultant());
            m.put("remark", e.getRemark());
            m.put("source", e.getSource());
            m.put("status", e.getStatus());
            m.put("createTime", e.getCreateTime() == null ? null : e.getCreateTime().toString());
            rows.add(m);
        }
        // 2) 账户流水：充值(type1)=余额充值；转课转出/退费转结(type2)按备注前缀分类
        for (com.edu.entity.EduAccountLog l : accountMapper.selectList(
                new LambdaQueryWrapper<com.edu.entity.EduAccountLog>()
                        .eq(studentId != null, com.edu.entity.EduAccountLog::getStudentId, studentId)
                        .orderByDesc(com.edu.entity.EduAccountLog::getId))) {
            String rm = l.getRemark() == null ? "" : l.getRemark();
            String t;
            if (l.getType() != null && l.getType() == 1) t = "充值";
            else if (rm.startsWith("转课转出学费入账")) t = "转课";
            else if (l.getType() != null && l.getType() == 2) t = "退费";
            else continue;
            java.util.Map<String, Object> m = new java.util.LinkedHashMap<>();
            m.put("rid", "AL" + l.getId());
            m.put("kind", "log");
            m.put("refId", l.getId());
            m.put("type", t);
            m.put("studentId", l.getStudentId());
            m.put("amount", l.getAmount());
            m.put("content", rm);
            m.put("payDetail", l.getPayDetail());
            m.put("campus", l.getCampus());
            m.put("handleDate", l.getHandleDate());
            m.put("handler", l.getOperator());
            m.put("orderTaker", l.getOrderTaker());
            m.put("consultant", l.getConsultant());
            m.put("source", l.getSource());
            m.put("remark", rm);
            m.put("createTime", l.getCreateTime() == null ? null : l.getCreateTime().toString());
            rows.add(m);
        }
        // 3) 补费（缴费记录）
        for (com.edu.entity.EduPayment p : paymentMapper.selectList(
                new LambdaQueryWrapper<com.edu.entity.EduPayment>()
                        .eq(studentId != null, com.edu.entity.EduPayment::getStudentId, studentId)
                        .orderByDesc(com.edu.entity.EduPayment::getId))) {
            java.util.Map<String, Object> m = new java.util.LinkedHashMap<>();
            m.put("rid", "PY" + p.getId());
            m.put("kind", "payment");
            m.put("refId", p.getId());
            m.put("type", "补费");
            m.put("studentId", p.getStudentId());
            m.put("courseId", p.getCourseId());
            m.put("amount", p.getAmount());
            m.put("content", "缴费单 " + (p.getPaymentNo() == null ? p.getId() : p.getPaymentNo()));
            m.put("handleDate", p.getPayTime() == null ? null : p.getPayTime().toLocalDate().toString());
            m.put("handler", p.getOperator());
            m.put("remark", p.getRemark());
            m.put("createTime", p.getCreateTime() == null ? null : p.getCreateTime().toString());
            rows.add(m);
        }
        // 4) 积分办理流水（办理中心-积分）
        for (com.edu.entity.EduScoreLog sl : scoreMapper.selectList(
                new LambdaQueryWrapper<com.edu.entity.EduScoreLog>()
                        .eq(studentId != null, com.edu.entity.EduScoreLog::getStudentId, studentId)
                        .orderByDesc(com.edu.entity.EduScoreLog::getId))) {
            java.util.Map<String, Object> m = new java.util.LinkedHashMap<>();
            m.put("rid", "SC" + sl.getId());
            m.put("kind", "score");
            m.put("refId", sl.getId());
            m.put("type", "积分");
            m.put("studentId", sl.getStudentId());
            m.put("amount", sl.getPoints());
            m.put("content", sl.getRemark());
            m.put("campus", sl.getCampus());
            m.put("handleDate", sl.getHandleDate());
            m.put("handler", sl.getOperator());
            m.put("orderTaker", sl.getOrderTaker());
            m.put("consultant", sl.getConsultant());
            m.put("source", sl.getSource());
            m.put("remark", sl.getRemark());
            m.put("createTime", sl.getCreateTime() == null ? null : sl.getCreateTime().toString());
            rows.add(m);
        }
        // 5) 教材杂费办理流水
        for (com.edu.entity.EduMaterialLog ml : materialLogMapper.selectList(
                new LambdaQueryWrapper<com.edu.entity.EduMaterialLog>()
                        .eq(studentId != null, com.edu.entity.EduMaterialLog::getStudentId, studentId)
                        .orderByDesc(com.edu.entity.EduMaterialLog::getId))) {
            java.util.Map<String, Object> m = new java.util.LinkedHashMap<>();
            m.put("rid", "MT" + ml.getId());
            m.put("kind", "material");
            m.put("refId", ml.getId());
            m.put("type", "教材杂费");
            m.put("studentId", ml.getStudentId());
            m.put("totalAmount", ml.getAmount());
            m.put("paidAmount", ml.getPaidAmount());
            m.put("content", ml.getRemark());
            m.put("payDetail", ml.getPayDetail());
            m.put("campus", ml.getCampus());
            m.put("handleDate", ml.getHandleDate());
            m.put("handler", ml.getOperator());
            m.put("orderTaker", ml.getOrderTaker());
            m.put("consultant", ml.getConsultant());
            m.put("source", ml.getSource());
            m.put("remark", ml.getRemark());
            m.put("createTime", ml.getCreateTime() == null ? null : ml.getCreateTime().toString());
            rows.add(m);
        }
        // 6) 补费办理流水
        for (com.edu.entity.EduSupplementLog sl : supplementLogMapper.selectList(
                new LambdaQueryWrapper<com.edu.entity.EduSupplementLog>()
                        .eq(studentId != null, com.edu.entity.EduSupplementLog::getStudentId, studentId)
                        .orderByDesc(com.edu.entity.EduSupplementLog::getId))) {
            java.util.Map<String, Object> m = new java.util.LinkedHashMap<>();
            m.put("rid", "SM" + sl.getId());
            m.put("kind", "supplement");
            m.put("refId", sl.getId());
            m.put("type", "补费");
            m.put("studentId", sl.getStudentId());
            m.put("totalAmount", sl.getAmount());
            m.put("paidAmount", sl.getPaidAmount());
            m.put("content", sl.getRemark());
            m.put("payDetail", sl.getPayDetail());
            m.put("campus", sl.getCampus());
            m.put("handleDate", sl.getHandleDate());
            m.put("handler", sl.getOperator());
            m.put("orderTaker", sl.getOrderTaker());
            m.put("consultant", sl.getConsultant());
            m.put("source", sl.getSource());
            m.put("remark", sl.getRemark());
            m.put("createTime", sl.getCreateTime() == null ? null : sl.getCreateTime().toString());
            rows.add(m);
        }
        // 类型筛选 + 排序（经办日期/创建时间倒序）+ 分页
        if (type != null && !type.isEmpty())
            rows.removeIf(r -> !type.equals(r.get("type")));
        rows.sort((a, b) -> String.valueOf(b.getOrDefault("handleDate", "")).compareTo(
                String.valueOf(a.getOrDefault("handleDate", ""))));
        int from = (int) Math.max(0, (page - 1) * size);
        int to = Math.min(rows.size(), from + (int) size);
        java.util.Map<String, Object> result = new java.util.HashMap<>();
        result.put("total", rows.size());
        result.put("records", from >= rows.size() ? new java.util.ArrayList<>() : rows.subList(from, to));
        return Result.ok(result);
    }

    /** 提交指纹前缀（操作名 + 用户 + 租户），拼接业务字段后交给 RepeatGuard 去重 */
    private String guardKey(String op) {
        com.edu.security.LoginUser u = com.edu.security.AuthContext.get();
        return u.getTenantId() + ":" + u.getId() + ":" + op + ":";
    }

    @GetMapping("/page")
    public Result<PageResult<EduEnroll>> page(@RequestParam(defaultValue = "1") long page,
                                              @RequestParam(defaultValue = "10") long size,
                                              @RequestParam(required = false) Long studentId,
                                              @RequestParam(required = false) Long teacherId,
                                              @RequestParam(required = false) Integer status,
 @RequestParam(required = false) String sortBy,
 @RequestParam(required = false) String direction) {
        Page<EduEnroll> p = enrollMapper.selectPage(new Page<>(page, size),
                new LambdaQueryWrapper<EduEnroll>()
                        .eq(studentId != null, EduEnroll::getStudentId, studentId)
                        .eq(teacherId != null, EduEnroll::getTeacherId, teacherId)
                        .eq(status != null, EduEnroll::getStatus, status)
                        .last(SafeSort.order(sortBy, direction, "id", "id", "price", "discount", "totalAmount", "usedHours", "status", "createTime")));
        // 填充套餐名称（按课程订阅/套餐已删除时为空）
        java.util.List<Long> pkgIds = p.getRecords().stream()
                .map(EduEnroll::getPackageId).filter(java.util.Objects::nonNull)
                .collect(java.util.stream.Collectors.toList());
        if (!pkgIds.isEmpty()) {
            java.util.Map<Long, String> names = packageMapper.selectBatchIds(pkgIds).stream()
                    .collect(java.util.stream.Collectors.toMap(
                            com.edu.entity.EduPackage::getId, com.edu.entity.EduPackage::getName, (a, b) -> a));
            p.getRecords().forEach(e -> e.setPackageName(names.get(e.getPackageId())));
        }
        return Result.ok(PageResult.of(p.getTotal(), p.getRecords()));
    }

    @Data
    public static class SubscribeDTO {
        private Long studentId;
        /** 报名/续报按套餐订阅：课程/课时/单价随套餐带出 */
        private Long packageId;
        /** 未选套餐时的入口：按课程默认售价 */
        private Long courseId;
        /** 报名校区（edu_course_campus 校区名，一单一校区；未传时取课程首个关联校区） */
        private String campus;
        private Long clazzId;
        private Long teacherId;
        /** 按课程定价行订阅（edu_course_price，按课时模式） */
        private Long priceId;
        /** 份数（数量），默认 1 */
        private Integer qty;
        /** 优惠金额（合计） */
        private java.math.BigDecimal discountAmount;
        /** 优惠明细JSON [{type,amount}]（编辑回显用，不合并） */
        private String discountDetail;
        /** 本次实收（支付信息分摊到该课程） */
        private java.math.BigDecimal paidAmount;
        /** 有效期（yyyy-MM-dd，勾选后传） */
        private String validDate;
        /** 备注（对内） */
        private String remark;
        /** 销售来源 */
        private String source;
        /** 经办人（默认当前登录人） */
        private String handler;
        /** 经办日期（yyyy-MM-dd，默认今天） */
        private String handleDate;
        /** 采单员 */
        private String orderTaker;
        /** 咨询顾问 */
        private String consultant;
        /** 支付信息（日志用） */
        private String payInfo;
        /** 支付明细JSON [{channel,account,serial,amount}] */
        private String payDetail;
    }

    /** 订阅班课/课程（套餐优先） */
    @PostMapping("/subscribe")
    @Log("订阅班课")
    public Result<EduEnroll> subscribe(@RequestBody SubscribeDTO dto) {
        // 重复提交防护：同一用户5秒内完全相同的订阅请求只放行一次
        String fp = dto.getStudentId() + ":" + dto.getPackageId() + ":" + dto.getCourseId() + ":"
                + dto.getCampus() + ":" + dto.getPriceId() + ":" + dto.getQty() + ":"
                + dto.getPaidAmount() + ":" + dto.getPayDetail();
        if (!repeatGuard.tryAcquire(guardKey("subscribe") + fp))
            return Result.error("报名已提交，请勿重复提交");
        return Result.ok(enrollService.subscribe(dto));
    }

    /** 学员欠费记录（补费用）：在读报名单与教材杂费单中 欠费>0 的记录 */
    @GetMapping("/supplement-records")
    public Result<?> supplementRecords(@RequestParam Long studentId) {
        java.util.List<java.util.Map<String, Object>> rows = new java.util.ArrayList<>();
        for (EduEnroll e : enrollMapper.selectList(new LambdaQueryWrapper<EduEnroll>()
                .eq(EduEnroll::getStudentId, studentId).eq(EduEnroll::getStatus, 1)
                .orderByDesc(EduEnroll::getId))) {
            java.math.BigDecimal due = (e.getTotalAmount() == null ? java.math.BigDecimal.ZERO : e.getTotalAmount())
                    .subtract(e.getPaidAmount() == null ? java.math.BigDecimal.ZERO : e.getPaidAmount());
            if (due.compareTo(java.math.BigDecimal.ZERO) <= 0) continue;
            java.util.Map<String, Object> m = new java.util.LinkedHashMap<>();
            m.put("kind", "enroll");
            m.put("refId", e.getId());
            m.put("rid", "EN" + e.getId());
            m.put("typeName", "报名");
            m.put("courseId", e.getCourseId());
            m.put("packageName", e.getPackageName());
            m.put("handler", e.getHandler());
            m.put("handleDate", e.getHandleDate());
            m.put("campus", e.getCampus());
            m.put("arrears", due);
            rows.add(m);
        }
        for (com.edu.entity.EduMaterialLog ml : materialLogMapper.selectList(
                new LambdaQueryWrapper<com.edu.entity.EduMaterialLog>()
                        .eq(com.edu.entity.EduMaterialLog::getStudentId, studentId)
                        .orderByDesc(com.edu.entity.EduMaterialLog::getId))) {
            java.math.BigDecimal due = (ml.getAmount() == null ? java.math.BigDecimal.ZERO : ml.getAmount())
                    .subtract(ml.getPaidAmount() == null ? java.math.BigDecimal.ZERO : ml.getPaidAmount());
            if (due.compareTo(java.math.BigDecimal.ZERO) <= 0) continue;
            java.util.Map<String, Object> m = new java.util.LinkedHashMap<>();
            m.put("kind", "material");
            m.put("refId", ml.getId());
            m.put("rid", "MT" + ml.getId());
            m.put("typeName", "教材杂费");
            m.put("name", ml.getRemark());
            m.put("handler", ml.getOperator());
            m.put("handleDate", ml.getHandleDate());
            m.put("campus", ml.getCampus());
            m.put("arrears", due);
            rows.add(m);
        }
        return Result.ok(rows);
    }

    /** 补费办理入参 */
    @Data
    public static class SupplementDTO {
        private Long studentId;
        private java.util.List<ItemDTO> items;
        /** 明细JSON留档（与 items 一致） */
        private String itemsJson;
        private java.math.BigDecimal paidAmount;
        private String payDetail;
        private String campus;
        private String source;
        private String handleDate;
        private String orderTaker;
        private String consultant;
        private String remark;

        @Data
        public static class ItemDTO {
            /** enroll=报名单 material=教材杂费单 */
            private String kind;
            private Long refId;
            /** 本次补费金额 */
            private java.math.BigDecimal amount;
        }
    }

    /** 补费办理：逐单追加实收并写补费流水 */
    @PostMapping("/supplement")
    @Log("补费")
    public Result<?> supplement(@RequestBody SupplementDTO dto) {
        // 重复提交防护
        String itemsFp = dto.getItems() == null ? "" : dto.getItems().stream()
                .sorted(java.util.Comparator.comparing(i -> i.getRefId() == null ? 0L : i.getRefId()))
                .map(i -> i.getKind() + "-" + i.getRefId() + "-" + i.getAmount())
                .collect(java.util.stream.Collectors.joining(","));
        if (!repeatGuard.tryAcquire(guardKey("supplement") + dto.getStudentId() + ":" + itemsFp))
            return Result.error("补费已提交，请勿重复提交");
        return Result.ok(enrollService.supplement(dto));
    }

    /** 补费流水编辑（办理记录-补费）：仅经办信息可改 */
    @PutMapping("/supplement-log")
    @Log("编辑补费流水")
    public Result<Void> updateSupplementLog(@RequestBody SupplementLogUpdateDTO dto) {
        if (dto.getId() == null) return Result.error("缺少流水ID");
        com.edu.entity.EduSupplementLog l = supplementLogMapper.selectById(dto.getId());
        if (l == null) return Result.error("补费流水不存在");
        if (dto.getCampus() != null) l.setCampus(dto.getCampus());
        if (dto.getSource() != null) l.setSource(dto.getSource());
        if (dto.getHandleDate() != null) l.setHandleDate(dto.getHandleDate());
        if (dto.getOrderTaker() != null) l.setOrderTaker(dto.getOrderTaker());
        if (dto.getConsultant() != null) l.setConsultant(dto.getConsultant());
        supplementLogMapper.updateById(l);
        return Result.ok();
    }

    @Data
    public static class SupplementLogUpdateDTO {
        private Long id;
        private String campus;
        private String source;
        private java.time.LocalDate handleDate;
        private String orderTaker;
        private String consultant;
    }

    /** 转课（课时转出）：在读报名单转出剩余课时，学费自动转入学员账户余额 */
    @Data
    public static class TransferDTO {
        private Long studentId;
        /** 转出明细：报名单 + 本次转出课时（1~剩余课时） */
        private java.util.List<ItemDTO> items;
        private String remark;
        private String source;
        private String handler;
        private String handleDate;
        private String orderTaker;
        private String consultant;

        @Data
        public static class ItemDTO {
            private Long enrollId;
            /** 本次转出课时 */
            private Integer hours;
        }
    }

    /** 转课办理：扣减课时/关闭报名单，转出学费入账户余额 */
    @PostMapping("/transfer")
    @Log("转课")
    public Result<?> transfer(@RequestBody TransferDTO dto) {
        // 重复提交防护：转出明细（单号+课时）完全相同的请求5秒内只放行一次
        String itemsFp = dto.getItems() == null ? "" : dto.getItems().stream()
                .sorted(java.util.Comparator.comparing(i -> i.getEnrollId() == null ? 0L : i.getEnrollId()))
                .map(i -> i.getEnrollId() + "-" + i.getHours())
                .collect(java.util.stream.Collectors.joining(","));
        if (!repeatGuard.tryAcquire(guardKey("transfer") + dto.getStudentId() + ":" + itemsFp))
            return Result.error("转课已提交，请勿重复提交");
        return Result.ok(enrollService.transfer(dto));
    }

    /** 退费：在读报名单退出剩余课时，金额转结到账户余额 */
    @Data
    public static class RefundDTO {
        private Long studentId;
        /** 退出明细：报名单 + 本次退出课时 + 是否按原价退费（默认按实收折算） */
        private java.util.List<ItemDTO> items;
        /** 实退合计（渠道退款金额，≤应退总计；差额转结账户余额） */
        private java.math.BigDecimal paidOut;
        /** 实退渠道明细JSON [{channel,account,serial,amount}]（账户流水留档） */
        private String payDetail;
        /** 经办校区 */
        private String campus;
        private String remark;
        private String source;
        private String handler;
        private String handleDate;
        private String orderTaker;
        private String consultant;

        @Data
        public static class ItemDTO {
            private Long enrollId;
            /** 本次退出课时 */
            private Integer hours;
            /** 按原价退费（优惠一并退）；false=按实收口径折算 */
            private Boolean byOriginal;
        }
    }

    /** 退费办理：扣减课时/退订报名单，实退渠道退款，差额转结账户余额 */
    @PostMapping("/refund")
    @Log("退费")
    public Result<?> refund(@RequestBody RefundDTO dto) {
        // 重复提交防护：退出明细完全相同的请求5秒内只放行一次
        String itemsFp = dto.getItems() == null ? "" : dto.getItems().stream()
                .sorted(java.util.Comparator.comparing(i -> i.getEnrollId() == null ? 0L : i.getEnrollId()))
                .map(i -> i.getEnrollId() + "-" + i.getHours() + "-" + i.getByOriginal())
                .collect(java.util.stream.Collectors.joining(","));
        if (!repeatGuard.tryAcquire(guardKey("refund") + dto.getStudentId() + ":" + itemsFp))
            return Result.error("退费已提交，请勿重复提交");
        return Result.ok(enrollService.refund(dto));
    }

    /** 修改报名单：全字段（定价/数量/优惠/老师/班级/支付/经办） */
    @Data
    public static class UpdateDTO {
        private Long id;
        private Long courseId;
        /** 报名校区（一单一校区，edu_course_campus 校区名） */
        private String campus;
        private Long clazzId;
        private Long teacherId;
        /** 定价行ID（edu_course_price） */
        private Long priceId;
        /** 份数 */
        private Integer qty;
        /** 优惠金额 */
        private java.math.BigDecimal discountAmount;
        /** 优惠明细JSON [{type,amount}]（编辑回显用，不合并） */
        private String discountDetail;
        private java.math.BigDecimal paidAmount;
        private String remark;
        private String source;
        private String orderTaker;
        private String consultant;
        private String validDate;
        private String handleDate;
        private String handler;
        /** 1在读 2已结课 3已退订（退订请走取消订阅） */
        private Integer status;
        /** 支付信息（日志用） */
        private String payInfo;
        /** 支付明细JSON */
        private String payDetail;
    }

    @PutMapping
    @Log("修改报名单")
    public Result<Void> update(@RequestBody UpdateDTO dto) {
        if (dto.getId() == null) return Result.error("缺少订单ID");
        EduEnroll e = enrollMapper.selectById(dto.getId());
        if (e == null) return Result.error("报名单不存在");

        // 课程/班级/老师
        if (dto.getCourseId() != null) e.setCourseId(dto.getCourseId());
        // 报名校区：显式提交时校验属于课程的开课校区
        if (dto.getCampus() != null && !dto.getCampus().isEmpty() && e.getCourseId() != null) {
            Long cnt = courseCampusMapper.selectCount(new LambdaQueryWrapper<com.edu.entity.EduCourseCampus>()
                    .eq(com.edu.entity.EduCourseCampus::getCourseId, e.getCourseId())
                    .eq(com.edu.entity.EduCourseCampus::getCampus, dto.getCampus()));
            if (cnt == 0) return Result.error("校区「" + dto.getCampus() + "」未开设该课程");
            e.setCampus(dto.getCampus());
        }
        if (dto.getClazzId() != null) e.setClazzId(dto.getClazzId());
        if (dto.getTeacherId() != null) e.setTeacherId(dto.getTeacherId());

        // 定价重算：定价行×数量得到原价（未减优惠）；未重算时以记录原价（现应收+已优惠）为基准
        java.math.BigDecimal original = null;
        if (dto.getPriceId() != null) {
            com.edu.entity.EduCoursePrice pr = coursePriceMapper.selectById(dto.getPriceId());
            if (pr != null && pr.getChargeMode() == 1) {
                int qty = dto.getQty() == null || dto.getQty() < 1 ? 1 : dto.getQty();
                e.setTotalHours((int) Math.round(pr.getHours().doubleValue() * qty));
                e.setPrice(pr.getTotalAmount().divide(pr.getHours(), 2, java.math.BigDecimal.ROUND_HALF_UP));
                original = pr.getTotalAmount().multiply(java.math.BigDecimal.valueOf(qty));
            }
        } else if (dto.getQty() != null && dto.getQty() >= 1 && e.getPrice() != null
                && e.getTotalHours() != null) {
            // 无定价行时按数量重算
            e.setTotalHours(e.getTotalHours() * dto.getQty());
            original = e.getPrice().multiply(java.math.BigDecimal.valueOf(e.getTotalHours()));
        }
        if (original == null)
            original = (e.getTotalAmount() == null ? java.math.BigDecimal.ZERO : e.getTotalAmount())
                    .add(e.getDiscountAmount() == null ? java.math.BigDecimal.ZERO : e.getDiscountAmount());
        // 优惠：显式传值时按新优惠重算（0=清除优惠），未传时保持记录原优惠
        java.math.BigDecimal discount = dto.getDiscountAmount() != null
                ? dto.getDiscountAmount().max(java.math.BigDecimal.ZERO)
                : (e.getDiscountAmount() == null ? java.math.BigDecimal.ZERO : e.getDiscountAmount());
        java.math.BigDecimal after = original.subtract(discount);
        if (after.compareTo(java.math.BigDecimal.ZERO) < 0) after = java.math.BigDecimal.ZERO;
        e.setTotalAmount(after);
        e.setDiscountAmount(discount);

        if (dto.getPaidAmount() != null) e.setPaidAmount(dto.getPaidAmount());
        // 优惠明细留档：显式传值时覆盖（空数组=清除），未传时保持原样
        if (dto.getDiscountDetail() != null) e.setDiscountDetail(dto.getDiscountDetail());
        if (dto.getPayDetail() != null) e.setPayDetail(dto.getPayDetail());
        if (dto.getRemark() != null) e.setRemark(dto.getRemark());
        if (dto.getSource() != null) e.setSource(dto.getSource());
        if (dto.getOrderTaker() != null) e.setOrderTaker(dto.getOrderTaker());
        if (dto.getConsultant() != null) e.setConsultant(dto.getConsultant());
        if (dto.getValidDate() != null && !dto.getValidDate().isEmpty()) {
            try { e.setValidDate(java.time.LocalDate.parse(dto.getValidDate())); } catch (Exception ignore) { }
        }
        if (dto.getHandleDate() != null && !dto.getHandleDate().isEmpty()) {
            try { e.setHandleDate(java.time.LocalDate.parse(dto.getHandleDate())); } catch (Exception ignore) { }
        }
        if (dto.getHandler() != null && !dto.getHandler().isEmpty()) e.setHandler(dto.getHandler());
        if (dto.getStatus() != null && dto.getStatus() != 3) e.setStatus(dto.getStatus());
        enrollMapper.updateById(e);
        enrollService.writeLogPublic(e, "UPDATE", dto.getPayInfo());
        return Result.ok();
    }

    /** 报名单操作日志列表 */
    @GetMapping("/{id}/logs")
    public Result<?> logs(@PathVariable Long id) {
        return Result.ok(enrollLogMapper.selectList(
                new LambdaQueryWrapper<com.edu.entity.EduEnrollLog>()
                        .eq(com.edu.entity.EduEnrollLog::getEnrollId, id)
                        .orderByDesc(com.edu.entity.EduEnrollLog::getId)));
    }

    @Data
    public static class CreditDTO {
        private Long studentId;
        private java.math.BigDecimal amount;
        private String remark;
    }

    /** 报名实收超额自动充入学员账户（充值流水） */
    @PostMapping("/credit")
    @Log("报名超额充入账户")
    public Result<Void> credit(@RequestBody CreditDTO dto) {
        if (dto.getStudentId() == null || dto.getAmount() == null
                || dto.getAmount().compareTo(java.math.BigDecimal.ZERO) <= 0)
            return Result.error("学员与金额为必填项");
        com.edu.entity.EduAccountLog log = new com.edu.entity.EduAccountLog();
        log.setTenantId(com.edu.security.AuthContext.get().getTenantId());
        log.setStudentId(dto.getStudentId());
        log.setType(1);
        log.setAmount(dto.getAmount());
        log.setRemark(dto.getRemark() == null || dto.getRemark().isEmpty()
                ? "报名实收超额自动充入" : dto.getRemark());
        log.setOperator(com.edu.security.AuthContext.get().getRealName());
        accountMapper.insert(log);
        return Result.ok();
    }

    /** 删除报名单（编辑页移除课程）：取消未来排课并删除记录与日志 */
    @DeleteMapping("/{id}")
    @Log("删除报名单")
    public Result<Void> delete(@PathVariable Long id) {
        enrollService.deleteEnroll(id);
        return Result.ok();
    }

    /** 取消订阅 */
    @PostMapping("/{id}/unsubscribe")
    @Log("取消订阅班课")
    public Result<Void> unsubscribe(@PathVariable Long id) {
        enrollService.unsubscribe(id);
        return Result.ok();
    }

    /** 为学生指派/更换上课老师 */
    @Data
    public static class AssignDTO {
        private Long teacherId;
    }

    @PostMapping("/{id}/assign-teacher")
    @Log("指派授课老师")
    public Result<Void> assignTeacher(@PathVariable Long id, @RequestBody AssignDTO dto) {
        enrollService.assignTeacher(id, dto.getTeacherId());
        return Result.ok();
    }

    /** 补充因请假取消的课时次数 */
    @Data
    public static class CompensateHoursDTO {
        private Integer hours;
    }

    @PostMapping("/{id}/compensate-hours")
    @Log("补充课时次数")
    public Result<Void> compensateHours(@PathVariable Long id, @RequestBody CompensateHoursDTO dto) {
        enrollService.compensateHours(id, dto.getHours() == null ? 1 : dto.getHours());
        return Result.ok();
    }
}
