package com.edu.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.math.BigDecimal;
import java.time.LocalDateTime;

/** 合同退费记录（每次退费留痕；退费页"已退课时"按学员汇总本表） */
@Data
@TableName("edu_refund")
public class EduRefund {
    @TableId(type = IdType.AUTO)
    private Long id;
    private Long tenantId;
    private String refundNo;
    private Long contractId;
    private Long studentId;
    private Long courseId;
    private BigDecimal refundHours;
    private BigDecimal refundAmount;
    /** 退费方式：1-退费至个人（现金/原路退回） 2-退费至账户（入学生账户余额） */
    private Integer refundTo;
    /** 退费时合同剩余课时（快照） */
    private BigDecimal remainHours;
    private String reason;
    private String operator;
    private Long orgId;
    private LocalDateTime createTime;
}
