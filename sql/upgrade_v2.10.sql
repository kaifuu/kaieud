-- ============================================================
-- V2.10 合同退费升级：新增退费记录表（edu_refund）
-- 每次退费留痕（单号/课时/金额/原因/操作人），
-- 退费页"已退课时"按学员汇总本表
-- ============================================================

CREATE TABLE `edu_refund` (
  `id` BIGINT NOT NULL AUTO_INCREMENT,
  `tenant_id` BIGINT DEFAULT 0,
  `refund_no` VARCHAR(50) DEFAULT NULL COMMENT '退费单号',
  `contract_id` BIGINT NOT NULL COMMENT '合同ID（edu_contract.id）',
  `student_id` BIGINT DEFAULT NULL COMMENT '学员ID（已退课时按学员汇总）',
  `course_id` BIGINT DEFAULT NULL COMMENT '课程ID',
  `refund_hours` DECIMAL(6,2) DEFAULT 0 COMMENT '退费课时',
  `refund_amount` DECIMAL(12,2) DEFAULT 0 COMMENT '退费金额',
  `remain_hours` DECIMAL(6,2) DEFAULT NULL COMMENT '退费时合同剩余课时（快照）',
  `reason` VARCHAR(500) DEFAULT NULL COMMENT '退费原因',
  `operator` VARCHAR(50) DEFAULT NULL COMMENT '操作人',
  `org_id` BIGINT DEFAULT NULL,
  `create_time` DATETIME DEFAULT CURRENT_TIMESTAMP,
  PRIMARY KEY (`id`),
  KEY `idx_contract` (`contract_id`),
  KEY `idx_student` (`student_id`)
) COMMENT '合同退费记录表';

-- 历史已退费合同补退费记录（按合同退费字段合成，保证已退课时口径完整）
INSERT INTO edu_refund (tenant_id, refund_no, contract_id, student_id, course_id,
                        refund_hours, refund_amount, remain_hours, reason, operator, org_id)
SELECT c.tenant_id, CONCAT('RF', c.id), c.id, c.student_id, c.course_id,
       IFNULL(c.refund_hours, 0), IFNULL(c.refund_amount, 0),
       (IFNULL(c.purchased_hours, 0) + IFNULL(c.gift_hours, 0)
        - IFNULL(c.used_purchased, 0) - IFNULL(c.used_gift, 0)),
       '历史退费数据迁移', '系统', c.org_id
FROM edu_contract c
WHERE c.status = 3;
