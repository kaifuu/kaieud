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
import com.edu.mapper.EduClazzMapper;
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
import java.util.List;
import java.util.Map;

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
                        .last(SafeSort.order(sortBy, direction, "id", "id", "name", "capacity", "startDate", "status", "createTime")));
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
                .set(EduClazz::getStartDate, clazz.getStartDate())
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

}
