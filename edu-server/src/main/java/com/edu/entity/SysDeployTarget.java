package com.edu.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDateTime;

/** 发布目标服务器（腾讯云等）：SSH/SFTP 发布与容器拉起 */
@Data
@TableName("sys_deploy_target")
public class SysDeployTarget {
    @TableId(type = IdType.AUTO)
    private Long id;
    private String name;
    private String host;
    private Integer sshPort;
    private String username;
    /** 认证：1-密码 2-私钥 */
    private Integer authType;
    /** 3DES 加密的密码/私钥口令（密钥同 edu.jwt.purge.crypto-key） */
    private String secret;
    private String keyPath;
    private String remoteDir;
    private Integer appPort;
    private Integer webPort;
    /** 随包 MySQL root 密码 */
    private String dbPassword;
    /** 数据库：1-随包MySQL 2-外部数据库 */
    private Integer dbMode;
    /** 外部数据库类型（预留，当前 MYSQL） */
    private String dbType;
    private String dbHost;
    private Integer dbPort;
    private String dbName;
    private String dbUsername;
    /** 3DES 加密的外部数据库密码 */
    private String dbSecret;
    private LocalDateTime lastDbTestAt;
    private Integer lastDbTestOk;
    private String lastDbTestMsg;
    private Integer enabled;
    private LocalDateTime lastTestAt;
    private Integer lastTestOk;
    private String lastTestMsg;
    private LocalDateTime createTime;
}
