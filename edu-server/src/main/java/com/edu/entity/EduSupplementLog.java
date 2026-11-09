package com.edu.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;

/** 补费办理流水（办理中心-补费）：给欠费报名单/教材杂费单追加收款 */
@Data
@TableName("edu_supplement_log")
public class EduSupplementLog {
    @TableId(type = IdType.AUTO)
    private Long id;
    private Long tenantId;
    private Long studentId;
    /** 补费明细JSON [{kind,refId,rid,typeName,name,arrears,amount}] */
    private String items;
    /** 补费金额合计（Σ本次补费） */
    private BigDecimal amount;
    /** 实收合计（渠道） */
    private BigDecimal paidAmount;
    /** 支付明细JSON [{channel,account,serial,amount}] */
    private String payDetail;
    /** 经办校区 */
    private String campus;
    /** 销售来源 */
    private String source;
    /** 经办日期 */
    private LocalDate handleDate;
    /** 采单员 */
    private String orderTaker;
    /** 咨询顾问 */
    private String consultant;
    private String remark;
    /** 操作人 */
    private String operator;
    private LocalDateTime createTime;
}
