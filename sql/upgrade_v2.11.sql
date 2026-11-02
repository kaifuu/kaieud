-- ============================================================
-- V2.11 系统字典：字典类型中文名持久化（sys_dict_type）
-- 修复：字典管理里给类型改中文名（如 user_type→用户类型）只存在
-- 前端内存映射，刷新即丢失；现落库持久保存
-- ============================================================

CREATE TABLE `sys_dict_type` (
  `id` BIGINT NOT NULL AUTO_INCREMENT,
  `dict_type` VARCHAR(50) NOT NULL COMMENT '类型标识（对应 sys_dict.dict_type）',
  `name` VARCHAR(50) NOT NULL COMMENT '类型中文名（字典管理左侧类型列表显示名）',
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_dict_type` (`dict_type`)
) COMMENT '字典类型中文名';

-- 存量类型初始化中文名
INSERT INTO sys_dict_type (dict_type, name) VALUES
('class_type', '班型'),
('course_category', '课程分类'),
('pay_channel', '缴费方式'),
('student_status', '学员状态'),
('contract_status', '合同状态'),
('campus', '所属校区'),
('user_type', '用户类型')
ON DUPLICATE KEY UPDATE name = VALUES(name);
