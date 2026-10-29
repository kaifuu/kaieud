package com.edu.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDateTime;

@Data
@TableName("edu_leave")
public class EduLeave {
    @TableId(type = IdType.AUTO)
    private Long id;
    private Long tenantId;
    private Long studentId;
    private Long scheduleId;
    private String reason;
    /** 0待审批 1已同意 2已拒绝 */
    private Integer status;
    private LocalDateTime createTime;
}
