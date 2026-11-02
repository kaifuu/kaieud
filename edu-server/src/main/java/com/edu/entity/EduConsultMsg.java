package com.edu.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDateTime;

/** 咨询追问消息：一次咨询下学生可多次追问、老师可多次回复，按时间成对话流 */
@Data
@TableName("edu_consult_msg")
public class EduConsultMsg {
    @TableId(type = IdType.AUTO)
    private Long id;
    private Long consultId;
    /** 发送方：0学生 1老师 */
    private Integer sender;
    private String content;
    private LocalDateTime createTime;
}
