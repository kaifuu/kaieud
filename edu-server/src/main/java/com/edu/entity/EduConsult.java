package com.edu.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDateTime;

@Data
@TableName("edu_consult")
public class EduConsult {
    @TableId(type = IdType.AUTO)
    private Long id;
    private Long tenantId;
    private Long studentId;
    private Long teacherId;
    private String content;
    private String reply;
    private LocalDateTime replyTime;
    private Integer status;
    private LocalDateTime createTime;
}
