package com.edu.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDateTime;

@Data
@TableName("edu_evaluation")
public class EduEvaluation {
    @TableId(type = IdType.AUTO)
    private Long id;
    private Long tenantId;
    private Long studentId;
    private Long teacherId;
    private Long courseId;
    private Long scheduleId;
    private Integer score;
    private String content;
    private LocalDateTime createTime;
}
