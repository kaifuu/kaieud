package com.edu.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDate;
import java.time.LocalDateTime;

/** 试听管理：插班试听（关联已成班课）/ 开班试听（全新录入） */
@Data
@TableName("edu_audition")
public class EduAudition {
    @TableId(type = IdType.AUTO)
    private Long id;
    private Long tenantId;
    /** 1插班试听 2开班试听 */
    private Integer type;
    private String name;
    private LocalDate auditionDate;
    private String campus;
    /** 课程（插班=关联班级课程名，开班=自由录入） */
    private String courseName;
    private String teacherName;
    private String classroomName;
    /** 时段（如 周六 09:00-10:30） */
    private String timePeriod;
    private String assistantName;
    /** 预招人数 */
    private Integer plannedCount;
    /** 插班试听关联班级ID */
    private Long relateClazzId;
    private String remark;
    private String operator;
    private LocalDateTime createTime;
}
