package com.edu.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDateTime;

@Data
@TableName("sys_log")
public class SysLog {
    @TableId(type = IdType.AUTO)
    private Long id;
    private Long tenantId;
    private Long userId;
    private String username;
    private String operation;
    private String method;
    private String params;
    private String ip;
    private Integer duration;
    private Integer status;
    /** 0操作日志 1登录日志 */
    private Integer logType;
    private String errorMsg;
    private LocalDateTime createTime;
}
