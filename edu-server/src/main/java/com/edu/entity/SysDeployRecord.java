package com.edu.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDateTime;

/** 发布记录：打包/部署/服务操作，全程日志落库供前端轮询 */
@Data
@TableName("sys_deploy_record")
public class SysDeployRecord {
    @TableId(type = IdType.AUTO)
    private Long id;
    /** BUILD-打包 DEPLOY-部署 SERVICE-服务操作 */
    private String type;
    private String version;
    private Long targetId;
    private String targetName;
    /** RUNNING/SUCCESS/FAILED */
    private String status;
    private String fileName;
    private String action;
    private String log;
    private String operator;
    private LocalDateTime createTime;
    private LocalDateTime finishTime;
}
