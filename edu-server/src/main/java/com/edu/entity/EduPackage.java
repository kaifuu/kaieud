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
