package com.edu.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.math.BigDecimal;
import java.time.LocalDateTime;

/** 消课台账（消课必须留痕，支持撤销回滚；消课瞬间计提教师成本） */
@Data
@TableName("edu_consume")
public class EduConsume {
    @TableId(type = IdType.AUTO)
    private Long id;
    private Long tenantId;
    private String consumeNo;
    private Long contractId;
    private Long scheduleId;
    private Long studentId;
    private Long courseId;
    private Long teacherId;
    private Integer classType;
    private BigDecimal hours;
    private BigDecimal beforeHours;
    private BigDecimal afterHours;
    /** 0正常上课 1补课 */
    private Integer consumeType;
    /** 扣课来源：消课/签到消课/到课/旷课 */
    private String source;
    /** 扣课说明（扣减节数少于本课次应扣节数时填写原因） */
    private String remark;
    private String operator;
    private Integer canceled;
    private LocalDateTime cancelTime;
    private Long orgId;
    /** 0待审核 1已审核 2已驳回 */
    private Integer auditStatus;
    private String auditor;
    private LocalDateTime auditTime;
    private String auditRemark;
    private LocalDateTime createTime;
    private Integer deleted;
}
