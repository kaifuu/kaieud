package com.edu.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.math.BigDecimal;
import java.time.LocalDateTime;

@Data
@TableName("edu_payment")
public class EduPayment {
    @TableId(type = IdType.AUTO)
    private Long id;
    private Long tenantId;
    private String paymentNo;
    private Long studentId;
    private Long courseId;
    private Long enrollId;
    private Long contractId;
    private String voucherUrl;
    /** 1红字冲销 */
    private Integer reversed;
    private BigDecimal amount;
    private BigDecimal discountAmount;
    /** 1现金 2微信 3支付宝 4银行转账 */
    private Integer payType;
    private LocalDateTime payTime;
    private String operator;
    private String remark;
    private Long orgId;
    private LocalDateTime createTime;
}
