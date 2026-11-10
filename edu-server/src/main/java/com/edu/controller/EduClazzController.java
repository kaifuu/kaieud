package com.edu.controller;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.edu.common.SafeSort;
import com.edu.common.Log;
import com.edu.common.PageResult;
import com.edu.common.Result;
import com.edu.entity.EduClazz;
import com.edu.entity.EduEnroll;
import com.edu.entity.EduTeacher;
import com.edu.entity.SysUser;
import com.edu.entity.EduStudent;
import com.edu.entity.EduContract;
import com.edu.mapper.EduClazzMapper;
import com.edu.mapper.EduStudentMapper;
import com.edu.mapper.EduContractMapper;
import com.edu.mapper.EduEnrollMapper;
import com.edu.mapper.EduTeacherMapper;
import com.edu.mapper.SysUserMapper;
import com.edu.security.AuthContext;
import com.edu.security.RequiresPerm;
import lombok.RequiredArgsConstructor;
import org.springframework.util.StringUtils;
import org.springframework.web.bind.annotation.*;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

/** 班级管理 + 班主任管理(指定/更换班主任) */
@RestController
@RequestMapping("/edu/clazz")
@RequiredArgsConstructor
@RequiresPerm("edu:clazz")
public class EduClazzController {

    private final EduClazzMapper clazzMapper;
    private final EduTeacherMapper teacherMapper;
    private final EduEnrollMapper enrollMapper;
    private final SysUserMapper userMapper;
    private final EduStudentMapper studentMapper;
    private final EduContractMapper contractMapper;
    private final com.edu.mapper.EduCourseMapper courseMapper;

    /** 员工类型（与用户管理"员工"TAB一致）：2教务 3班主任 6校区校长 7校区咨询师 8校区学管师 */
    private static final List<Integer> STAFF_TYPES = java.util.Arrays.asList(2, 3, 6, 7, 8);

    /**
     * 班主任候选：用户管理中类型为员工的在职人员（含其教师档案ID，可能尚未建档为 null）。
     * 前端下拉展示 userId/姓名，保存时按 userId 解析教师档案。
     */
    @GetMapping("/teacher-candidates")
    public Result<List<Map<String, Object>>> teacherCandidates() {
        List<SysUser> users = userMapper.selectList(new LambdaQueryWrapper<SysUser>()
                .in(SysUser::getUserType, STAFF_TYPES)
                .eq(SysUser::getStatus, 1)
                .orderByAsc(SysUser::getRealName));
        if (users.isEmpty()) return Result.ok(new ArrayList<>());
        Map<Long, EduTeacher> byUserId = new HashMap<>();
        for (EduTeacher t : teacherMapper.selectList(new LambdaQueryWrapper<EduTeacher>()
                .in(EduTeacher::getUserId, users.stream().map(SysUser::getId)
                        .collect(java.util.stream.Collectors.toList())))) {
            if (t.getUserId() != null) byUserId.put(t.getUserId(), t);
        }
        List<Map<String, Object>> result = new ArrayList<>();
        for (SysUser u : users) {
            Map<String, Object> m = new HashMap<>();
            m.put("userId", u.getId());
            m.put("name", StringUtils.hasText(u.getRealName()) ? u.getRealName() : u.getUsername());
            m.put("userType", u.getUserType());
            EduTeacher t = byUserId.get(u.getId());
            m.put("teacherId", t == null ? null : t.getId());
            result.add(m);
        }
        return Result.ok(result);
    }

    /** 按员工用户解析教师档案（班主任ID存教师档案ID）：无档案则补建（登录即挂教师中心，与"兼任教师"口径一致） */
    private Long resolveTeacherId(Long userId) {
        EduTeacher t = teacherMapper.selectOne(new LambdaQueryWrapper<EduTeacher>()
                .eq(EduTeacher::getUserId, userId).last("LIMIT 1"));
        if (t != null) return t.getId();
        SysUser u = userMapper.selectById(userId);
        if (u == null) throw new com.edu.common.exception.BizException("所选员工不存在");
        EduTeacher neu = new EduTeacher();
        neu.setTenantId(u.getTenantId());
        neu.setUserId(u.getId());
        neu.setName(StringUtils.hasText(u.getRealName()) ? u.getRealName() : u.getUsername());
        neu.setPhone(u.getPhone());
        neu.setStatus(1);
        neu.setDeleted(0);
        teacherMapper.insert(neu);
        return neu.getId();
    }

    @GetMapping("/page")
    public Result<PageResult<EduClazz>> page(@RequestParam(defaultValue = "1") long page,
                                             @RequestParam(defaultValue = "10") long size,
                                             @RequestParam(required = false) String keyword,
                                             @RequestParam(required = false) Long courseId,
                                             @RequestParam(required = false) Integer status,
 @RequestParam(required = false) String sortBy,
 @RequestParam(required = false) String direction) {
        Page<EduClazz> p = clazzMapper.selectPage(new Page<>(page, size),
                new LambdaQueryWrapper<EduClazz>()
                        .like(StringUtils.hasText(keyword), EduClazz::getName, keyword)
                        .eq(courseId != null, EduClazz::getCourseId, courseId)
                        .eq(status != null, EduClazz::getStatus, status)
                        .last(SafeSort.order(sortBy, direction, "id", "id", "name", "capacity", "startDate", "endDate", "status", "createTime")));
        return Result.ok(PageResult.of(p.getTotal(), p.getRecords()));
    }

    @GetMapping("/list")
    public Result<?> list() {
        return Result.ok(clazzMapper.selectList(
                new LambdaQueryWrapper<EduClazz>().ne(EduClazz::getStatus, 3)));
    }

    @PostMapping
    @Log("新增班级")
    public Result<Void> save(@RequestBody EduClazz clazz) {
        if (clazz.getClassTeacherUserId() != null)
            clazz.setClassTeacherId(resolveTeacherId(clazz.getClassTeacherUserId()));
        clazz.setTenantId(AuthContext.get().getTenantId());
        clazzMapper.insert(clazz);
        return Result.ok();
    }

    /**
     * 修改班级：班主任按员工用户解析教师档案（无档案则补建）。
     * 清空班主任时 classTeacherId 为 null，updateById 会忽略 null 字段导致清不掉，
     * 改用 UpdateWrapper 显式 SET（含 null）。
     */
    @PutMapping
    @Log("修改班级")
    public Result<Void> update(@RequestBody EduClazz clazz) {
        if (clazz.getClassTeacherUserId() != null)
            clazz.setClassTeacherId(resolveTeacherId(clazz.getClassTeacherUserId()));
        clazzMapper.update(null, new LambdaUpdateWrapper<EduClazz>()
                .eq(EduClazz::getId, clazz.getId())
                .set(EduClazz::getName, clazz.getName())
                .set(EduClazz::getCourseId, clazz.getCourseId())
                .set(EduClazz::getClassTeacherId, clazz.getClassTeacherId())
                .set(EduClazz::getCapacity, clazz.getCapacity())
                .set(EduClazz::getMinCapacity, clazz.getMinCapacity())
                .set(EduClazz::getStudentHours, clazz.getStudentHours())
                .set(EduClazz::getTeacherHours, clazz.getTeacherHours())
                .set(EduClazz::getClassroomId, clazz.getClassroomId())
                .set(EduClazz::getStartDate, clazz.getStartDate())
                .set(EduClazz::getEndDate, clazz.getEndDate())
                .set(EduClazz::getStatus, clazz.getStatus()));
        return Result.ok();
    }

    @DeleteMapping("/{id}")
    @Log("删除班级")
    public Result<Void> delete(@PathVariable Long id) {
        Long cnt = enrollMapper.selectCount(new LambdaQueryWrapper<EduEnroll>()
                .eq(EduEnroll::getClazzId, id).eq(EduEnroll::getStatus, 1));
        if (cnt > 0) return Result.error("班级仍有在读学生，不允许删除");
        clazzMapper.deleteById(id);
        return Result.ok();
    }


    /* ==================== 班级学生管理 ==================== */

    /**
     * 班级学生候选：按 合同编号/学生姓名/学生手机号 搜索，
     * 候选为有生效合同(status<3)的学生，附其可绑合同列表（含剩余课时）。
     */
    @GetMapping("/student-candidates")
    public Result<List<Map<String, Object>>> studentCandidates(@RequestParam(required = false) String keyword,
                                                               @RequestParam(required = false) Long clazzId) {
        List<EduContract> contracts = contractMapper.selectList(new LambdaQueryWrapper<EduContract>()
                .lt(EduContract::getStatus, 3));
        if (contracts.isEmpty()) return Result.ok(new ArrayList<>());
        Map<Long, EduStudent> stuMap = new HashMap<>();
        for (EduStudent s : studentMapper.selectList(null)) stuMap.put(s.getId(), s);
        // 已在指定班级的学生不重复入列
        Set<Long> inClazz = new HashSet<>();
        if (clazzId != null)
            for (EduEnroll e : enrollMapper.selectList(new LambdaQueryWrapper<EduEnroll>()
                    .eq(EduEnroll::getClazzId, clazzId).eq(EduEnroll::getStatus, 1)))
                inClazz.add(e.getStudentId());
        String k = keyword == null ? "" : keyword.trim().toLowerCase();
        Map<Long, List<EduContract>> byStudent = new LinkedHashMap<>();
        for (EduContract c : contracts) byStudent.computeIfAbsent(c.getStudentId(), x -> new ArrayList<>()).add(c);
        List<Map<String, Object>> result = new ArrayList<>();
        for (Map.Entry<Long, List<EduContract>> en : byStudent.entrySet()) {
            EduStudent s = stuMap.get(en.getKey());
            if (s == null || inClazz.contains(en.getKey())) continue;
            boolean hit = k.isEmpty();
            if (!hit)
                for (EduContract c : en.getValue())
                    if (c.getContractNo() != null && c.getContractNo().toLowerCase().contains(k)) { hit = true; break; }
            if (!hit && s.getName() != null && s.getName().toLowerCase().contains(k)) hit = true;
            if (!hit && s.getParentPhone() != null && s.getParentPhone().contains(k)) hit = true;
            if (!hit && s.getBackupPhone() != null && s.getBackupPhone().contains(k)) hit = true;
            if (!hit) continue;
            Map<String, Object> m = new LinkedHashMap<>();
            m.put("studentId", en.getKey());
            m.put("name", s.getName());
            m.put("phone", s.getParentPhone() == null ? s.getBackupPhone() : s.getParentPhone());
            List<Map<String, Object>> contractList = new ArrayList<>();
            for (EduContract c : en.getValue()) {
                Map<String, Object> cm = new LinkedHashMap<>();
                cm.put("id", c.getId());
                cm.put("contractNo", c.getContractNo());
                cm.put("courseId", c.getCourseId());
                cm.put("remainHours", remainHours(c));
                cm.put("status", c.getStatus() == null ? 0 : c.getStatus());
                cm.put("endDate", c.getEndDate() == null ? null : c.getEndDate().toString());
                contractList.add(cm);
            }
            m.put("contracts", contractList);
            result.add(m);
        }
        return Result.ok(result);
    }

    /** 加入班级（绑定学生+合同）：在读人数不能超过容量 */
    @PostMapping("/{id}/students")
    @Log("班级添加学生")
    public Result<Void> addStudent(@PathVariable Long id, @RequestBody AddStudentDTO dto) {
        if (dto.getStudentId() == null || dto.getContractId() == null)
            return Result.error("学生与关联合同为必填项");
        EduClazz clazz = clazzMapper.selectById(id);
        if (clazz == null) return Result.error("班级不存在");
        Long studying = enrollMapper.selectCount(new LambdaQueryWrapper<EduEnroll>()
                .eq(EduEnroll::getClazzId, id).eq(EduEnroll::getStatus, 1));
        int cap = clazz.getCapacity() == null ? 0 : clazz.getCapacity();
        if (studying + 1 > cap)
            return Result.error("班级容量不足：容量 " + cap + " 名，已占用 " + studying + " 名，空余 " + Math.max(cap - studying, 0) + " 名");
        EduContract c = contractMapper.selectById(dto.getContractId());
        if (c == null || !dto.getStudentId().equals(c.getStudentId()))
            return Result.error("所选合同不属于该学生");
        if (remainHours(c).compareTo(java.math.BigDecimal.ZERO) <= 0)
            return Result.error("该合同课时已耗尽，无法加入班级");
        Long dup = enrollMapper.selectCount(new LambdaQueryWrapper<EduEnroll>()
                .eq(EduEnroll::getClazzId, id).eq(EduEnroll::getStudentId, dto.getStudentId())
                .eq(EduEnroll::getStatus, 1));
        if (dup > 0) return Result.error("该学生已在本班级");
        EduEnroll e = new EduEnroll();
        e.setTenantId(clazz.getTenantId());
        e.setStudentId(dto.getStudentId());
        e.setCourseId(c.getCourseId());
        e.setClazzId(id);
        e.setTeacherId(clazz.getClassTeacherId());
        e.setTotalHours(remainHours(c).intValue());
        e.setUsedHours(0);
        e.setPrice(c.getUnitPrice());
        e.setStatus(1);
        enrollMapper.insert(e);
        return Result.ok();
    }

    /** 调班：在读报名迁到目标班级（容量+课程一致性校验），原班名额自动释放 */
    @PostMapping("/{fromId}/transfer")
    @Log("学生调班")
    public Result<Void> transfer(@PathVariable Long fromId, @RequestBody TransferDTO dto) {
        if (dto.getToClazzId() == null || dto.getStudentId() == null)
            return Result.error("目标班级与学生为必填项");
        if (fromId.equals(dto.getToClazzId())) return Result.error("目标班级不能与当前班级相同");
        EduClazz to = clazzMapper.selectById(dto.getToClazzId());
        if (to == null) return Result.error("目标班级不存在");
        Long studying = enrollMapper.selectCount(new LambdaQueryWrapper<EduEnroll>()
                .eq(EduEnroll::getClazzId, dto.getToClazzId()).eq(EduEnroll::getStatus, 1));
        int cap = to.getCapacity() == null ? 0 : to.getCapacity();
        if (studying + 1 > cap)
            return Result.error("目标班级容量不足：容量 " + cap + " 名，已占用 " + studying + " 名");
        EduEnroll e = enrollMapper.selectOne(new LambdaQueryWrapper<EduEnroll>()
                .eq(EduEnroll::getClazzId, fromId).eq(EduEnroll::getStudentId, dto.getStudentId())
                .eq(EduEnroll::getStatus, 1).last("LIMIT 1"));
        if (e == null) return Result.error("该学生不在当前班级");
        if (e.getCourseId() != null && to.getCourseId() != null && !e.getCourseId().equals(to.getCourseId()))
            return Result.error("目标班级课程与报名课程不一致，不允许调班");
        e.setClazzId(dto.getToClazzId());
        if (to.getClassTeacherId() != null) e.setTeacherId(to.getClassTeacherId());
        enrollMapper.updateById(e);
        return Result.ok();
    }

    /** 班级学生列表（详情页）：容量/占用/空余 + 学生合同课时（总/已上/未上） */
    @GetMapping("/{id}/students")
    public Result<Map<String, Object>> students(@PathVariable Long id) {
        EduClazz clazz = clazzMapper.selectById(id);
        if (clazz == null) return Result.error("班级不存在");
        List<EduEnroll> enrolls = enrollMapper.selectList(new LambdaQueryWrapper<EduEnroll>()
                .eq(EduEnroll::getClazzId, id).eq(EduEnroll::getStatus, 1));
        Map<Long, EduStudent> stuMap = new HashMap<>();
        if (!enrolls.isEmpty()) {
            Set<Long> sids = new HashSet<>();
            for (EduEnroll e : enrolls) sids.add(e.getStudentId());
            for (EduStudent s : studentMapper.selectBatchIds(sids)) stuMap.put(s.getId(), s);
        }
        List<Map<String, Object>> rows = new ArrayList<>();
        for (EduEnroll e : enrolls) {
            EduStudent s = stuMap.get(e.getStudentId());
            int total = e.getTotalHours() == null ? 0 : e.getTotalHours();
            int used = e.getUsedHours() == null ? 0 : e.getUsedHours();
            Map<String, Object> m = new LinkedHashMap<>();
            m.put("enrollId", e.getId());
            m.put("studentId", e.getStudentId());
            m.put("name", s == null ? "-" : s.getName());
            m.put("phone", s == null ? "-" : (s.getParentPhone() == null ? s.getBackupPhone() : s.getParentPhone()));
            m.put("totalHours", total);
            m.put("usedHours", used);
            m.put("remainHours", Math.max(total - used, 0));
            m.put("enrollTime", e.getCreateTime());
            rows.add(m);
        }
        Map<String, Object> data = new HashMap<>();
        data.put("capacity", clazz.getCapacity() == null ? 0 : clazz.getCapacity());
        data.put("studying", enrolls.size());
        data.put("rows", rows);
        return Result.ok(data);
    }

    /** 合同剩余课时 */
    private java.math.BigDecimal remainHours(EduContract c) {
        return (c.getPurchasedHours() == null ? java.math.BigDecimal.ZERO : c.getPurchasedHours())
                .add(c.getGiftHours() == null ? java.math.BigDecimal.ZERO : c.getGiftHours())
                .subtract(c.getUsedPurchased() == null ? java.math.BigDecimal.ZERO : c.getUsedPurchased())
                .subtract(c.getUsedGift() == null ? java.math.BigDecimal.ZERO : c.getUsedGift());
    }

    @lombok.Data
    public static class AddStudentDTO {
        private Long studentId;
        private Long contractId;
    }

    /**
     * 班级功能页-添加学员候选：本班课程的在读报名单（仅班课，授课模式=9），
     * 按学员聚合取最新一单（排除已在本班的），附剩余课时/报名时间/分班状态。
     * 筛选：关键字(姓名/手机号)/校区/分班状态/报名时间范围。
     */
    @GetMapping("/{id}/student-add-candidates")
    public Result<List<Map<String, Object>>> addCandidates(@PathVariable Long id,
            @RequestParam(required = false) String keyword,
            @RequestParam(required = false) String campus,
            @RequestParam(required = false) Boolean assigned,
            @RequestParam(required = false) String begin,
            @RequestParam(required = false) String end) {
        EduClazz clazz = clazzMapper.selectById(id);
        if (clazz == null) return Result.error("班级不存在");
        // 仅班课课程可按此方式加学员（一对一不走班级）
        com.edu.entity.EduCourse course = clazz.getCourseId() == null ? null : courseMapper.selectById(clazz.getCourseId());
        if (course == null || course.getTeachMode() == null || course.getTeachMode() != 9)
            return Result.ok(new ArrayList<>());
        List<EduEnroll> enrolls = enrollMapper.selectList(new LambdaQueryWrapper<EduEnroll>()
                .eq(EduEnroll::getCourseId, clazz.getCourseId())
                .eq(EduEnroll::getStatus, 1)
                .orderByDesc(EduEnroll::getId));
        // 按学员取最新一单；已在本班的排除
        Map<Long, EduEnroll> latest = new LinkedHashMap<>();
        for (EduEnroll e : enrolls) {
            if (id.equals(e.getClazzId())) continue;
            latest.putIfAbsent(e.getStudentId(), e);
        }
        if (latest.isEmpty()) return Result.ok(new ArrayList<>());
        Map<Long, EduStudent> stuMap = new HashMap<>();
        for (EduStudent s : studentMapper.selectBatchIds(latest.keySet())) stuMap.put(s.getId(), s);
        String k = keyword == null ? "" : keyword.trim().toLowerCase();
        List<Map<String, Object>> result = new ArrayList<>();
        for (Map.Entry<Long, EduEnroll> en : latest.entrySet()) {
            EduEnroll e = en.getValue();
            EduStudent s = stuMap.get(en.getKey());
            if (s == null) continue;
            // 报名日期：经办日期优先，退回创建日期
            String d = e.getHandleDate() != null ? e.getHandleDate().toString()
                    : (e.getCreateTime() == null ? null : e.getCreateTime().toLocalDate().toString());
            // 筛选：校区 / 分班状态 / 报名时间范围 / 关键字
            if (StringUtils.hasText(campus) && !campus.equals(e.getCampus())) continue;
            boolean isAssigned = e.getClazzId() != null;
            if (assigned != null && assigned != isAssigned) continue;
            if (StringUtils.hasText(begin) && (d == null || d.compareTo(begin) < 0)) continue;
            if (StringUtils.hasText(end) && (d == null || d.compareTo(end) > 0)) continue;
            if (!k.isEmpty()) {
                boolean hit = (s.getName() != null && s.getName().toLowerCase().contains(k))
                        || (s.getParentPhone() != null && s.getParentPhone().contains(k))
                        || (s.getBackupPhone() != null && s.getBackupPhone().contains(k));
                if (!hit) continue;
            }
            int total = e.getTotalHours() == null ? 0 : e.getTotalHours();
            int used = e.getUsedHours() == null ? 0 : e.getUsedHours();
            Map<String, Object> m = new LinkedHashMap<>();
            m.put("studentId", en.getKey());
            m.put("name", s.getName());
            m.put("phone", s.getParentPhone() == null ? s.getBackupPhone() : s.getParentPhone());
            m.put("enrollId", e.getId());
            m.put("remainHours", Math.max(total - used, 0));
            m.put("campus", e.getCampus());
            m.put("clazzId", e.getClazzId());
            m.put("assigned", isAssigned);
            m.put("enrollTime", d);
            result.add(m);
        }
        return Result.ok(result);
    }

    /**
     * 批量加入班级：把所选学员在本班课程的最新在读报名单分入本班（容量校验）；
     * 已在其他班的报名单调整到本班（换班），无可报课程报名单的学员跳过。
     */
    @PostMapping("/{id}/add-students")
    @Log("班级批量添加学员")
    public Result<String> addStudents(@PathVariable Long id, @RequestBody AddStudentsDTO dto) {
        if (dto.getStudentIds() == null || dto.getStudentIds().isEmpty())
            return Result.error("请选择要加入的学员");
        EduClazz clazz = clazzMapper.selectById(id);
        if (clazz == null) return Result.error("班级不存在");
        Long studying = enrollMapper.selectCount(new LambdaQueryWrapper<EduEnroll>()
                .eq(EduEnroll::getClazzId, id).eq(EduEnroll::getStatus, 1));
        int cap = clazz.getCapacity() == null ? 0 : clazz.getCapacity();
        if (studying + dto.getStudentIds().size() > cap)
            return Result.error("班级容量不足：满班 " + cap + " 名，已占用 " + studying
                    + " 名，本次需加入 " + dto.getStudentIds().size() + " 名");
        int ok = 0;
        for (Long sid : dto.getStudentIds()) {
            // 与候选口径一致：本课程在读、不在本班的最新一单（clazz_id 为空=未分班，需包含）
            EduEnroll e = enrollMapper.selectOne(new LambdaQueryWrapper<EduEnroll>()
                    .eq(EduEnroll::getStudentId, sid)
                    .eq(EduEnroll::getCourseId, clazz.getCourseId())
                    .eq(EduEnroll::getStatus, 1)
                    .and(w -> w.isNull(EduEnroll::getClazzId).or().ne(EduEnroll::getClazzId, id))
                    .orderByDesc(EduEnroll::getId).last("LIMIT 1"));
            if (e == null) continue;
            e.setClazzId(id);
            if (clazz.getClassTeacherId() != null) e.setTeacherId(clazz.getClassTeacherId());
            enrollMapper.updateById(e);
            ok++;
        }
        return Result.ok("已加入 " + ok + " 名学员"
                + (ok < dto.getStudentIds().size() ? "，" + (dto.getStudentIds().size() - ok) + " 名无可加报名单已跳过" : ""));
    }

    @lombok.Data
    public static class AddStudentsDTO {
        private List<Long> studentIds;
    }

    @lombok.Data
    public static class TransferDTO {
        private Long studentId;
        private Long toClazzId;
    }
}
