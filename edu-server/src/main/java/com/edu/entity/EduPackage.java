package com.edu.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.math.BigDecimal;
import java.time.LocalDateTime;

/** 课时套餐模板（售卖包，合同创建时引用，支持一客一价） */
@Data
@TableName("edu_package")
public class EduPackage {
    @TableId(type = IdType.AUTO)
    private Long id;
    private Long tenantId;
    private String name;
    /** 科目（字典 course_category） */
    private Integer category;
    /** 授课模式（字典 class_type） */
    private Integer teachMode;
    /** 适用年级 */
    private String grade;
    /** 关联课程（历史字段，新版套餐按科目/授课模式/年级定义，不再必填） */
    private Long courseId;
    /** 1一对一 2小班 3大班 */
    private Integer classType;
    private BigDecimal unitPrice;
    private BigDecimal hours;
    private BigDecimal giftHours;
    private Integer validDays;
    private java.time.LocalDate startDate;
    private java.time.LocalDate endDate;
    private Integer enabled;
    private String createBy;
    private LocalDateTime createTime;
    private Integer deleted;
}
