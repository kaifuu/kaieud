package com.edu.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDateTime;

/** 学员变更记录（停课/复课/退费需留痕） */
@Data
@TableName("edu_student_log")
public class EduStudentLog {
    @TableId(type = IdType.AUTO)
    private Long id;
    private Long tenantId;
    private Long studentId;
    private String changeType;
    private String remark;
    private String operator;
    private LocalDateTime createTime;
}
