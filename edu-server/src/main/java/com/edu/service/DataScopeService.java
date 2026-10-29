package com.edu.service;

import com.edu.security.AuthContext;
import com.edu.security.LoginUser;
import org.springframework.stereotype.Service;

/**
 * 数据权限范围：按登录人角色决定可见数据边界。
 * 校区校长/咨询师 -> 本校区(org)；学管师 -> 本人负责学员；总部/超管 -> 全部。
 */
@Service
public class DataScopeService {

    public enum ScopeType { ALL, CAMPUS, MANAGER }

    /** 供链式调用处的静态判断 */
    public static boolean isCampus(LoginUser lu) { return resolveStatic(lu) == ScopeType.CAMPUS; }
    public static boolean isManager(LoginUser lu) { return resolveStatic(lu) == ScopeType.MANAGER; }
    private static ScopeType resolveStatic(LoginUser lu) {
        if (lu == null || lu.getUserType() == null) return ScopeType.ALL;
        switch (lu.getUserType()) {
            case 6: case 7: return ScopeType.CAMPUS;
            case 8: return ScopeType.MANAGER;
            default: return ScopeType.ALL;
        }
    }

    public ScopeType resolve(LoginUser lu) {
        if (lu == null || lu.getUserType() == null) return ScopeType.ALL;
        switch (lu.getUserType()) {
            case 6: case 7: return ScopeType.CAMPUS;   // 校区校长 / 咨询师
            case 8: return ScopeType.MANAGER;          // 学管师
            default: return ScopeType.ALL;             // 总部/超管
        }
    }

    public ScopeType resolve() { return resolve(AuthContext.get()); }

    /** 学员表：直接按 org_id / manager_id 过滤 */
    public void applyStudent(com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper<com.edu.entity.EduStudent> qw) {
        LoginUser lu = AuthContext.get();
        if (lu == null) return;
        ScopeType st = resolve(lu);
        if (st == ScopeType.CAMPUS) qw.eq(com.edu.entity.EduStudent::getOrgId, lu.getOrgId());
        else if (st == ScopeType.MANAGER) qw.eq(com.edu.entity.EduStudent::getManagerId, lu.getId());
    }

    /**
     * 合同/缴费/消课表：按 student_id 关联学员的范围过滤。
     * @param studentIdColumn 该表中学员ID的列名（如 student_id）
     */
    public void applyByStudent(com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper<?> qw,
                               LoginUser lu, String studentIdColumn) {
        if (lu == null) return;
        ScopeType st = resolve(lu);
        if (st == ScopeType.ALL) return;
        String col = studentIdColumn;
        if (st == ScopeType.CAMPUS) {
            qw.exists("SELECT 1 FROM edu_student ds WHERE ds.id = " + col
                    + " AND ds.org_id = " + lu.getOrgId());
        } else {
            qw.exists("SELECT 1 FROM edu_student ds WHERE ds.id = " + col
                    + " AND ds.manager_id = " + lu.getId());
        }
    }

    public void applyByStudent(com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper<?> qw,
                               String studentIdColumn) {
        applyByStudent(qw, AuthContext.get(), studentIdColumn);
    }

    /** 新建学员时继承创建人的校区/学管师身份 */
    public void fillOnStudentCreate(com.edu.entity.EduStudent s, LoginUser lu) {
        ScopeType st = resolve(lu);
        if (st == ScopeType.CAMPUS) s.setOrgId(lu.getOrgId());
        if (st == ScopeType.MANAGER) s.setManagerId(lu.getId());
        if (s.getOrgId() == null) s.setOrgId(1L);
    }
}
