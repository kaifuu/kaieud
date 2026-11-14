package com.edu.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDateTime;

/** 课堂点评（课次×学员）：老师课后给到课学员的小红花与文字点评；家长端已读标记 */
@Data
@TableName("edu_lesson_comment")
public class EduLessonComment {
    @TableId(type = IdType.AUTO)
    private Long id;
    private Long tenantId;
    private Long clazzId;
    private Long scheduleId;
    private Long studentId;
    private Long teacherId;
    /** 小红花数 */
    private Integer flowers;
    private String content;
    /** 家长已读：0未读 1已读 */
    private Integer readFlag;
    private LocalDateTime createTime;
    private LocalDateTime updateTime;
}
