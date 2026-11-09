package com.edu.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;

/** 学员积分流水（办理中心-积分）：增加/兑换留痕，余额=Σ增加−Σ兑换 */
@Data
@TableName("edu_score_log")
public class EduScoreLog {
    @TableId(type = IdType.AUTO)
    private Long id;
    private Long tenantId;
    private Long studentId;
    /** 1增加积分 2积分兑换（扣减） */
    private Integer type;
    /** 本次积分（正数，type2为扣减） */
    private BigDecimal points;
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
