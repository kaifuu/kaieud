package com.edu.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDateTime;

/** 旷课申请：审批通过免扣课；未批准超72小时系统自动扣课 */
@Data
@TableName("edu_absence")
public class EduAbsence {
    @TableId(type = IdType.AUTO)
    private Long id;
    private Long tenantId;
    private Long orgId;
    private Long scheduleId;
    /** 学员（一对一），班课为空=整班 */
    private Long studentId;
    private Long teacherId;
    private Long courseId;
    private Long clazzId;
    private LocalDateTime classTime;
    private String reason;
    /** 0待审批 1已批准(免扣课) 2已驳回(已扣课) 3超72小时自动扣课 */
    private Integer status;
    private LocalDateTime applyTime;
    /** 审批截止（申请+72小时，超时自动扣课） */
    private LocalDateTime deadline;
    private String approver;
    private LocalDateTime approveTime;
    private String approveRemark;
    private LocalDateTime deductTime;
    private LocalDateTime createTime;
}
