-- v2.68 试听管理（报名管理下新增菜单）：
--   插班试听（从已成班课选择关联日程回填）/ 开班试听（全新录入）
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
