-- v2.64~v2.68 合并补丁：班级班容/课时列、合同日志表、教室校区列、授课模式字典收敛、试听管理

ALTER TABLE edu_clazz
  ADD COLUMN min_capacity INT NULL COMMENT '最少开班人数' AFTER capacity,
  ADD COLUMN student_hours DECIMAL(6,1) NULL COMMENT '每次上课学生扣除课时' AFTER min_capacity,
  ADD COLUMN teacher_hours DECIMAL(6,1) NULL COMMENT '每次上课教师获得课时' AFTER student_hours;

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

ALTER TABLE edu_classroom
  ADD COLUMN campus VARCHAR(64) NULL COMMENT '所属校区（系统字典-校区名）' AFTER org_id;

UPDATE sys_dict SET label = '班课' WHERE dict_type = 'class_type' AND label = '大班课';
DELETE FROM sys_dict WHERE dict_type = 'class_type' AND label IN ('一对二', '一对三', '一对四', '一对五');

CREATE TABLE IF NOT EXISTS `edu_audition` (
  `id` bigint NOT NULL AUTO_INCREMENT,
  `tenant_id` bigint NOT NULL DEFAULT '0',
  `type` tinyint NOT NULL DEFAULT '1' COMMENT '1插班试听 2开班试听',
  `name` varchar(64) NOT NULL COMMENT '名称',
  `audition_date` date DEFAULT NULL COMMENT '日期',
  `campus` varchar(64) DEFAULT NULL COMMENT '校区',
  `course_name` varchar(128) DEFAULT NULL COMMENT '课程（插班=关联班级课程名，开班=自由录入）',
  `teacher_name` varchar(64) DEFAULT NULL COMMENT '教师',
  `classroom_name` varchar(64) DEFAULT NULL COMMENT '教室',
  `time_period` varchar(64) DEFAULT NULL COMMENT '时段（如 周六 09:00-10:30）',
  `assistant_name` varchar(64) DEFAULT NULL COMMENT '助教',
  `planned_count` int DEFAULT NULL COMMENT '预招人数',
  `relate_clazz_id` bigint DEFAULT NULL COMMENT '插班试听关联班级ID',
  `remark` varchar(255) DEFAULT NULL,
  `operator` varchar(64) DEFAULT NULL COMMENT '经办人',
  `create_time` datetime DEFAULT CURRENT_TIMESTAMP,
  PRIMARY KEY (`id`),
  KEY `idx_tenant` (`tenant_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_general_ci COMMENT='试听管理';
-- 菜单：各租户在「报名管理」之后插入「试听管理」
INSERT INTO sys_menu (tenant_id, parent_id, name, path, component, perm, type, sort, visible, scope)
SELECT m.tenant_id, m.parent_id, '试听管理', 'audition', 'edu/Audition', 'edu:audition', 'C', m.sort + 1, 1, 0
FROM sys_menu m
WHERE m.name = '报名管理' AND m.type = 'C'
  AND NOT EXISTS (SELECT 1 FROM sys_menu x WHERE x.tenant_id = m.tenant_id AND x.name = '试听管理');
-- 角色绑定：凡拥有该租户「报名管理」菜单的角色同步授予「试听管理」
INSERT INTO sys_role_menu (role_id, menu_id)
SELECT DISTINCT rm.role_id, a.id
FROM sys_menu a
JOIN sys_menu e ON e.tenant_id = a.tenant_id AND e.name = '报名管理' AND e.type = 'C'
JOIN sys_role_menu rm ON rm.menu_id = e.id
WHERE a.name = '试听管理' AND a.type = 'C'
  AND NOT EXISTS (SELECT 1 FROM sys_role_menu rx WHERE rx.role_id = rm.role_id AND rx.menu_id = a.id);

