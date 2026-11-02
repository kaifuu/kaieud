-- v2.24 通知已读/未读：按用户记录阅读状态（点击查看内容后 未读 -> 已读）
CREATE TABLE sys_notice_read (
  id        BIGINT   NOT NULL AUTO_INCREMENT PRIMARY KEY COMMENT '主键',
  notice_id BIGINT   NOT NULL COMMENT '通知ID',
  user_id   BIGINT   NOT NULL COMMENT '用户ID',
  read_time DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '阅读时间',
  UNIQUE KEY uk_notice_user (notice_id, user_id)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COMMENT = '通知阅读状态（按用户）';
