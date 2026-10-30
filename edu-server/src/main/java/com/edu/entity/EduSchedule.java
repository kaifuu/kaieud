package com.edu.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableLogic;
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
    /** 最后操作时间（调课/取消/完成/消课登记），排课历史按此展示操作发生时间 */
    private LocalDateTime updateTime;
    /** 逻辑删除：0正常 1已删除（日历删除课次），查询自动过滤 */
    @TableLogic
    private Integer deleted;

    /** 当前阶段（非库字段，按系统时间推导）：tobe待排课/today待上课/ongoing上课中/done已下课/canceled已取消 */
    @TableField(exist = false)
    private String phase;
    /** 待排课任务剩余未排课时（非库字段） */
    @TableField(exist = false)
    private Integer remainingHours;
    /** 待排课任务周期文本，如班级起止日期（非库字段） */
    @TableField(exist = false)
    private String timeText;
}
