package com.edu.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDateTime;

/** 班级试听学员：学员 + 试听活动/日程试听快照（班级功能页-试听学员TAB） */
@Data
@TableName("edu_audition_student")
public class EduAuditionStudent {
    @TableId(type = IdType.AUTO)
    private Long id;
    private Long tenantId;
    /** 所属班级 */
    private Long clazzId;
    private Long studentId;
    private String studentName;
    /** 来源：1试听活动 2日程试听 */
    private Integer source;
    /** 关联试听活动ID（日程试听为空） */
    private Long auditionId;
    /** 试听名称快照（活动名或班级名） */
    private String auditionName;
    private String courseName;
    private String teacherName;
    private String classroomName;
    private String timePeriod;
    private String campus;
    /** 办试听时间 */
    private java.time.LocalDate auditionDate;
    /** 助教 */
    private String assistantName;
    private String operator;
    private LocalDateTime createTime;
}
