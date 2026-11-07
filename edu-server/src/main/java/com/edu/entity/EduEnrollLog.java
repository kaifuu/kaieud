package com.edu.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.math.BigDecimal;
import java.time.LocalDateTime;

/** 报名/续报操作日志：记录每次新增/修改 */
@Data
@TableName("edu_enroll_log")
public class EduEnrollLog {
    @TableId(type = IdType.AUTO)
    private Long id;
    private Long tenantId;
    private Long enrollId;
    /** CREATE / UPDATE */
    private String action;
    /** 操作内容：课程名，价格×数量=总价 */
    private String content;
    private BigDecimal totalAmount;
    private BigDecimal paidAmount;
    /** 支付信息 */
    private String payInfo;
    /** 余额变动 */
    private BigDecimal balanceChange;
    private String operator;
    private LocalDateTime createTime;
}
