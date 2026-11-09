package com.edu.controller;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.edu.common.Log;
import com.edu.common.RepeatGuard;
import com.edu.common.Result;
import com.edu.entity.EduScoreLog;
import com.edu.entity.EduStudent;
import com.edu.mapper.EduScoreLogMapper;
import com.edu.mapper.EduStudentMapper;
import com.edu.security.AuthContext;
import com.edu.security.RequiresPerm;
import lombok.Data;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.HashMap;
import java.util.Map;

/**
 * 积分管理（办理中心-积分）：学员积分的增加/兑换办理与余额查询。
 * 复用办理中心权限（edu:enroll），余额=Σ增加−Σ兑换，兑换不可超过余额。
 */
@RestController
@RequestMapping("/edu/score")
@RequiredArgsConstructor
@RequiresPerm("edu:enroll")
public class EduScoreController {

    private final EduScoreLogMapper scoreMapper;
    private final EduStudentMapper studentMapper;
    private final RepeatGuard repeatGuard;

    /** 积分余额汇总：累计增加 / 累计兑换 / 剩余积分 */
    @GetMapping("/summary")
    public Result<Map<String, Object>> summary(@RequestParam Long studentId) {
        return Result.ok(summaryOf(studentId));
    }

    private Map<String, Object> summaryOf(Long studentId) {
        BigDecimal earned = BigDecimal.ZERO, used = BigDecimal.ZERO;
        for (EduScoreLog l : scoreMapper.selectList(new LambdaQueryWrapper<EduScoreLog>()
                .eq(EduScoreLog::getStudentId, studentId))) {
            if (l.getPoints() == null) continue;
            if (l.getType() != null && l.getType() == 1) earned = earned.add(l.getPoints());
            else if (l.getType() != null && l.getType() == 2) used = used.add(l.getPoints());
        }
        Map<String, Object> m = new HashMap<>();
        m.put("earned", earned);
        m.put("used", used);
        m.put("total", earned.subtract(used));
        return m;
    }

    /** 积分办理：type1增加 / type2兑换（不可超过余额） */
    @PostMapping("/handle")
    @Log("积分办理")
    public Result<Map<String, Object>> handle(@RequestBody ScoreDTO dto) {
        if (dto.getStudentId() == null) return Result.error("请选择学员");
        if (dto.getPoints() == null || dto.getPoints().compareTo(BigDecimal.ZERO) <= 0)
            return Result.error("积分必须大于 0");
        if (dto.getType() == null || (dto.getType() != 1 && dto.getType() != 2))
            return Result.error("办理类型不合法");
        EduStudent s = studentMapper.selectById(dto.getStudentId());
        if (s == null || s.getDeleted() == 1) return Result.error("学员不存在或已删除");
        // 重复提交防护：同一用户5秒内完全相同的积分请求只放行一次
        com.edu.security.LoginUser u = AuthContext.get();
        String fp = u.getTenantId() + ":" + u.getId() + ":score:"
                + dto.getStudentId() + ":" + dto.getType() + ":" + dto.getPoints();
        if (!repeatGuard.tryAcquire(fp))
            return Result.error("积分办理已提交，请勿重复提交");
        if (dto.getType() == 2) {
            Map<String, Object> sum = summaryOf(dto.getStudentId());
            BigDecimal total = (BigDecimal) sum.get("total");
            if (dto.getPoints().compareTo(total) > 0)
                return Result.error("兑换积分 " + dto.getPoints().stripTrailingZeros().toPlainString()
                        + " 超过剩余积分 " + total.stripTrailingZeros().toPlainString());
        }
        EduScoreLog log = new EduScoreLog();
        log.setTenantId(s.getTenantId());
        log.setStudentId(dto.getStudentId());
        log.setType(dto.getType());
        log.setPoints(dto.getPoints());
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
        log.setRemark((dto.getType() == 1 ? "增加积分 " : "积分兑换 ")
                + dto.getPoints().stripTrailingZeros().toPlainString() + " 分"
                + (userRemark.isEmpty() ? "" : "：" + userRemark));
        scoreMapper.insert(log);
        Map<String, Object> r = summaryOf(dto.getStudentId());
        r.put("id", log.getId());
        return Result.ok(r);
    }

    /** 积分流水编辑（办理记录-积分）：仅经办信息可改，积分数值与类型不可改 */
    @PutMapping("/log")
    @Log("编辑积分流水")
    public Result<Void> updateLog(@RequestBody ScoreLogUpdateDTO dto) {
        if (dto.getId() == null) return Result.error("缺少流水ID");
        EduScoreLog l = scoreMapper.selectById(dto.getId());
        if (l == null) return Result.error("积分流水不存在");
        if (dto.getCampus() != null) l.setCampus(dto.getCampus());
        if (dto.getSource() != null) l.setSource(dto.getSource());
        if (dto.getHandleDate() != null) l.setHandleDate(dto.getHandleDate());
        if (dto.getOrderTaker() != null) l.setOrderTaker(dto.getOrderTaker());
        if (dto.getConsultant() != null) l.setConsultant(dto.getConsultant());
        scoreMapper.updateById(l);
        return Result.ok();
    }

    @Data
    public static class ScoreDTO {
        private Long studentId;
        /** 1增加积分 2积分兑换 */
        private Integer type;
        /** 本次积分（正数） */
        private BigDecimal points;
        private String campus;
        private String source;
        private String handleDate;
        private String orderTaker;
        private String consultant;
        private String remark;
    }

    @Data
    public static class ScoreLogUpdateDTO {
        private Long id;
        private String campus;
        private String source;
        private LocalDate handleDate;
        private String orderTaker;
        private String consultant;
    }
}
