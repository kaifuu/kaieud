package com.edu.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDateTime;

@Data
@TableName("edu_schedule")
public class EduSchedule {
    @TableId(type = IdType.AUTO)
    private Long id;
    private Long tenantId;
    private Long courseId;
    private Long clazzId;
    private Long teacherId;
    private Long studentId;
    private Long classroomId;
        private String classroom;
    private LocalDateTime startTime;
    private LocalDateTime endTime;
    /** 0待上课 1已完成 2已取消 */
    private Long orgId;
    private Integer status;
    private String cancelReason;
    private Long originId;
    private Integer compensation;
    private String remark;
    private LocalDateTime createTime;
}
