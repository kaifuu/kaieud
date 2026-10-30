package com.edu.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDateTime;

/** 排课操作日志：排课/调课/取消排课/补课 每笔操作留痕（含操作人登录账号） */
@Data
@TableName("edu_schedule_log")
public class EduScheduleLog {
    @TableId(type = IdType.AUTO)
    private Long id;
    private Long tenantId;
    private Long scheduleId;
    /** 排课/调课/取消排课/补课 */
    private String opType;
    /** 操作人（登录账号用户名） */
    private String operator;
    private LocalDateTime opTime;
    private String remark;
    /** 课次删除后的展示快照（删除排课日志及其回填的历史日志） */
    private String courseName;
    private String teacherName;
    private String classroom;
    /** 上课时间快照，如 2026-10-28 10:00:00 ~ 12:00:00 */
    private String classTime;
    private LocalDateTime createTime;
}
