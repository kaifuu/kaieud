package com.edu.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.math.BigDecimal;
import java.time.LocalDateTime;

@Data
@TableName("edu_enroll")
public class EduEnroll {
    @TableId(type = IdType.AUTO)
    private Long id;
    private Long tenantId;
    private Long studentId;
    private Long courseId;
    private Long clazzId;
    private Long teacherId;
    private Integer totalHours;
    private Integer usedHours;
    private BigDecimal price;
    private BigDecimal discount;
    private BigDecimal totalAmount;
    private BigDecimal paidAmount;
    /** 1在读 2已结课 3已退费 */
    private Integer status;
    private LocalDateTime createTime;
}
