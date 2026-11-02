-- ============================================================
-- V2.9 教室备注字段：教室管理补充备注（位置、设备、使用注意事项等）；
-- 所属校区改为必填项（应用层校验）
-- ============================================================

ALTER TABLE edu_classroom
    ADD COLUMN remark VARCHAR(500) DEFAULT NULL COMMENT '备注（教室位置、设备、使用注意事项等）';

-- ============================================================
-- V2.9 咨询多轮追问：新增咨询消息表（edu_consult_msg）
-- 一次咨询下学生可多次追问、老师可多次回复，按时间成对话流；
-- 历史咨询无消息记录，读取时按主记录 content/reply 合成展示
-- ============================================================

CREATE TABLE `edu_consult_msg` (
  `id` BIGINT NOT NULL AUTO_INCREMENT,
  `consult_id` BIGINT NOT NULL COMMENT '咨询ID（edu_consult.id）',
  `sender` TINYINT NOT NULL COMMENT '发送方：0学生 1老师',
  `content` VARCHAR(500) NOT NULL COMMENT '消息内容',
  `create_time` DATETIME DEFAULT CURRENT_TIMESTAMP,
  PRIMARY KEY (`id`), KEY `idx_consult` (`consult_id`)
) COMMENT '咨询追问消息表';

-- ============================================================
-- V2.9 缴费管理升级：缴费记录补充操作人（新增/修改留痕）
-- ============================================================

ALTER TABLE edu_payment
    ADD COLUMN update_by VARCHAR(32) DEFAULT NULL COMMENT '操作人（最后新增/修改人）';

-- ============================================================
-- V2.9 用户类型入系统字典（用户管理下拉/标签从字典取数）
-- ============================================================

INSERT INTO sys_dict (dict_type, label, value, sort) VALUES
('user_type', '运营总负责人', '1', 1),
('user_type', '教务人员', '2', 2),
('user_type', '班主任', '3', 3),
('user_type', '教师', '4', 4),
('user_type', '学生', '5', 5),
('user_type', '校区校长', '6', 6),
('user_type', '校区咨询师', '7', 7),
('user_type', '校区学管师', '8', 8),
('user_type', '系统用户', '9', 9);

-- ============================================================
-- V2.9 一次性修复：合同已缴金额按缴费流水重算（历史缴费未同步合同口径）
-- ============================================================

UPDATE edu_contract c SET
  paid_amount = IFNULL((SELECT SUM(p.amount) FROM edu_payment p WHERE p.contract_id = c.id), 0);
UPDATE edu_contract c SET status = CASE
  WHEN status IN (3, 4) THEN status
  WHEN paid_amount <= 0 THEN 0
  WHEN paid_amount >= total_amount THEN 2
  ELSE 1 END;

-- ============================================================
-- V2.9 缴费操作记录：新增/修改/删除全量留痕（缴费管理"操作记录"TAB）
-- ============================================================

CREATE TABLE `edu_payment_log` (
  `id` BIGINT NOT NULL AUTO_INCREMENT,
  `tenant_id` BIGINT DEFAULT 0,
  `payment_id` BIGINT DEFAULT NULL COMMENT '缴费ID（edu_payment.id）',
  `payment_no` VARCHAR(50) DEFAULT NULL COMMENT '缴费单号',
  `action` TINYINT NOT NULL COMMENT '操作类型：1新增 2修改 3删除',
  `student_id` BIGINT DEFAULT NULL COMMENT '学生ID（数据权限过滤）',
  `amount` DECIMAL(10,2) DEFAULT NULL COMMENT '操作后实缴金额',
  `amount_before` DECIMAL(10,2) DEFAULT NULL COMMENT '修改前金额（新增时NULL）',
  `detail` VARCHAR(500) DEFAULT NULL COMMENT '变更明细（改了哪些字段、前后值）',
  `operator` VARCHAR(50) DEFAULT NULL COMMENT '操作人',
  `org_id` BIGINT DEFAULT NULL,
  `create_time` DATETIME DEFAULT CURRENT_TIMESTAMP,
  PRIMARY KEY (`id`),
  KEY `idx_payment` (`payment_id`),
  KEY `idx_student` (`student_id`)
) COMMENT '缴费操作记录表';
