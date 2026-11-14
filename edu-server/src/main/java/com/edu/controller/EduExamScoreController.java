package com.edu.controller;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.edu.common.Log;
import com.edu.common.Result;
import com.edu.entity.EduClazz;
import com.edu.entity.EduExamScore;
import com.edu.entity.EduStudent;
import com.edu.mapper.EduClazzMapper;
import com.edu.mapper.EduExamScoreMapper;
import com.edu.mapper.EduStudentMapper;
import com.edu.security.AuthContext;
import com.edu.security.RequiresPerm;
import lombok.Data;
import lombok.RequiredArgsConstructor;
import org.springframework.util.StringUtils;
import org.springframework.web.bind.annotation.*;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * 考试成绩（班级管理-成绩TAB）：按班级列出学员成绩，支持整班批量录入（一次考试多名学员）、
 * 单条编辑/删除。列表按考试日期倒序、成绩倒序展示；考试名称/学员姓名前端筛选。
 */
@RestController
@RequestMapping("/edu/exam-score")
@RequiredArgsConstructor
@RequiresPerm("edu:clazz")
public class EduExamScoreController {

    private final EduExamScoreMapper scoreMapper;
    private final EduStudentMapper studentMapper;
    private final EduClazzMapper clazzMapper;

    /** 班级成绩列表（附带学员姓名/联系方式；examName 精确、stuName 模糊由前端筛选） */
    @GetMapping("/list")
    public Result<List<Map<String, Object>>> list(@RequestParam Long clazzId) {
        List<EduExamScore> rows = scoreMapper.selectList(new LambdaQueryWrapper<EduExamScore>()
                .eq(EduExamScore::getClazzId, clazzId)
                .orderByDesc(EduExamScore::getExamDate)
                .orderByDesc(EduExamScore::getScore));
        Set<Long> sids = new HashSet<>();
        for (EduExamScore r : rows) sids.add(r.getStudentId());
        Map<Long, EduStudent> stus = new HashMap<>();
        if (!sids.isEmpty()) for (EduStudent s : studentMapper.selectBatchIds(sids)) stus.put(s.getId(), s);
        List<Map<String, Object>> out = new ArrayList<>();
        for (EduExamScore r : rows) {
            EduStudent s = stus.get(r.getStudentId());
            Map<String, Object> m = new HashMap<>();
            m.put("id", r.getId());
            m.put("studentId", r.getStudentId());
            m.put("stuName", s == null ? "-" : s.getName());
            m.put("phone", s == null ? null : (s.getParentPhone() == null ? s.getBackupPhone() : s.getParentPhone()));
            m.put("item", r.getItem());
            m.put("score", r.getScore());
            m.put("source", r.getSource());
            m.put("examDate", r.getExamDate() == null ? null : r.getExamDate().toString());
            m.put("examName", r.getExamName());
            m.put("remark", r.getRemark());
            m.put("createTime", r.getCreateTime() == null ? null
                    : r.getCreateTime().format(java.time.format.DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm")));
            out.add(m);
        }
        return Result.ok(out);
    }

    /** 批量录入成绩：一次考试（名称/日期/项目固定）多名学员各自成绩，空成绩跳过 */
    @PostMapping("/save-batch")
    @Log("录入成绩")
    public Result<Integer> saveBatch(@RequestBody BatchDTO dto) {
        if (dto.getClazzId() == null) return Result.error("缺少班级");
        if (!StringUtils.hasText(dto.getExamName())) return Result.error("请填写考试名称");
        if (dto.getRows() == null || dto.getRows().isEmpty()) return Result.error("请填写学员成绩");
        EduClazz clazz = clazzMapper.selectById(dto.getClazzId());
        if (clazz == null) return Result.error("班级不存在");
        LocalDate examDate = parseDate(dto.getExamDate());
        int count = 0;
        for (BatchDTO.RowDTO row : dto.getRows()) {
            if (row.getStudentId() == null || row.getScore() == null) continue;
            EduExamScore s = new EduExamScore();
            s.setTenantId(clazz.getTenantId());
            s.setClazzId(dto.getClazzId());
            s.setStudentId(row.getStudentId());
            s.setExamName(dto.getExamName().trim());
            s.setItem(StringUtils.hasText(dto.getItem()) ? dto.getItem().trim() : null);
            s.setScore(row.getScore());
            s.setSource(StringUtils.hasText(dto.getSource()) ? dto.getSource().trim() : "机构内");
            s.setExamDate(examDate);
            s.setRemark(StringUtils.hasText(row.getRemark()) ? row.getRemark().trim() : null);
            s.setOperator(AuthContext.get() == null ? null : AuthContext.get().getRealName());
            scoreMapper.insert(s);
            count++;
        }
        if (count == 0) return Result.error("没有可录入的成绩（学员成绩为空）");
        return Result.ok(count);
    }

    /** 编辑单条成绩：项目/成绩/考试日期/备注可改 */
    @PostMapping("/{id}/update")
    @Log("编辑成绩")
    public Result<Void> update(@PathVariable Long id, @RequestBody BatchDTO.RowDTORaw dto) {
        EduExamScore s = scoreMapper.selectById(id);
        if (s == null) return Result.error("成绩记录不存在");
        if (dto.getItem() != null) s.setItem(dto.getItem().trim().isEmpty() ? null : dto.getItem().trim());
        if (dto.getScore() != null) s.setScore(dto.getScore());
        if (dto.getExamDate() != null) s.setExamDate(parseDate(dto.getExamDate()));
        if (dto.getRemark() != null) s.setRemark(dto.getRemark().trim().isEmpty() ? null : dto.getRemark().trim());
        s.setUpdateTime(LocalDateTime.now());
        scoreMapper.updateById(s);
        return Result.ok();
    }

    /** 删除单条成绩 */
    @PostMapping("/{id}/delete")
    @Log("删除成绩")
    public Result<Void> delete(@PathVariable Long id) {
        scoreMapper.deleteById(id);
        return Result.ok();
    }

    private LocalDate parseDate(String d) {
        if (!StringUtils.hasText(d)) return LocalDate.now();
        try { return LocalDate.parse(d.length() > 10 ? d.substring(0, 10) : d); }
        catch (Exception e) { return LocalDate.now(); }
    }

    @Data
    public static class BatchDTO {
        private Long clazzId;
        private String examName;
        private String examDate;
        private String item;
        /** 来源：机构内/机构外（缺省机构内） */
        private String source;
        private List<RowDTO> rows;

        @Data
        public static class RowDTO {
            private Long studentId;
            private BigDecimal score;
            private String remark;
        }

        /** 编辑单条入参（examDate 字符串） */
        @Data
        public static class RowDTORaw {
            private String item;
            private BigDecimal score;
            private String examDate;
            private String remark;
        }
    }
}
