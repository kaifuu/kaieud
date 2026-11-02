-- v2.20 企业数据物理清除审计：谁、什么时间、清除了哪个企业（物理删除不可恢复，必须留痕）
CREATE TABLE sys_purge_log (
  id          BIGINT       NOT NULL AUTO_INCREMENT PRIMARY KEY COMMENT '主键',
  tenant_id   BIGINT       NOT NULL COMMENT '被清除的企业ID',
  tenant_name VARCHAR(64)  NOT NULL COMMENT '被清除的企业名称',
  operator    VARCHAR(64)  NOT NULL COMMENT '操作人（平台账号 username/realName）',
  operation   VARCHAR(64)  NOT NULL COMMENT '操作类型（物理删除企业数据）',
  detail      VARCHAR(512) NULL COMMENT '清除明细（各表删除行数）',
  create_time DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '操作时间',
  KEY idx_purge_tenant (tenant_id)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COMMENT = '企业数据物理清除审计表';
