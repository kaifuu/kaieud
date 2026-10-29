package com.edu.security;

import lombok.Data;
import java.util.Set;

/** 当前登录用户上下文对象 */
@Data
public class LoginUser {
    private Long id;
    private Long tenantId;
    private Long orgId;
    private String username;
    private String realName;
    /** 1超管 2教管人员 3班主任 4教师 5学生 */
    private Integer userType;
    /** 教师档案ID(user_type=4时有值) */
    private Long teacherId;
    /** 学生档案ID(user_type=5时有值) */
    private Long studentId;
    /** 权限标识集合 */
    private Set<String> perms;

    public boolean isSuperAdmin() { return userType != null && userType == 1; }
    public boolean hasPerm(String perm) {
        return isSuperAdmin() || (perm != null && perms != null && perms.contains(perm));
    }
}
