package com.edu.controller;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.edu.common.SafeSort;
import com.edu.common.Log;
import com.edu.common.PageResult;
import com.edu.common.Result;
import com.edu.dto.PasswordDTO;
import com.edu.entity.SysUser;
import com.edu.entity.SysUserRole;
import com.edu.mapper.SysUserMapper;
import com.edu.mapper.SysUserRoleMapper;
import com.edu.security.AuthContext;
import com.edu.security.RequiresPerm;
import lombok.RequiredArgsConstructor;
import org.springframework.util.DigestUtils;
import org.springframework.util.StringUtils;
import org.springframework.web.bind.annotation.*;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

/** 用户管理 */
@RestController
@RequestMapping("/sys/user")
@RequiredArgsConstructor
@RequiresPerm("sys:user")
public class SysUserController {

    private final SysUserMapper userMapper;
    private final SysUserRoleMapper userRoleMapper;
    private final com.edu.mapper.EduStudentMapper studentMapper;
    private final com.edu.mapper.EduTeacherMapper teacherMapper;
    private final com.edu.service.DataScopeService dataScopeService;

    @GetMapping("/page")
    public Result<PageResult<Map<String, Object>>> page(@RequestParam(defaultValue = "1") long page,
                                            @RequestParam(defaultValue = "10") long size,
                                            @RequestParam(required = false) String keyword,
                                            @RequestParam(required = false) Integer userType,
                                            @RequestParam(required = false) String userTypes,
                                            @RequestParam(required = false) Integer status,
 @RequestParam(required = false) String sortBy,
 @RequestParam(required = false) String direction) {
        LambdaQueryWrapper<SysUser> qw = new LambdaQueryWrapper<SysUser>()
                .and(StringUtils.hasText(keyword), w -> w
                        .like(SysUser::getUsername, keyword)
                        .or().like(SysUser::getRealName, keyword)
                        .or().like(SysUser::getPhone, keyword))
                .eq(userType != null, SysUser::getUserType, userType)
                .in(StringUtils.hasText(userTypes), SysUser::getUserType,
                        java.util.Arrays.stream(userTypes.split(","))
                                .map(Integer::valueOf).collect(Collectors.toList()))
                .eq(status != null, SysUser::getStatus, status)
                .last(SafeSort.order(sortBy, direction, "id", "id", "username", "realName", "userType", "createTime", "lastLoginTime"));
        Page<SysUser> p = userMapper.selectPage(new Page<>(page, size), qw);
        // 学生用户合并学员档案字段（年级/学校/家长电话等），供学生TAB展示
        List<Long> stuUserIds = p.getRecords().stream()
                .filter(u -> u.getUserType() != null && u.getUserType() == 5)
                .map(SysUser::getId).collect(Collectors.toList());
        Map<Long, com.edu.entity.EduStudent> stuMap = stuUserIds.isEmpty()
                ? new HashMap<>()
                : studentMapper.selectList(new LambdaQueryWrapper<com.edu.entity.EduStudent>()
                        .in(com.edu.entity.EduStudent::getUserId, stuUserIds))
                .stream().collect(Collectors.toMap(com.edu.entity.EduStudent::getUserId, s -> s, (a, b) -> a));
        List<Map<String, Object>> rows = new ArrayList<>();
        for (SysUser u : p.getRecords()) {
            u.setPassword(null);
            Map<String, Object> m = new HashMap<>();
            m.put("id", u.getId());
            m.put("username", u.getUsername());
            m.put("realName", u.getRealName());
            m.put("userType", u.getUserType());
            m.put("orgId", u.getOrgId());
            m.put("phone", u.getPhone());
            m.put("email", u.getEmail());
            m.put("status", u.getStatus());
            m.put("lastLoginTime", u.getLastLoginTime());
            m.put("createTime", u.getCreateTime());
            com.edu.entity.EduStudent st = stuMap.get(u.getId());
            if (st != null) {
                m.put("grade", st.getGrade());
                m.put("school", st.getSchool());
                m.put("parentPhone", st.getParentPhone());
                m.put("advisor", st.getAdvisor());
            }
            rows.add(m);
        }
        return Result.ok(PageResult.of(p.getTotal(), rows));
    }

    @PostMapping
    @Log("新增用户")
    public Result<SysUser> save(@RequestBody SysUser user) {
        Long cnt = userMapper.selectCount(new LambdaQueryWrapper<SysUser>()
                .eq(SysUser::getUsername, user.getUsername()));
        if (cnt > 0) return Result.error("用户名已存在");
        // 必填校验（user_type列非空无默认，缺参会插库失败）
        if (user.getUserType() == null) return Result.error("用户类型不能为空");
        if (!StringUtils.hasText(user.getRealName())) return Result.error("姓名不能为空");
        if (user.getTenantId() == null) user.setTenantId(1L); // 缺省归属默认租户
        user.setId(null);
        user.setPassword(DigestUtils.md5DigestAsHex(("123456" + AuthService_SALT()).getBytes()));
        userMapper.insert(user);
        // 按用户类型联动建档：学生 -> edu_student；教师 -> edu_teacher
        createUserProfile(user);
        if (user.getRoleIds() != null) {
            for (Long roleId : user.getRoleIds()) {
                SysUserRole ur = new SysUserRole();
                ur.setUserId(user.getId()); ur.setRoleId(roleId);
                userRoleMapper.insert(ur);
            }
        }
        return Result.ok(user); // 返回含新ID的实体，供前端保存后补传照片
    }

    @PutMapping
    @Log("修改用户")
    public Result<Void> update(@RequestBody SysUser user) {
        user.setPassword(null); // 不通过此接口改密码
        userMapper.updateById(user);
        syncUserProfile(user);
        if (user.getRoleIds() != null) {
            userRoleMapper.delete(new LambdaQueryWrapper<SysUserRole>().eq(SysUserRole::getUserId, user.getId()));
            for (Long roleId : user.getRoleIds()) {
                SysUserRole ur = new SysUserRole();
                ur.setUserId(user.getId()); ur.setRoleId(roleId);
                userRoleMapper.insert(ur);
            }
        }
        return Result.ok();
    }

    /** 按用户类型联动建档：学生 -> edu_student；教师 -> edu_teacher */
    private void createUserProfile(SysUser user) {
        Integer type = user.getUserType();
        if (type == null) return;
        Long tenantId = user.getTenantId() == null ? 1L : user.getTenantId();
        if (type == 5) {
            com.edu.entity.EduStudent st = new com.edu.entity.EduStudent();
            st.setTenantId(tenantId);
            // 校区/学管师身份继承：校区级角色建学生自动归属其校区并绑定本人
            com.edu.service.DataScopeService.ScopeType st2 = dataScopeService.resolve();
            if (st2 == com.edu.service.DataScopeService.ScopeType.CAMPUS)
                st.setOrgId(com.edu.security.AuthContext.get().getOrgId());
            if (st2 == com.edu.service.DataScopeService.ScopeType.MANAGER)
                st.setManagerId(com.edu.security.AuthContext.get().getId());
            st.setUserId(user.getId());
            st.setName(user.getRealName());
            st.setGender(user.getGender() == null ? 1 : user.getGender());
            st.setBirthDate(user.getBirthDate());
            st.setGrade(user.getGrade());
            st.setSchool(user.getSchool());
            st.setParentName(user.getParentName());
            st.setParentPhone(user.getParentPhone());
            st.setBackupPhone(user.getBackupPhone());
            st.setAdvisor(user.getAdvisor());
            st.setTags(user.getTags());
            st.setAddress(user.getAddress());
            st.setStatus(1);
            st.setDeleted(0);
            studentMapper.insert(st);
        } else if (type == 4) {
            com.edu.entity.EduTeacher t = new com.edu.entity.EduTeacher();
            t.setTenantId(tenantId);
            t.setUserId(user.getId());
            t.setName(user.getRealName());
            t.setGender(user.getGender() == null ? 1 : user.getGender());
            t.setPhone(user.getPhone());
            t.setEmail(user.getEmail());
            t.setSubject(user.getSubject());
            t.setTitle(user.getTitle());
            t.setRateOne2one(user.getRateOne2one());
            t.setRateClass(user.getRateClass());
            t.setClassTypes(user.getClassTypes());
            t.setIntro(user.getIntro());
            t.setStatus(1);
            t.setDeleted(0);
            teacherMapper.insert(t);
        }
    }

    /** 编辑用户时同步联动档案的基础信息 */
    private void syncUserProfile(SysUser user) {
        Integer type = user.getUserType();
        if (type == null) return;
        if (type == 5) {
            com.edu.entity.EduStudent st = studentMapper.selectOne(
                    new LambdaQueryWrapper<com.edu.entity.EduStudent>()
                            .eq(com.edu.entity.EduStudent::getUserId, user.getId()).last("LIMIT 1"));
            if (st != null) {
                if (StringUtils.hasText(user.getRealName())) st.setName(user.getRealName());
                if (user.getGender() != null) st.setGender(user.getGender());
                if (StringUtils.hasText(user.getGrade())) st.setGrade(user.getGrade());
                if (StringUtils.hasText(user.getSchool())) st.setSchool(user.getSchool());
                studentMapper.updateById(st);
            }
        } else if (type == 4) {
            com.edu.entity.EduTeacher t = teacherMapper.selectOne(
                    new LambdaQueryWrapper<com.edu.entity.EduTeacher>()
                            .eq(com.edu.entity.EduTeacher::getUserId, user.getId()).last("LIMIT 1"));
            if (t != null) {
                if (StringUtils.hasText(user.getRealName())) t.setName(user.getRealName());
                if (user.getPhone() != null) t.setPhone(user.getPhone());
                teacherMapper.updateById(t);
            }
        }
    }

    @DeleteMapping("/{id}")
    @Log("删除用户")
    public Result<Void> delete(@PathVariable Long id) {
        if (id == 1L) return Result.error("超级管理员不允许删除");
        userRoleMapper.delete(new LambdaQueryWrapper<SysUserRole>().eq(SysUserRole::getUserId, id));
        userMapper.deleteById(id);
        return Result.ok();
    }

    @PostMapping("/{id}/reset-password")
    @Log("重置用户密码")
    public Result<Void> resetPassword(@PathVariable Long id) {
        SysUser user = new SysUser();
        user.setId(id);
        user.setPassword(DigestUtils.md5DigestAsHex(("123456" + AuthService_SALT()).getBytes()));
        userMapper.updateById(user);
        return Result.ok();
    }

    /** 建档信息回显（编辑用户时按类型拉取学生/教师档案字段） */
    @GetMapping("/{id}/profile")
    public Result<Map<String, Object>> profile(@PathVariable Long id) {
        SysUser u = userMapper.selectById(id);
        if (u == null) return Result.error("用户不存在");
        Map<String, Object> m = new HashMap<>();
        m.put("realName", u.getRealName());
        m.put("gender", u.getGender());
        m.put("phone", u.getPhone());
        m.put("email", u.getEmail());
        Integer type = u.getUserType();
        if (type != null && type == 5) {
            com.edu.entity.EduStudent st = studentMapper.selectOne(
                    new LambdaQueryWrapper<com.edu.entity.EduStudent>()
                            .eq(com.edu.entity.EduStudent::getUserId, id).last("LIMIT 1"));
            if (st != null) {
                m.put("birthDate", st.getBirthDate());
                m.put("grade", st.getGrade());
                m.put("school", st.getSchool());
                m.put("parentName", st.getParentName());
                m.put("parentPhone", st.getParentPhone());
                m.put("backupPhone", st.getBackupPhone());
                m.put("advisor", st.getAdvisor());
                m.put("tags", st.getTags());
                m.put("address", st.getAddress());
            }
        } else if (type != null && type == 4) {
            com.edu.entity.EduTeacher t = teacherMapper.selectOne(
                    new LambdaQueryWrapper<com.edu.entity.EduTeacher>()
                            .eq(com.edu.entity.EduTeacher::getUserId, id).last("LIMIT 1"));
            if (t != null) {
                m.put("subject", t.getSubject());
                m.put("title", t.getTitle());
                m.put("rateOne2one", t.getRateOne2one());
                m.put("rateClass", t.getRateClass());
                m.put("classTypes", t.getClassTypes());
                m.put("intro", t.getIntro());
            }
        }
        return Result.ok(m);
    }

    /** 上传头像：图像字节直接存MySQL */
    @PostMapping("/{id}/avatar-upload")
    @Log("上传头像")
    public Result<Void> uploadAvatar(@PathVariable Long id,
                                     @RequestParam("file") org.springframework.web.multipart.MultipartFile file)
            throws java.io.IOException {
        if (file.isEmpty()) return Result.error("文件为空");
        if (file.getSize() > 5 * 1024 * 1024) return Result.error("头像不能超过5M");
        String name = file.getOriginalFilename() == null ? "" : file.getOriginalFilename().toLowerCase();
        if (!name.endsWith(".png") && !name.endsWith(".jpg") && !name.endsWith(".jpeg"))
            return Result.error("仅支持 png/jpg 图片");
        userMapper.updateAvatar(id, file.getBytes());
        return Result.ok();
    }

    @GetMapping("/{id}/roles")
    public Result<List<Long>> roles(@PathVariable Long id) {
        return Result.ok(userRoleMapper.selectList(
                        new LambdaQueryWrapper<SysUserRole>().eq(SysUserRole::getUserId, id))
                .stream().map(SysUserRole::getRoleId).collect(Collectors.toList()));
    }

    /** 占位：避免直接依赖service包循环，密码盐值取自AuthService */
    private static String AuthService_SALT() {
        return "edu";
    }
}
