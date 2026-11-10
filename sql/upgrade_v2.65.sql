-- v2.65 合同管理升级：
--   1) 列表删除改为物理删除（有缴费/消课/退费记录的合同禁止删除）
--   2) 新增合同操作日志表 edu_contract_log（合同历史TAB数据源：新增/修改/退费/删除全量留痕）
CREATE TABLE IF NOT EXISTS `edu_contract_log` (
  `id` bigint NOT NULL AUTO_INCREMENT,
  `tenant_id` bigint NOT NULL DEFAULT '0',
  `contract_id` bigint NOT NULL COMMENT '合同ID',
  `contract_no` varchar(64) DEFAULT NULL COMMENT '合同编号（合同删除后仍可追溯）',
  `student_id` bigint DEFAULT NULL COMMENT '学员ID',
  `action` varchar(16) NOT NULL COMMENT '新增/修改/退费/删除',
  `content` varchar(500) DEFAULT NULL COMMENT '操作内容摘要',
  `operator` varchar(64) DEFAULT NULL COMMENT '操作人',
  `create_time` datetime DEFAULT CURRENT_TIMESTAMP COMMENT '操作时间',
  PRIMARY KEY (`id`),
  KEY `idx_contract` (`contract_id`),
  KEY `idx_student` (`student_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_general_ci COMMENT='合同操作日志';
