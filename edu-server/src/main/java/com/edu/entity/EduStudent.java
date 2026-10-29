package com.edu.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDate;
import java.time.LocalDateTime;

@Data
@TableName("edu_student")
public class EduStudent {
    @TableId(type = IdType.AUTO)
    private Long id;
    private Long tenantId;
    private Long userId;
    private String name;
    private Integer gender;
    private LocalDate birthDate;
    private String school;
    private String grade;
    private String parentName;
    private String parentPhone;
    private String address;
    private String remark;
    private String backupPhone;
    private String advisor;
    private String tags;
    /** 1在读2停课3结课4退费 */
    private Integer status;
    private Integer deleted;
    private Long orgId;
    /** 学管师用户ID */
    private Long managerId;
    private LocalDateTime createTime;
}
