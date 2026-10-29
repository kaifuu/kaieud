package com.edu.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;

/** 合同（绑定学员+套餐引用，支持一客一价、赠送课时、退费冻结） */
@Data
@TableName("edu_contract")
public class EduContract {
    @TableId(type = IdType.AUTO)
    private Long id;
    private Long tenantId;
    private String contractNo;
    private Long studentId;
    private Long packageId;
    private Long courseId;
    private Integer classType;
    private BigDecimal purchasedHours;
    private BigDecimal giftHours;
    /** 已消购买课时（先扣购买再扣赠送） */
    private BigDecimal usedPurchased;
    private BigDecimal usedGift;
    private BigDecimal unitPrice;
    private BigDecimal totalAmount;
    private BigDecimal paidAmount;
    private LocalDate startDate;
    private LocalDate endDate;
    /** 0待缴费 1部分缴费 2已结清 3已退费 4已到期 */
    private Integer status;
    private String attachmentUrl;
    private BigDecimal refundHours;
    private BigDecimal refundAmount;
    private String advisor;
    private String remark;
    private String createBy;
    private Long orgId;
    private LocalDateTime createTime;
    private Integer deleted;
}
