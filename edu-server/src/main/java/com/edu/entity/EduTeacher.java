package com.edu.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.math.BigDecimal;
import java.time.LocalDateTime;

@Data
@TableName("edu_teacher")
public class EduTeacher {
    @TableId(type = IdType.AUTO)
    private Long id;
    private Long tenantId;
    private Long userId;
    private String name;
    private Integer gender;
    private String phone;
    private String email;
    private String subject;
    private String title;
    private String intro;
    private BigDecimal hourlyRate;
    private java.math.BigDecimal rateOne2one;
    private java.math.BigDecimal rateClass;
    private String classTypes;
    private String teachCourseIds;
    private String banTimes;
    private Integer deleted;
    private Integer status;
    private LocalDateTime createTime;
}
