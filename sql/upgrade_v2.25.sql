-- =====================================================================
-- v2.25 容器管理（平台运营）：打包发布 / SSH部署到远程服务器 / 拉起服务 / 发布日志
-- 平台级表（无 tenant_id，仅平台运营可用）
-- =====================================================================

-- 发布目标服务器（腾讯云等）
CREATE TABLE sys_deploy_target (
  id            BIGINT       NOT NULL AUTO_INCREMENT PRIMARY KEY COMMENT '主键',
  name          VARCHAR(64)  NOT NULL COMMENT '名称（如 腾讯云生产）',
  host          VARCHAR(128) NOT NULL COMMENT '服务器IP/域名',
  ssh_port      INT          NOT NULL DEFAULT 22 COMMENT 'SSH端口',
  username      VARCHAR(32)  NOT NULL DEFAULT 'root' COMMENT 'SSH用户名',
  auth_type     TINYINT      NOT NULL DEFAULT 1 COMMENT '认证:1-密码 2-私钥',
  secret        VARCHAR(512) NOT NULL COMMENT '3DES加密的密码/私钥口令',
  key_path      VARCHAR(255) NULL COMMENT '私钥文件路径（KEY认证）',
  remote_dir    VARCHAR(128) NOT NULL DEFAULT '/opt/kaied' COMMENT '远端部署目录',
  app_port      INT          NOT NULL DEFAULT 8080 COMMENT '应用端口',
  web_port      INT          NOT NULL DEFAULT 80 COMMENT '前端端口',
  db_password   VARCHAR(32)  NOT NULL DEFAULT 'kaied123' COMMENT '随包MySQL root密码',
  enabled       TINYINT      NOT NULL DEFAULT 1 COMMENT '1-启用 0-停用',
  last_test_at  DATETIME     NULL COMMENT '最近连通测试时间',
  last_test_ok  TINYINT      NULL COMMENT '最近测试结果',
  last_test_msg VARCHAR(480) NULL COMMENT '最近测试消息',
  create_time   DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间'
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COMMENT = '发布目标服务器';

-- 发布记录（打包/部署/服务操作 全程日志）
CREATE TABLE sys_deploy_record (
  id          BIGINT       NOT NULL AUTO_INCREMENT PRIMARY KEY COMMENT '主键',
  type        VARCHAR(16)  NOT NULL COMMENT '类型:BUILD-打包 DEPLOY-部署 SERVICE-服务操作',
  version     VARCHAR(32)  NULL COMMENT '版本号',
  target_id   BIGINT       NULL COMMENT '目标服务器ID（部署/服务操作）',
  target_name VARCHAR(64)  NULL COMMENT '目标名称快照',
  status      VARCHAR(16)  NOT NULL COMMENT 'RUNNING/SUCCESS/FAILED',
  file_name   VARCHAR(255) NULL COMMENT '产物文件名（打包）',
  action      VARCHAR(32)  NULL COMMENT '服务操作:status/start/stop/restart/logs',
  log         MEDIUMTEXT   NULL COMMENT '全程日志（前端轮询展示）',
  operator    VARCHAR(64)  NULL COMMENT '操作人',
  create_time DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP,
  finish_time DATETIME     NULL
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COMMENT = '发布记录与日志';
