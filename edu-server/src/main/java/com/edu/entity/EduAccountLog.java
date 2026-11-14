package com.edu.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;

/** 学生账户流水：充值（教务补差价等）与退费入账；余额 = 累计充值 + 累计退费入账 */
@Data
@TableName("edu_account_log")
public class EduAccountLog {
    @TableId(type = IdType.AUTO)
    private Long id;
    private Long tenantId;
    private Long studentId;
    /** 关联合同（充值必须关联合同；退费入账记录来源合同） */
    private Long contractId;
    /** 1-充值 2-退费入账 */
    private Integer type;
    private BigDecimal amount;
    /** 退费课时（退费入账时记录） */
    private BigDecimal hours;
    /** 经办校区（余额充值留档） */
    private String campus;
    /** 支付明细JSON [{channel,account,serial,amount}]（余额充值多账户支付留档） */
    private String payDetail;
    /** 销售来源（余额充值留档） */
    private String source;
    /** 经办日期（余额充值留档） */
    private LocalDate handleDate;
    /** 采单员（余额充值留档） */
    private String orderTaker;
    /** 咨询顾问（余额充值留档） */
    private String consultant;
    /** 原因备注（如：1对4转1对1补差价） */
    private String remark;
    private String operator;
    private LocalDateTime createTime;
    /** 编辑时间（DB ON UPDATE 自动刷新，只读展示用） */
    private LocalDateTime updateTime;
}
