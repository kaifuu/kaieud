-- ============================================================
-- V2.6 排课操作日志：排课历史精确记录每笔操作与操作人
-- 排课 / 调课 / 取消排课 / 补课 每笔操作各留一条记录，
-- 操作人记录当时登录账号的用户名（与消课台账 edu_consume.operator 口径一致）
-- ============================================================

CREATE TABLE IF NOT EXISTS edu_schedule_log (
  id          BIGINT       AUTO_INCREMENT PRIMARY KEY COMMENT '主键',
  tenant_id   BIGINT       DEFAULT 0     COMMENT '租户ID',
  schedule_id BIGINT       NOT NULL      COMMENT '课次ID',
  op_type     VARCHAR(20)  NOT NULL      COMMENT '操作类型：排课/调课/取消排课/补课',
  operator    VARCHAR(50)  DEFAULT NULL  COMMENT '操作人（登录账号用户名）',
  op_time     DATETIME     DEFAULT NULL  COMMENT '操作时间',
  remark      VARCHAR(255) DEFAULT NULL  COMMENT '备注（调课/取消原因）',
  create_time DATETIME     DEFAULT CURRENT_TIMESTAMP COMMENT '入库时间',
  KEY idx_schedule (schedule_id),
  KEY idx_op_time (op_time)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='排课操作日志';

-- 存量课次按当前状态回补一条推导记录（历史数据无操作人信息，记为 -）
INSERT INTO edu_schedule_log (tenant_id, schedule_id, op_type, operator, op_time, remark, create_time)
SELECT tenant_id, id,
       CASE WHEN status = 2 THEN '取消排课'
            WHEN remark LIKE '调整时间%' THEN '调课'
            WHEN compensation = 1 THEN '补课'
            ELSE '排课' END,
       '-',
       CASE WHEN status = 2 OR remark LIKE '调整时间%'
            THEN COALESCE(update_time, create_time) ELSE create_time END,
       NULL, NOW()
FROM edu_schedule;
