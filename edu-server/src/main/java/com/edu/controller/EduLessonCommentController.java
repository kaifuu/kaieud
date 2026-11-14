package com.edu.controller;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.edu.common.Log;
import com.edu.common.Result;
import com.edu.entity.EduLessonComment;
import com.edu.entity.EduSchedule;
import com.edu.entity.EduStudent;
import com.edu.entity.EduConsume;
import com.edu.mapper.EduConsumeMapper;
import com.edu.mapper.EduLessonCommentMapper;
import com.edu.mapper.EduScheduleMapper;
import com.edu.mapper.EduStudentMapper;
import com.edu.security.RequiresPerm;
import lombok.Data;
import lombok.RequiredArgsConstructor;
import org.springframework.util.StringUtils;
import org.springframework.web.bind.annotation.*;

import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * 课堂点评（班级管理-课堂点评TAB）：按课次聚合展示到课人数/点评状态/小红花/阅读率；
 * 点评=给到课学员逐个发小红花+文字点评，按（课次,学员）upsert。
 */
@RestController
@RequestMapping("/edu/lesson-comment")
@RequiredArgsConstructor
@RequiresPerm("edu:clazz")
public class EduLessonCommentController {

    private final EduLessonCommentMapper commentMapper;
    private final EduScheduleMapper scheduleMapper;
    private final EduConsumeMapper consumeMapper;
    private final EduStudentMapper studentMapper;

    /** 班级课堂点评：已记上课课次（status=1）逐课次聚合 + 到课学员名单 + 已点评明细 */
    @GetMapping("/by-clazz")
    public Result<List<Map<String, Object>>> byClazz(@RequestParam Long clazzId) {
        DateTimeFormatter dtf = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss");
        List<EduSchedule> schedules = scheduleMapper.selectList(new LambdaQueryWrapper<EduSchedule>()
                .eq(EduSchedule::getClazzId, clazzId)
                .eq(EduSchedule::getStatus, 1)
                .orderByDesc(EduSchedule::getStartTime));
        if (schedules.isEmpty()) return Result.ok(new ArrayList<>());
        Set<Long> sids = new HashSet<>();
        for (EduSchedule s : schedules) sids.add(s.getId());

        // 到课（消课未撤销未驳回）→ 每课次到课学员
        Map<Long, List<Long>> attendStu = new HashMap<>();
        for (EduConsume c : consumeMapper.selectList(new LambdaQueryWrapper<EduConsume>()
                .in(EduConsume::getScheduleId, sids)
                .eq(EduConsume::getCanceled, 0)
                .and(w -> w.isNull(EduConsume::getAuditStatus).or().ne(EduConsume::getAuditStatus, 2)))) {
            attendStu.computeIfAbsent(c.getScheduleId(), k -> new ArrayList<>()).add(c.getStudentId());
        }
        // 点评记录 → 每课次明细
        Map<Long, List<Map<String, Object>>> cmts = new HashMap<>();
        for (EduLessonComment c : commentMapper.selectList(new LambdaQueryWrapper<EduLessonComment>()
                .in(EduLessonComment::getScheduleId, sids))) {
            Map<String, Object> m = new HashMap<>();
            m.put("studentId", c.getStudentId());
            m.put("flowers", c.getFlowers());
            m.put("content", c.getContent());
            m.put("readFlag", c.getReadFlag());
            cmts.computeIfAbsent(c.getScheduleId(), k -> new ArrayList<>()).add(m);
        }
        // 学员姓名
        Set<Long> stuIds = new HashSet<>();
        for (List<Long> l : attendStu.values()) stuIds.addAll(l);
        for (List<Map<String, Object>> l : cmts.values())
            for (Map<String, Object> m : l) stuIds.add((Long) m.get("studentId"));
        Map<Long, String> names = new HashMap<>();
        if (!stuIds.isEmpty()) for (EduStudent s : studentMapper.selectBatchIds(stuIds)) names.put(s.getId(), s.getName());

        List<Map<String, Object>> rows = new ArrayList<>();
        for (EduSchedule s : schedules) {
            List<Long> att = attendStu.getOrDefault(s.getId(), new ArrayList<>());
            List<Map<String, Object>> cs = cmts.getOrDefault(s.getId(), new ArrayList<>());
            int flowers = 0, read = 0;
            for (Map<String, Object> c : cs) {
                flowers += c.get("flowers") == null ? 0 : ((Number) c.get("flowers")).intValue();
                if (Integer.valueOf(1).equals(c.get("readFlag"))) read++;
            }
            List<Map<String, Object>> attend = new ArrayList<>();
            for (Long sid : att) {
                Map<String, Object> a = new HashMap<>();
                a.put("studentId", sid);
                a.put("stuName", names.getOrDefault(sid, "-"));
                attend.add(a);
            }
            Map<String, Object> r = new HashMap<>();
            r.put("scheduleId", s.getId());
            r.put("startTime", s.getStartTime() == null ? null : s.getStartTime().format(dtf));
            r.put("endTime", s.getEndTime() == null ? null : s.getEndTime().format(dtf));
            r.put("teacherId", s.getTeacherId());
            r.put("assistantId", s.getAssistantId());
            r.put("attend", att.size());
            r.put("attendStudents", attend);
            r.put("flowers", flowers);
            r.put("commented", !cs.isEmpty());
            r.put("readRate", cs.isEmpty() ? "-" : read + "/" + cs.size());
            r.put("comments", cs);
            rows.add(r);
        }
        return Result.ok(rows);
    }

    /** 保存点评：按（课次,学员）upsert 小红花与内容；内容与小红花全空的行跳过 */
    @PostMapping("/save-batch")
    @Log("课堂点评")
    public Result<Integer> saveBatch(@RequestBody BatchDTO dto) {
        if (dto.getScheduleId() == null) return Result.error("缺少课次");
        if (dto.getRows() == null || dto.getRows().isEmpty()) return Result.error("请填写点评内容");
        EduSchedule sch = scheduleMapper.selectById(dto.getScheduleId());
        if (sch == null) return Result.error("课次不存在");
        int count = 0;
        for (RowDTO row : dto.getRows()) {
            if (row.getStudentId() == null) continue;
            boolean empty = (row.getFlowers() == null || row.getFlowers() == 0)
                    && !StringUtils.hasText(row.getContent());
            if (empty) continue;
            EduLessonComment c = commentMapper.selectOne(new LambdaQueryWrapper<EduLessonComment>()
                    .eq(EduLessonComment::getScheduleId, dto.getScheduleId())
                    .eq(EduLessonComment::getStudentId, row.getStudentId())
                    .last("LIMIT 1"));
            if (c == null) {
                c = new EduLessonComment();
                c.setTenantId(sch.getTenantId());
                c.setClazzId(sch.getClazzId());
                c.setScheduleId(sch.getId());
                c.setStudentId(row.getStudentId());
                c.setTeacherId(sch.getTeacherId());
                c.setCreateTime(LocalDateTime.now());
            }
            c.setFlowers(row.getFlowers() == null ? 0 : row.getFlowers());
            c.setContent(StringUtils.hasText(row.getContent()) ? row.getContent().trim() : null);
            c.setUpdateTime(LocalDateTime.now());
            if (c.getId() == null) commentMapper.insert(c);
            else commentMapper.updateById(c);
            count++;
        }
        if (count == 0) return Result.error("没有可保存的点评（内容与小红花均为空）");
        return Result.ok(count);
    }

    @Data
    public static class BatchDTO {
        private Long scheduleId;
        private List<RowDTO> rows;
    }

    @Data
    public static class RowDTO {
        private Long studentId;
        private Integer flowers;
        private String content;
    }
}
