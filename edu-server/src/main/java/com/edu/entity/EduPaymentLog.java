package com.edu.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.math.BigDecimal;
import java.time.LocalDateTime;

/** 缴费操作记录（新增/修改/删除全量留痕，只增不改） */
@Data
@TableName("edu_payment_log")
public class EduPaymentLog {
    @TableId(type = IdType.AUTO)
    private Long id;
    private Long tenantId;
    private Long paymentId;
    private String paymentNo;
    /** 1新增 2修改 3删除 */
    private Integer action;
    private Long studentId;
    /** 操作后实缴金额 */
    private BigDecimal amount;
    /** 修改前金额（新增时NULL） */
    private BigDecimal amountBefore;
    /** 变更明细（改了哪些字段、前后值） */
    private String detail;
    private String operator;
    private Long orgId;
    private LocalDateTime createTime;
}
