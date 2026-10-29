package com.edu.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;

@Data
@TableName("sys_user")
public class SysUser {
    @TableId(type = IdType.AUTO)
    private Long id;
    private Long tenantId;
    private Long orgId;
    private String username;
    private String password;
    private String realName;
    private Integer gender;
    private String phone;
    private String email;
    /** 1超管 2教管人员 3班主任 4教师 5学生 */
    private Integer userType;
    private Integer status;
    private LocalDateTime lastLoginTime;
    private LocalDateTime createTime;

    /** 头像图像（直接存MySQL，常规查询不加载） */
    @TableField(select = false)
    private byte[] avatar;

    // ===== 建档扩展字段（按用户类型写入 edu_student / edu_teacher，非表字段） =====
    @TableField(exist = false)
    private LocalDate birthDate;
    @TableField(exist = false)
    private String grade;
    @TableField(exist = false)
    private String school;
    @TableField(exist = false)
    private String parentName;
    @TableField(exist = false)
    private String parentPhone;
    @TableField(exist = false)
    private String backupPhone;
    @TableField(exist = false)
    private String advisor;
    @TableField(exist = false)
    private String tags;
    @TableField(exist = false)
    private String address;
    @TableField(exist = false)
    private String subject;
    @TableField(exist = false)
    private String title;
    @TableField(exist = false)
    private java.math.BigDecimal rateOne2one;
    @TableField(exist = false)
    private java.math.BigDecimal rateClass;
    /** 可带班型，逗号分隔（1一对一2小班3大班） */
    @TableField(exist = false)
    private String classTypes;
    @TableField(exist = false)
    private String intro;

    /** 角色ID集合(非表字段) */
    @TableField(exist = false)
    private List<Long> roleIds;
}
