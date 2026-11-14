package com.edu.controller;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.edu.common.Log;
import com.edu.common.Result;
import com.edu.entity.EduEnroll;
import com.edu.entity.EduHomework;
import com.edu.entity.EduHomeworkSubmit;
import com.edu.entity.EduStudent;
import com.edu.mapper.EduEnrollMapper;
import com.edu.mapper.EduHomeworkMapper;
import com.edu.mapper.EduHomeworkSubmitMapper;
import com.edu.mapper.EduStudentMapper;
import com.edu.security.RequiresPerm;
import lombok.Data;
import lombok.RequiredArgsConstructor;
import org.springframework.util.StringUtils;
import org.springframework.web.bind.annotation.*;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * 班级作业（班级管理-作业TAB）：布置作业 + 学员提交/教师点评。
 * 提交与点评当前由教师在"查看提交"弹窗代录（家长端提交入口规划中），已读标记为家长端预留。
 */
@RestController
@RequestMapping("/edu/homework")
@RequiredArgsConstructor
@RequiresPerm("edu:clazz")
public class EduHomeworkController {

    private final EduHomeworkMapper homeworkMapper;
    private final EduHomeworkSubmitMapper submitMapper;
    private final EduEnrollMapper enrollMapper;
    private final EduStudentMapper studentMapper;

    /** 班级作业列表：逐作业统计 已提交/已点评/未读/未提交 + 学员提交明细（在读学员全列） */
    @GetMapping("/by-clazz")
    public Result<List<Map<String, Object>>> byClazz(@RequestParam Long clazzId) {
        DateTimeFormatter dtf = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm");
        List<EduHomework> hws = homeworkMapper.selectList(new LambdaQueryWrapper<EduHomework>()
                .eq(EduHomework::getClazzId, clazzId).orderByDesc(EduHomework::getCreateTime));
        if (hws.isEmpty()) return Result.ok(new ArrayList<>());
        Set<Long> hids = new HashSet<>();
        for (EduHomework h : hws) hids.add(h.getId());

        Map<Long, Map<Long, EduHomeworkSubmit>> subs = new HashMap<>();   // homeworkId -> studentId -> submit
        for (EduHomeworkSubmit s : submitMapper.selectList(new LambdaQueryWrapper<EduHomeworkSubmit>()
                .in(EduHomeworkSubmit::getHomeworkId, hids)))
            subs.computeIfAbsent(s.getHomeworkId(), k -> new HashMap<>()).put(s.getStudentId(), s);

        // 应交名单：默认全班在读；作业定向发送时按 targetStudents
        List<EduEnroll> enrolls = enrollMapper.selectList(new LambdaQueryWrapper<EduEnroll>()
                .eq(EduEnroll::getClazzId, clazzId).eq(EduEnroll::getStatus, 1));
        Map<Long, String> names = new HashMap<>();
        Set<Long> sids = new HashSet<>();
        for (EduEnroll e : enrolls) sids.add(e.getStudentId());
        for (EduHomework h : hws)
            if (StringUtils.hasText(h.getTargetStudents()))
                for (String t : h.getTargetStudents().split(","))
                    try { sids.add(Long.parseLong(t.trim())); } catch (NumberFormatException ignore) { }
        if (!sids.isEmpty()) for (EduStudent st : studentMapper.selectBatchIds(sids)) names.put(st.getId(), st.getName());

        List<Map<String, Object>> rows = new ArrayList<>();
        for (EduHomework h : hws) {
            // 本作业应交学员：定向名单 或 全班在读
            List<Long> targets = new ArrayList<>();
            if (StringUtils.hasText(h.getTargetStudents())) {
                for (String t : h.getTargetStudents().split(","))
                    try { targets.add(Long.parseLong(t.trim())); } catch (NumberFormatException ignore) { }
            } else {
                for (EduEnroll e : enrolls) targets.add(e.getStudentId());
            }
            Map<Long, EduHomeworkSubmit> sm = subs.getOrDefault(h.getId(), new HashMap<>());
            int submitted = 0, reviewed = 0, unread = 0;
            List<Map<String, Object>> students = new ArrayList<>();
            for (Long sid : targets) {
                EduHomeworkSubmit s = sm.get(sid);
                if (s != null) {
                    submitted++;
                    if (StringUtils.hasText(s.getReviewContent())) reviewed++;
                    if (s.getReadFlag() == null || s.getReadFlag() == 0) unread++;
                }
                Map<String, Object> st = new HashMap<>();
                st.put("studentId", sid);
                st.put("stuName", names.getOrDefault(sid, "-"));
                st.put("submitted", s != null);
                st.put("content", s == null ? null : s.getContent());
                st.put("reviewContent", s == null ? null : s.getReviewContent());
                st.put("readFlag", s == null || s.getReadFlag() == null ? 0 : s.getReadFlag());
                students.add(st);
            }
            Map<String, Object> r = new HashMap<>();
            r.put("id", h.getId());
            r.put("title", h.getTitle());
            r.put("content", h.getContent());
            r.put("images", h.getImages());
            r.put("videos", h.getVideos());
            r.put("audios", h.getAudios());
            r.put("teacherId", h.getTeacherId());
            r.put("targetStudents", h.getTargetStudents());
            r.put("deadline", h.getDeadline() == null ? null : h.getDeadline().toString());
            r.put("status", h.getStatus());
            r.put("visibleAll", h.getVisibleAll());
            r.put("parentSubmit", h.getParentSubmit());
            r.put("createTime", h.getCreateTime() == null ? null : h.getCreateTime().format(dtf));
            r.put("studying", targets.size());
            r.put("submitted", submitted);
            r.put("reviewed", reviewed);
            r.put("unread", unread);
            r.put("unsubmitted", Math.max(targets.size() - submitted, 0));
            r.put("students", students);
            rows.add(r);
        }
        return Result.ok(rows);
    }

    /** 布置作业（status 0发布 / 2草稿） */
    @PostMapping("/create")
    @Log("布置作业")
    public Result<Void> create(@RequestBody HwDTO dto) {
        if (dto.getClazzId() == null) return Result.error("缺少班级");
        if (!StringUtils.hasText(dto.getTitle())) return Result.error("请填写作业标题");
        EduHomework h = new EduHomework();
        applyDto(h, dto);
        h.setClazzId(dto.getClazzId());
        h.setStatus(dto.getStatus() != null && dto.getStatus() == 2 ? 2 : 0);
        homeworkMapper.insert(h);
        return Result.ok();
    }

    /** 编辑作业（标题/内容/附件/发送学员/截止/开关/发布） */
    @PostMapping("/{id}/update")
    @Log("编辑作业")
    public Result<Void> update(@PathVariable Long id, @RequestBody HwDTO dto) {
        EduHomework h = homeworkMapper.selectById(id);
        if (h == null) return Result.error("作业不存在");
        applyDto(h, dto);
        h.setUpdateTime(LocalDateTime.now());
        homeworkMapper.updateById(h);
        return Result.ok();
    }

    /** DTO → 实体公共赋值（null 字段不覆盖，targets 列表转逗号串） */
    private void applyDto(EduHomework h, HwDTO dto) {
        if (StringUtils.hasText(dto.getTitle())) h.setTitle(dto.getTitle().trim());
        if (dto.getContent() != null) h.setContent(dto.getContent().trim().isEmpty() ? null : dto.getContent().trim());
        if (dto.getImages() != null) h.setImages(dto.getImages());
        if (dto.getVideos() != null) h.setVideos(dto.getVideos());
        if (dto.getAudios() != null) h.setAudios(dto.getAudios());
        if (dto.getTeacherId() != null) h.setTeacherId(dto.getTeacherId());
        if (dto.getTargets() != null)
            h.setTargetStudents(dto.getTargets().isEmpty() ? null
                    : dto.getTargets().stream().map(String::valueOf).collect(java.util.stream.Collectors.joining(",")));
        if (dto.getDeadline() != null) h.setDeadline(parseDate(dto.getDeadline()));
        if (dto.getStatus() != null) h.setStatus(dto.getStatus());
        if (dto.getVisibleAll() != null) h.setVisibleAll(dto.getVisibleAll());
        if (dto.getParentSubmit() != null) h.setParentSubmit(dto.getParentSubmit());
    }

    /** 删除作业（连同提交记录） */
    @PostMapping("/{id}/delete")
    @Log("删除作业")
    public Result<Void> delete(@PathVariable Long id) {
        submitMapper.delete(new LambdaQueryWrapper<EduHomeworkSubmit>()
                .eq(EduHomeworkSubmit::getHomeworkId, id));
        homeworkMapper.deleteById(id);
        return Result.ok();
    }

    /** 保存学员提交/教师点评：按（作业,学员）upsert；取消提交则删除该行 */
    @PostMapping("/submit-save")
    @Log("作业提交登记")
    public Result<Integer> submitSave(@RequestBody SubmitDTO dto) {
        if (dto.getHomeworkId() == null) return Result.error("缺少作业");
        EduHomework h = homeworkMapper.selectById(dto.getHomeworkId());
        if (h == null) return Result.error("作业不存在");
        int count = 0;
        for (RowDTO row : dto.getRows()) {
            if (row.getStudentId() == null) continue;
            EduHomeworkSubmit s = submitMapper.selectOne(new LambdaQueryWrapper<EduHomeworkSubmit>()
                    .eq(EduHomeworkSubmit::getHomeworkId, dto.getHomeworkId())
                    .eq(EduHomeworkSubmit::getStudentId, row.getStudentId())
                    .last("LIMIT 1"));
            if (!Boolean.TRUE.equals(row.getSubmitted())) {   // 未提交：删除既有提交
                if (s != null) submitMapper.deleteById(s.getId());
                continue;
            }
            if (s == null) {
                s = new EduHomeworkSubmit();
                s.setTenantId(h.getTenantId());
                s.setHomeworkId(h.getId());
                s.setStudentId(row.getStudentId());
                s.setSubmitTime(LocalDateTime.now());
                s.setReadFlag(0);
            }
            s.setContent(StringUtils.hasText(row.getContent()) ? row.getContent().trim() : null);
            String review = StringUtils.hasText(row.getReviewContent()) ? row.getReviewContent().trim() : null;
            boolean reviewChanged = review == null
                    ? StringUtils.hasText(s.getReviewContent())
                    : !review.equals(s.getReviewContent());
            s.setReviewContent(review);
            if (reviewChanged) s.setReviewTime(review == null ? null : LocalDateTime.now());
            if (s.getId() == null) submitMapper.insert(s);
            else submitMapper.updateById(s);
            count++;
        }
        return Result.ok(count);
    }

    private LocalDate parseDate(String d) {
        if (!StringUtils.hasText(d)) return null;
        try { return LocalDate.parse(d.length() > 10 ? d.substring(0, 10) : d); }
        catch (Exception e) { return null; }
    }

    @Data
    public static class HwDTO {
        private Long clazzId;
        private String title;
        private String content;
        /** 附件 JSON 字符串（[{url,name}]） */
        private String images;
        private String videos;
        private String audios;
        private Long teacherId;
        /** 发送学员ID集（空列表=全班在读） */
        private List<Long> targets;
        private String deadline;
        /** 0发布 1关闭 2草稿 */
        private Integer status;
        private Integer visibleAll;
        private Integer parentSubmit;
    }

    @Data
    public static class SubmitDTO {
        private Long homeworkId;
        private List<RowDTO> rows;
    }

    @Data
    public static class RowDTO {
        private Long studentId;
        private Boolean submitted;
        private String content;
        private String reviewContent;
    }
}
