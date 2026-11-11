package com.edu.controller;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.edu.common.Log;
import com.edu.common.PageResult;
import com.edu.common.Result;
import com.edu.common.SafeSort;
import com.edu.entity.EduAudition;
import com.edu.mapper.EduAuditionMapper;
import com.edu.security.AuthContext;
import com.edu.security.RequiresPerm;
import lombok.RequiredArgsConstructor;
import org.springframework.util.StringUtils;
import org.springframework.web.bind.annotation.*;

import java.util.List;

/** 试听管理：插班试听（关联已成班课）/ 开班试听（全新录入） */
@RestController
@RequestMapping("/edu/audition")
@RequiredArgsConstructor
@RequiresPerm("edu:audition")
public class EduAuditionController {

    private final EduAuditionMapper auditionMapper;
    private final com.edu.mapper.EduAuditionStudentMapper auditionStudentMapper;
    private final com.edu.mapper.EduStudentMapper studentMapper;
    private final com.edu.mapper.EduClassroomMapper classroomMapper;

    /** 列表：类型/关键字筛选；附 已报名/空余/超员 与教室最低/最多容纳人数 */
    @GetMapping("/page")
    public Result<PageResult<java.util.Map<String, Object>>> page(@RequestParam(defaultValue = "1") long page,
                                                @RequestParam(defaultValue = "10") long size,
                                                @RequestParam(required = false) Integer type,
                                                @RequestParam(required = false) String keyword,
                                                @RequestParam(required = false) String sortBy,
                                                @RequestParam(required = false) String direction) {
        Page<EduAudition> p = auditionMapper.selectPage(new Page<>(page, size),
                new LambdaQueryWrapper<EduAudition>()
                        .eq(type != null, EduAudition::getType, type)
                        .and(StringUtils.hasText(keyword), w -> w
                                .like(EduAudition::getName, keyword)
                                .or().like(EduAudition::getCourseName, keyword)
                                .or().like(EduAudition::getTeacherName, keyword))
                        .last(SafeSort.order(sortBy, direction, "id",
                                "id", "name", "type", "auditionDate", "plannedCount", "createTime")));
        // 已报名人数：按 audition_id 分组计数
        java.util.Map<Long, Integer> registered = new java.util.HashMap<>();
        java.util.Set<Long> aids = new java.util.HashSet<>();
        for (EduAudition a : p.getRecords()) aids.add(a.getId());
        if (!aids.isEmpty())
            for (com.edu.entity.EduAuditionStudent s : auditionStudentMapper.selectList(
                    new LambdaQueryWrapper<com.edu.entity.EduAuditionStudent>()
                            .in(com.edu.entity.EduAuditionStudent::getAuditionId, aids)
                            .select(com.edu.entity.EduAuditionStudent::getAuditionId)))
                registered.merge(s.getAuditionId(), 1, Integer::sum);
        // 教室容纳：按教室名匹配（试听活动存教室名快照）
        java.util.Map<String, com.edu.entity.EduClassroom> rooms = new java.util.HashMap<>();
        for (com.edu.entity.EduClassroom r : classroomMapper.selectList(null))
            rooms.put(r.getName(), r);
        List<java.util.Map<String, Object>> rows = new java.util.ArrayList<>();
        for (EduAudition a : p.getRecords()) {
            java.util.Map<String, Object> m = new java.util.LinkedHashMap<>();
            m.put("id", a.getId());
            m.put("type", a.getType());
            m.put("name", a.getName());
            m.put("auditionDate", a.getAuditionDate() == null ? null : a.getAuditionDate().toString());
            m.put("campus", a.getCampus());
            m.put("courseName", a.getCourseName());
            m.put("teacherName", a.getTeacherName());
            m.put("classroomName", a.getClassroomName());
            m.put("timePeriod", a.getTimePeriod());
            m.put("assistantName", a.getAssistantName());
            m.put("plannedCount", a.getPlannedCount());
            m.put("relateClazzId", a.getRelateClazzId());
            m.put("remark", a.getRemark());
            m.put("operator", a.getOperator());
            int reg = registered.getOrDefault(a.getId(), 0);
            int planned = a.getPlannedCount() == null ? 0 : a.getPlannedCount();
            m.put("registeredCount", reg);
            m.put("freeCount", Math.max(planned - reg, 0));
            m.put("overCount", Math.max(reg - planned, 0));
            com.edu.entity.EduClassroom room = a.getClassroomName() == null ? null : rooms.get(a.getClassroomName());
            m.put("roomMin", room == null ? null : room.getMinCapacity());
            m.put("roomMax", room == null ? null : room.getCapacity());
            rows.add(m);
        }
        return Result.ok(PageResult.of(p.getTotal(), rows));
    }

    /** 全量下拉用 */
    @GetMapping("/list")
    public Result<List<EduAudition>> list() {
        return Result.ok(auditionMapper.selectList(
                new LambdaQueryWrapper<EduAudition>().orderByDesc(EduAudition::getId)));
    }

    @PostMapping
    @Log("新增试听")
    public Result<Void> save(@RequestBody EduAudition a) {
        if (!StringUtils.hasText(a.getName())) return Result.error("名称为必填项");
        a.setId(null);
        a.setTenantId(AuthContext.get().getTenantId());
        a.setOperator(AuthContext.get().getRealName());
        auditionMapper.insert(a);
        return Result.ok();
    }

    @PutMapping
    @Log("修改试听")
    public Result<Void> update(@RequestBody EduAudition a) {
        if (a.getId() == null) return Result.error("缺少ID");
        if (!StringUtils.hasText(a.getName())) return Result.error("名称为必填项");
        auditionMapper.updateById(a);
        return Result.ok();
    }

    @DeleteMapping("/{id}")
    @Log("删除试听")
    public Result<Void> delete(@PathVariable Long id) {
        auditionMapper.deleteById(id);
        return Result.ok();
    }

    /** 试听活动登记学员（分页/筛选/排序）：keyword=学员姓名/手机号，登记时间范围，列排序 */
    @GetMapping("/{id}/students")
    public Result<PageResult<java.util.Map<String, Object>>> students(
            @PathVariable Long id,
            @RequestParam(defaultValue = "1") long page,
            @RequestParam(defaultValue = "10") long size,
            @RequestParam(required = false) String keyword,
            @RequestParam(required = false) String begin,
            @RequestParam(required = false) String end,
            @RequestParam(required = false) String sortBy,
            @RequestParam(required = false) String direction) {
        LambdaQueryWrapper<com.edu.entity.EduAuditionStudent> qw = new LambdaQueryWrapper<com.edu.entity.EduAuditionStudent>()
                .eq(com.edu.entity.EduAuditionStudent::getAuditionId, id)
                .ge(StringUtils.hasText(begin), com.edu.entity.EduAuditionStudent::getCreateTime, begin + " 00:00:00")
                .le(StringUtils.hasText(end), com.edu.entity.EduAuditionStudent::getCreateTime, end + " 23:59:59");
        // 关键字：学员姓名直接匹配；手机号先解析学员ID集合再过滤（手机号存学员档案）
        if (StringUtils.hasText(keyword)) {
            String k = keyword.trim();
            java.util.Set<Long> ids = new java.util.HashSet<>();
            for (com.edu.entity.EduStudent s : studentMapper.selectList(
                    new LambdaQueryWrapper<com.edu.entity.EduStudent>()
                            .like(com.edu.entity.EduStudent::getName, k)
                            .or().like(com.edu.entity.EduStudent::getParentPhone, k)
                            .or().like(com.edu.entity.EduStudent::getBackupPhone, k)))
                ids.add(s.getId());
            java.util.Set<Long> finalIds = ids;
            qw.and(w -> w.like(com.edu.entity.EduAuditionStudent::getStudentName, k)
                    .or(finalIds.size() > 0, x -> x.in(com.edu.entity.EduAuditionStudent::getStudentId, finalIds)));
        }
        qw.last(SafeSort.order(sortBy, direction, "id", "id", "studentName", "auditionDate", "createTime"));
        Page<com.edu.entity.EduAuditionStudent> p = auditionStudentMapper.selectPage(new Page<>(page, size), qw);
        // 手机号映射（家长电话→备用电话）
        java.util.Map<Long, String> phones = new java.util.HashMap<>();
        java.util.Set<Long> sids = new java.util.HashSet<>();
        for (com.edu.entity.EduAuditionStudent s : p.getRecords()) if (s.getStudentId() != null) sids.add(s.getStudentId());
        if (!sids.isEmpty())
            for (com.edu.entity.EduStudent s : studentMapper.selectBatchIds(sids))
                phones.put(s.getId(), s.getParentPhone() != null ? s.getParentPhone() : s.getBackupPhone());
        List<java.util.Map<String, Object>> rows = new java.util.ArrayList<>();
        for (com.edu.entity.EduAuditionStudent s : p.getRecords()) {
            java.util.Map<String, Object> m = new java.util.LinkedHashMap<>();
            m.put("id", s.getId());
            m.put("studentId", s.getStudentId());
            m.put("studentName", s.getStudentName());
            m.put("studentPhone", phones.get(s.getStudentId()));
            m.put("source", s.getSource());
            m.put("courseName", s.getCourseName());
            m.put("teacherName", s.getTeacherName());
            m.put("classroomName", s.getClassroomName());
            m.put("timePeriod", s.getTimePeriod());
            m.put("auditionDate", s.getAuditionDate() == null ? null : s.getAuditionDate().toString());
            m.put("operator", s.getOperator());
            m.put("createTime", s.getCreateTime() == null ? null : s.getCreateTime().toString());
            rows.add(m);
        }
        return Result.ok(PageResult.of(p.getTotal(), rows));
    }

    /**
     * 试听活动直接登记学员（活动维度，无需关联班级，开班试听同样可登记）：
     * 快照取活动信息，按活动+学员去重。
     */
    @PostMapping("/{id}/students-batch")
    @Log("试听活动登记学员")
    public Result<String> addStudentsBatch(@PathVariable Long id, @RequestBody StudentsBatchDTO dto) {
        EduAudition a = auditionMapper.selectById(id);
        if (a == null) return Result.error("试听活动不存在");
        if (dto.getStudentIds() == null || dto.getStudentIds().isEmpty()) return Result.error("请先选择学员");
        java.util.Map<Long, String> names = new java.util.HashMap<>();
        for (com.edu.entity.EduStudent s : studentMapper.selectBatchIds(dto.getStudentIds()))
            names.put(s.getId(), s.getName());
        java.util.Set<Long> existing = new java.util.HashSet<>();
        for (com.edu.entity.EduAuditionStudent s : auditionStudentMapper.selectList(
                new LambdaQueryWrapper<com.edu.entity.EduAuditionStudent>()
                        .eq(com.edu.entity.EduAuditionStudent::getAuditionId, id)
                        .select(com.edu.entity.EduAuditionStudent::getStudentId)))
            existing.add(s.getStudentId());
        int ok = 0;
        for (Long sid : dto.getStudentIds()) {
            if (sid == null || existing.contains(sid)) continue;
            com.edu.entity.EduAuditionStudent s = new com.edu.entity.EduAuditionStudent();
            s.setTenantId(a.getTenantId());
            s.setClazzId(null);
            s.setStudentId(sid);
            s.setStudentName(names.get(sid));
            s.setSource(1);
            s.setAuditionId(a.getId());
            s.setAuditionName(a.getName());
            s.setCourseName(a.getCourseName());
            s.setTeacherName(a.getTeacherName());
            s.setClassroomName(a.getClassroomName());
            s.setTimePeriod(a.getTimePeriod());
            s.setCampus(a.getCampus());
            s.setAuditionDate(a.getAuditionDate());
            s.setAssistantName(a.getAssistantName());
            s.setOperator(AuthContext.get().getRealName());
            auditionStudentMapper.insert(s);
            ok++;
        }
        return Result.ok("已为 " + ok + " 名学员登记试听"
                + (ok < dto.getStudentIds().size() ? "，" + (dto.getStudentIds().size() - ok) + " 名已登记跳过" : ""));
    }

    @lombok.Data
    public static class StudentsBatchDTO {
        private java.util.List<Long> studentIds;
    }
}
