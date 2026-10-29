package com.edu.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDateTime;

@Data
@TableName("sys_notify_config")
public class SysNotifyConfig {
    @TableId(type = IdType.AUTO)
    private Long id;
    private Long tenantId;
    private String name;
    /** 事件类型: CLASS_START/COURSE_CANCEL/PAY_DUE/LEAVE_AUDIT */
    private String eventType;
    /** 0站内 1短信 2邮件 */
    private Integer channel;
    private String receivers;
    private Integer enabled;
    private String remark;
    private LocalDateTime createTime;
}
