package com.edu.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDateTime;

/** 作业提交（学员×作业）：提交内容 + 教师点评 + 家长已读 */
@Data
@TableName("edu_homework_submit")
public class EduHomeworkSubmit {
    @TableId(type = IdType.AUTO)
    private Long id;
    private Long tenantId;
    private Long homeworkId;
    private Long studentId;
    private String content;
    private LocalDateTime submitTime;
    private String reviewContent;
    private LocalDateTime reviewTime;
    /** 家长已读：0未读 1已读 */
    private Integer readFlag;
}
