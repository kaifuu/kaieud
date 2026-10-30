-- ============================================================
-- V2.5 增量：已下课登记四态（到课/教师请假/学生请假/旷课）+ 旷课审批 + 课时管理
-- 1) edu_schedule.status 扩展：0待上课 1已到课 2已取消 3教师请假 4学生请假 5旷课待审批 6旷课已免扣 7旷课已扣课
-- 2) edu_consume 增加 source：扣课来源（消课/签到消课/到课/旷课），课时管理页面按来源展示
-- 3) 新表 edu_absence：旷课申请（审批通过免扣课；未批准超72小时系统自动扣课）
-- 4) 新菜单：233 旷课审批（教务管理） / 234 课时管理（教务管理，排课管理之后）
-- ============================================================
USE edu_course;

ALTER TABLE edu_schedule
    MODIFY COLUMN status TINYINT DEFAULT 0 COMMENT '0待上课 1已到课(已扣课) 2已取消 3教师请假(不扣课) 4学生请假(不扣课) 5旷课待审批 6旷课已免扣 7旷课已扣课';

ALTER TABLE edu_consume
    ADD COLUMN source VARCHAR(20) DEFAULT '消课' COMMENT '扣课来源：消课/签到消课/到课/旷课';

CREATE TABLE `edu_absence` (
  `id` BIGINT AUTO_INCREMENT PRIMARY KEY,
  `tenant_id` BIGINT DEFAULT NULL COMMENT '租户',
  `org_id` BIGINT DEFAULT NULL COMMENT '校区',
  `schedule_id` BIGINT NOT NULL COMMENT '课次',
  `student_id` BIGINT DEFAULT NULL COMMENT '学员(一对一)，班课为空=整班',
  `teacher_id` BIGINT DEFAULT NULL COMMENT '教师',
  `course_id` BIGINT DEFAULT NULL COMMENT '课程',
  `clazz_id` BIGINT DEFAULT NULL COMMENT '班级',
  `class_time` DATETIME DEFAULT NULL COMMENT '上课时间(冗余)',
  `reason` VARCHAR(255) DEFAULT NULL COMMENT '旷课说明',
  `status` TINYINT DEFAULT 0 COMMENT '0待审批 1已批准(免扣课) 2已驳回(已扣课) 3超72小时自动扣课',
  `apply_time` DATETIME DEFAULT NULL COMMENT '申请时间',
  `deadline` DATETIME DEFAULT NULL COMMENT '审批截止(申请+72小时，超时自动扣课)',
  `approver` VARCHAR(64) DEFAULT NULL COMMENT '审批人',
  `approve_time` DATETIME DEFAULT NULL COMMENT '审批时间',
  `approve_remark` VARCHAR(255) DEFAULT NULL COMMENT '审批备注',
  `deduct_time` DATETIME DEFAULT NULL COMMENT '扣课执行时间(驳回/超时自动)',
  `create_time` DATETIME DEFAULT CURRENT_TIMESTAMP,
  KEY `idx_schedule` (`schedule_id`),
  KEY `idx_status_deadline` (`status`, `deadline`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='旷课申请（审批流）';

INSERT INTO sys_menu (id, parent_id, name, path, component, icon, perm, type, sort) VALUES
(234, 2, '课时管理', 'hours', 'edu/Hours', 'Timer', 'edu:hours', 'C', 5),
(233, 2, '旷课审批', 'absence', 'edu/Absence', 'WarningFilled', 'edu:absence', 'C', 11);

-- 角色：超管(1)/教管(2)/班主任(3) 可见两个新页面
INSERT INTO sys_role_menu (role_id, menu_id) VALUES (1,233),(1,234),(2,233),(2,234),(3,233),(3,234);
