package com.edu.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.math.BigDecimal;
import java.time.LocalDateTime;

/** 课程定价标准（收费模式：按课时 / 按时间段，按校区分行） */
@Data
@TableName("edu_course_price")
public class EduCoursePrice {
    @TableId(type = IdType.AUTO)
    private Long id;
    private Long tenantId;
    private Long courseId;
    /** 1按课时收费 2按时间收费 */
    private Integer chargeMode;
    /** 校区（全部校区模式下为：全部校区） */
    private String campus;
    /** 数量-课时（模式1，支持两位小数） */
    private BigDecimal hours;
    /** 时间段：天/周/月/季/年（模式2，数量固定1） */
    private String timePeriod;
    private BigDecimal totalAmount;
    private Integer sort;
    private LocalDateTime createTime;
}
