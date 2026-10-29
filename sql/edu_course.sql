-- =====================================================
-- 课时管理系统数据库初始化脚本（MySQL 8.x）
-- 默认账号：admin / 123456 （密码存储：MD5(明文 + 'edu')）
-- =====================================================
CREATE DATABASE IF NOT EXISTS `edu_course` DEFAULT CHARACTER SET utf8mb4 COLLATE utf8mb4_general_ci;
USE `edu_course`;

-- ---------------- 系统管理 ----------------
CREATE TABLE `sys_tenant` (
  `id` BIGINT NOT NULL AUTO_INCREMENT COMMENT '租户ID',
  `name` VARCHAR(64) NOT NULL COMMENT '租户名称',
  `code` VARCHAR(32) NOT NULL COMMENT '租户编码',
  `contact_person` VARCHAR(32) DEFAULT NULL COMMENT '联系人',
  `contact_phone` VARCHAR(20) DEFAULT NULL COMMENT '联系电话',
  `address` VARCHAR(255) DEFAULT NULL COMMENT '地址',
  `status` TINYINT NOT NULL DEFAULT 1 COMMENT '状态 1启用 0停用',
  `create_time` DATETIME DEFAULT CURRENT_TIMESTAMP,
  `update_time` DATETIME DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  PRIMARY KEY (`id`), UNIQUE KEY `uk_code` (`code`)
) COMMENT '租户表';

CREATE TABLE `sys_org` (
  `id` BIGINT NOT NULL AUTO_INCREMENT,
  `tenant_id` BIGINT NOT NULL COMMENT '租户ID',
  `parent_id` BIGINT NOT NULL DEFAULT 0 COMMENT '父节点',
  `name` VARCHAR(64) NOT NULL COMMENT '组织名称(校区)',
  `leader` VARCHAR(32) DEFAULT NULL COMMENT '负责人',
  `phone` VARCHAR(20) DEFAULT NULL,
  `sort` INT NOT NULL DEFAULT 0,
  `status` TINYINT NOT NULL DEFAULT 1,
  `create_time` DATETIME DEFAULT CURRENT_TIMESTAMP,
  PRIMARY KEY (`id`), KEY `idx_tenant` (`tenant_id`)
) COMMENT '组织/校区表';

CREATE TABLE `sys_user` (
  `id` BIGINT NOT NULL AUTO_INCREMENT,
  `tenant_id` BIGINT NOT NULL DEFAULT 1 COMMENT '租户ID(超级管理员为0)',
  `org_id` BIGINT DEFAULT NULL COMMENT '所属组织',
  `username` VARCHAR(32) NOT NULL COMMENT '登录名',
  `password` CHAR(32) NOT NULL COMMENT 'MD5(明文+edu)',
  `real_name` VARCHAR(32) NOT NULL COMMENT '姓名',
  `gender` TINYINT DEFAULT 1 COMMENT '1男 2女',
  `phone` VARCHAR(20) DEFAULT NULL,
  `email` VARCHAR(64) DEFAULT NULL,
  `user_type` TINYINT NOT NULL COMMENT '1超管 2教管人员 3班主任 4教师 5学生',
  `status` TINYINT NOT NULL DEFAULT 1 COMMENT '1启用 0停用',
  `last_login_time` DATETIME DEFAULT NULL,
  `create_time` DATETIME DEFAULT CURRENT_TIMESTAMP,
  PRIMARY KEY (`id`), UNIQUE KEY `uk_username` (`username`)
) COMMENT '用户表';

CREATE TABLE `sys_menu` (
  `id` BIGINT NOT NULL,
  `parent_id` BIGINT NOT NULL DEFAULT 0,
  `name` VARCHAR(32) NOT NULL COMMENT '菜单名称',
  `path` VARCHAR(128) DEFAULT NULL COMMENT '路由地址',
  `component` VARCHAR(128) DEFAULT NULL COMMENT '前端组件路径',
  `icon` VARCHAR(64) DEFAULT NULL,
  `perm` VARCHAR(64) DEFAULT NULL COMMENT '权限标识',
  `type` CHAR(1) NOT NULL DEFAULT 'C' COMMENT 'M目录 C菜单',
  `sort` INT NOT NULL DEFAULT 0,
  `visible` TINYINT NOT NULL DEFAULT 1,
  PRIMARY KEY (`id`)
) COMMENT '菜单表';

CREATE TABLE `sys_role` (
  `id` BIGINT NOT NULL AUTO_INCREMENT,
  `tenant_id` BIGINT NOT NULL DEFAULT 1,
  `name` VARCHAR(32) NOT NULL,
  `code` VARCHAR(32) NOT NULL COMMENT 'SUPER_ADMIN/EDU_ADMIN/CLASS_TEACHER/TEACHER/STUDENT',
  `remark` VARCHAR(255) DEFAULT NULL,
  `create_time` DATETIME DEFAULT CURRENT_TIMESTAMP,
  PRIMARY KEY (`id`)
) COMMENT '角色表';

CREATE TABLE `sys_role_menu` (
  `id` BIGINT NOT NULL AUTO_INCREMENT,
  `role_id` BIGINT NOT NULL,
  `menu_id` BIGINT NOT NULL,
  PRIMARY KEY (`id`), UNIQUE KEY `uk_rm` (`role_id`,`menu_id`)
) COMMENT '角色-菜单';

CREATE TABLE `sys_user_role` (
  `id` BIGINT NOT NULL AUTO_INCREMENT,
  `user_id` BIGINT NOT NULL,
  `role_id` BIGINT NOT NULL,
  PRIMARY KEY (`id`), UNIQUE KEY `uk_ur` (`user_id`,`role_id`)
) COMMENT '用户-角色';

CREATE TABLE `sys_log` (
  `id` BIGINT NOT NULL AUTO_INCREMENT,
  `tenant_id` BIGINT DEFAULT NULL,
  `user_id` BIGINT DEFAULT NULL,
  `username` VARCHAR(32) DEFAULT NULL,
  `operation` VARCHAR(128) DEFAULT NULL COMMENT '操作内容',
  `method` VARCHAR(255) DEFAULT NULL,
  `params` TEXT,
  `ip` VARCHAR(64) DEFAULT NULL,
  `duration` INT DEFAULT 0 COMMENT '耗时ms',
  `status` TINYINT DEFAULT 0 COMMENT '0成功 1失败',
  `error_msg` TEXT,
  `create_time` DATETIME DEFAULT CURRENT_TIMESTAMP,
  PRIMARY KEY (`id`), KEY `idx_create` (`create_time`)
) COMMENT '操作日志表';

-- ---------------- 教务管理 ----------------
CREATE TABLE `edu_student` (
  `id` BIGINT NOT NULL AUTO_INCREMENT,
  `tenant_id` BIGINT NOT NULL,
  `user_id` BIGINT DEFAULT NULL COMMENT '关联登录账号',
  `name` VARCHAR(32) NOT NULL,
  `gender` TINYINT DEFAULT 1 COMMENT '1男 2女',
  `birth_date` DATE DEFAULT NULL,
  `school` VARCHAR(64) DEFAULT NULL COMMENT '就读学校(统计维度)',
  `grade` VARCHAR(32) DEFAULT NULL COMMENT '年级',
  `parent_name` VARCHAR(32) DEFAULT NULL,
  `parent_phone` VARCHAR(20) DEFAULT NULL,
  `address` VARCHAR(255) DEFAULT NULL,
  `remark` VARCHAR(255) DEFAULT NULL,
  `create_time` DATETIME DEFAULT CURRENT_TIMESTAMP,
  PRIMARY KEY (`id`), KEY `idx_tenant` (`tenant_id`)
) COMMENT '学生表';

CREATE TABLE `edu_teacher` (
  `id` BIGINT NOT NULL AUTO_INCREMENT,
  `tenant_id` BIGINT NOT NULL,
  `user_id` BIGINT DEFAULT NULL COMMENT '关联登录账号',
  `name` VARCHAR(32) NOT NULL,
  `gender` TINYINT DEFAULT 1,
  `phone` VARCHAR(20) DEFAULT NULL,
  `email` VARCHAR(64) DEFAULT NULL,
  `subject` VARCHAR(32) DEFAULT NULL COMMENT '授课科目',
  `title` VARCHAR(32) DEFAULT NULL COMMENT '职称',
  `intro` VARCHAR(500) DEFAULT NULL,
  `hourly_rate` DECIMAL(10,2) DEFAULT 0 COMMENT '课时费',
  `status` TINYINT DEFAULT 1 COMMENT '1在职 0离职',
  `create_time` DATETIME DEFAULT CURRENT_TIMESTAMP,
  PRIMARY KEY (`id`), KEY `idx_tenant` (`tenant_id`)
) COMMENT '教师表';

CREATE TABLE `edu_course` (
  `id` BIGINT NOT NULL AUTO_INCREMENT,
  `tenant_id` BIGINT NOT NULL,
  `name` VARCHAR(64) NOT NULL COMMENT '课程/班型名称',
  `course_type` TINYINT NOT NULL DEFAULT 1 COMMENT '1班课 2一对一',
  `subject` VARCHAR(32) DEFAULT NULL COMMENT '科目',
  `total_hours` INT NOT NULL DEFAULT 0 COMMENT '总课时',
  `price` DECIMAL(10,2) NOT NULL DEFAULT 0 COMMENT '单价/课时',
  `discount` DECIMAL(5,2) DEFAULT 100 COMMENT '折扣(%)',
  `status` TINYINT DEFAULT 1 COMMENT '1上架 0下架',
  `remark` VARCHAR(255) DEFAULT NULL,
  `create_time` DATETIME DEFAULT CURRENT_TIMESTAMP,
  PRIMARY KEY (`id`), KEY `idx_tenant` (`tenant_id`)
) COMMENT '课程(班型)表';

CREATE TABLE `edu_clazz` (
  `id` BIGINT NOT NULL AUTO_INCREMENT,
  `tenant_id` BIGINT NOT NULL,
  `name` VARCHAR(64) NOT NULL COMMENT '班级名称',
  `course_id` BIGINT NOT NULL COMMENT '课程ID',
  `class_teacher_id` BIGINT DEFAULT NULL COMMENT '班主任(教师表ID)',
  `org_id` BIGINT DEFAULT NULL COMMENT '所属校区',
  `capacity` INT NOT NULL DEFAULT 20,
  `start_date` DATE DEFAULT NULL,
  `end_date` DATE DEFAULT NULL,
  `status` TINYINT DEFAULT 1 COMMENT '1招生中 2开课中 3已结课',
  `remark` VARCHAR(255) DEFAULT NULL,
  `create_time` DATETIME DEFAULT CURRENT_TIMESTAMP,
  PRIMARY KEY (`id`), KEY `idx_tenant` (`tenant_id`)
) COMMENT '班级表';

CREATE TABLE `edu_enroll` (
  `id` BIGINT NOT NULL AUTO_INCREMENT,
  `tenant_id` BIGINT NOT NULL,
  `student_id` BIGINT NOT NULL,
  `course_id` BIGINT NOT NULL,
  `clazz_id` BIGINT DEFAULT NULL COMMENT '班课班级(一对一为空)',
  `teacher_id` BIGINT DEFAULT NULL COMMENT '指派授课老师(教师表ID)',
  `total_hours` INT NOT NULL DEFAULT 0,
  `used_hours` INT NOT NULL DEFAULT 0 COMMENT '已消耗课时',
  `price` DECIMAL(10,2) NOT NULL DEFAULT 0 COMMENT '课时单价',
  `discount` DECIMAL(5,2) DEFAULT 100 COMMENT '折扣%',
  `total_amount` DECIMAL(12,2) NOT NULL DEFAULT 0 COMMENT '应收总额=单价*课时*折扣',
  `paid_amount` DECIMAL(12,2) DEFAULT 0 COMMENT '已缴金额',
  `status` TINYINT DEFAULT 1 COMMENT '1在读 2已结课 3已退费',
  `create_time` DATETIME DEFAULT CURRENT_TIMESTAMP,
  PRIMARY KEY (`id`), KEY `idx_tenant` (`tenant_id`), KEY `idx_student` (`student_id`), KEY `idx_teacher` (`teacher_id`)
) COMMENT '报名/课时合同表';

CREATE TABLE `edu_schedule` (
  `id` BIGINT NOT NULL AUTO_INCREMENT,
  `tenant_id` BIGINT NOT NULL,
  `course_id` BIGINT NOT NULL,
  `clazz_id` BIGINT DEFAULT NULL COMMENT '班课ID(一对一为空)',
  `teacher_id` BIGINT NOT NULL COMMENT '授课老师(教师表ID)',
  `student_id` BIGINT DEFAULT NULL COMMENT '学生(一对一专用)',
  `classroom` VARCHAR(64) DEFAULT NULL,
  `start_time` DATETIME NOT NULL,
  `end_time` DATETIME NOT NULL,
  `status` TINYINT DEFAULT 0 COMMENT '0待上课 1已完成 2已取消',
  `cancel_reason` VARCHAR(255) DEFAULT NULL,
  `origin_id` BIGINT DEFAULT NULL COMMENT '补课来源课次ID',
  `compensation` TINYINT DEFAULT 0 COMMENT '1补课课时',
  `remark` VARCHAR(255) DEFAULT NULL,
  `create_time` DATETIME DEFAULT CURRENT_TIMESTAMP,
  PRIMARY KEY (`id`), KEY `idx_tenant` (`tenant_id`), KEY `idx_teacher` (`teacher_id`), KEY `idx_start` (`start_time`)
) COMMENT '排课表';

CREATE TABLE `edu_leave` (
  `id` BIGINT NOT NULL AUTO_INCREMENT,
  `tenant_id` BIGINT NOT NULL,
  `student_id` BIGINT NOT NULL,
  `schedule_id` BIGINT NOT NULL,
  `reason` VARCHAR(255) NOT NULL,
  `status` TINYINT DEFAULT 0 COMMENT '0待审批 1已同意 2已拒绝',
  `create_time` DATETIME DEFAULT CURRENT_TIMESTAMP,
  PRIMARY KEY (`id`)
) COMMENT '学生请假表';

CREATE TABLE `edu_evaluation` (
  `id` BIGINT NOT NULL AUTO_INCREMENT,
  `tenant_id` BIGINT NOT NULL,
  `student_id` BIGINT NOT NULL,
  `teacher_id` BIGINT NOT NULL,
  `course_id` BIGINT DEFAULT NULL,
  `schedule_id` BIGINT DEFAULT NULL,
  `score` TINYINT NOT NULL DEFAULT 5 COMMENT '评分1-5',
  `content` VARCHAR(500) DEFAULT NULL,
  `create_time` DATETIME DEFAULT CURRENT_TIMESTAMP,
  PRIMARY KEY (`id`), KEY `idx_teacher` (`teacher_id`)
) COMMENT '学生对教师的评价';

CREATE TABLE `edu_consult` (
  `id` BIGINT NOT NULL AUTO_INCREMENT,
  `tenant_id` BIGINT NOT NULL,
  `student_id` BIGINT NOT NULL,
  `teacher_id` BIGINT NOT NULL,
  `content` VARCHAR(500) NOT NULL COMMENT '咨询内容',
  `reply` VARCHAR(500) DEFAULT NULL,
  `reply_time` DATETIME DEFAULT NULL,
  `status` TINYINT DEFAULT 0 COMMENT '0待回复 1已回复',
  `create_time` DATETIME DEFAULT CURRENT_TIMESTAMP,
  PRIMARY KEY (`id`), KEY `idx_teacher` (`teacher_id`)
) COMMENT '问题咨询表';

CREATE TABLE `edu_payment` (
  `id` BIGINT NOT NULL AUTO_INCREMENT,
  `tenant_id` BIGINT NOT NULL,
  `payment_no` VARCHAR(32) NOT NULL COMMENT '缴费单号',
  `student_id` BIGINT NOT NULL,
  `course_id` BIGINT NOT NULL,
  `enroll_id` BIGINT DEFAULT NULL,
  `amount` DECIMAL(12,2) NOT NULL COMMENT '实缴金额',
  `discount_amount` DECIMAL(12,2) DEFAULT 0 COMMENT '优惠金额',
  `pay_type` TINYINT DEFAULT 1 COMMENT '1现金 2微信 3支付宝 4银行转账',
  `pay_time` DATETIME DEFAULT CURRENT_TIMESTAMP,
  `operator` VARCHAR(32) DEFAULT NULL,
  `remark` VARCHAR(255) DEFAULT NULL,
  `create_time` DATETIME DEFAULT CURRENT_TIMESTAMP,
  PRIMARY KEY (`id`), UNIQUE KEY `uk_no` (`payment_no`), KEY `idx_tenant` (`tenant_id`)
) COMMENT '缴费记录表';

-- =====================================================
-- 初始化数据
-- =====================================================
INSERT INTO `sys_tenant` (`id`,`name`,`code`,`contact_person`,`contact_phone`,`status`) VALUES
(1,'总部校区','HQ','张老师','13800000001',1);

INSERT INTO `sys_org` (`id`,`tenant_id`,`parent_id`,`name`,`leader`,`phone`,`sort`) VALUES
(1,1,0,'总校','王校长','13800000002',1),
(2,1,1,'高新分校','李主任','13800000003',2),
(3,1,1,'城东分校','赵主任','13800000004',3);

INSERT INTO `sys_menu` (`id`,`parent_id`,`name`,`path`,`component`,`icon`,`perm`,`type`,`sort`) VALUES
(1,0,'系统管理','/system',NULL,'Setting','sys','M',1),
(101,1,'租户管理','tenant','system/tenant','OfficeBuilding','sys:tenant','C',1),
(102,1,'组织管理','org','system/org','School','sys:org','C',2),
(103,1,'用户管理','user','system/user','User','sys:user','C',3),
(104,1,'菜单管理','menu','system/menu','Menu','sys:menu','C',4),
(105,1,'角色管理','role','system/role','UserFilled','sys:role','C',5),
(106,1,'日志管理','log','system/log','Document','sys:log','C',6),
(2,0,'教务管理','/edu',NULL,'Reading','edu','M',2),
(201,2,'学生管理','student','edu/student','Avatar','edu:student','C',1),
(202,2,'班主任管理','class-teacher','edu/classTeacher','UserFilled','edu:classTeacher','C',2),
(203,2,'班级管理','clazz','edu/clazz','Files','edu:clazz','C',3),
(204,2,'课程管理','course','edu/course','Notebook','edu:course','C',4),
(205,2,'排课管理','schedule','edu/schedule','Calendar','edu:schedule','C',5),
(206,2,'缴费管理','payment','edu/payment','Money','edu:payment','C',6),
(207,2,'请假管理','leave','edu/leave','Clock','edu:leave','C',7),
(208,2,'咨询与评价','consult','edu/consult','ChatDotRound','edu:consult','C',8),
(3,0,'统计汇总','/stats','stats/index','DataAnalysis','edu:stats','C',3),
(4,0,'首页概览','/home','home/index','Home','', 'C',0),
(5,0,'学生中心','/portal',NULL,'Guide','portal','M',4),
(501,5,'选课中心','course-select','portal/CourseSelect','Goods','portal','C',1),
(502,5,'我的课表','my-schedule','portal/MySchedule','Calendar','portal','C',2),
(503,5,'我的老师','my-teacher','portal/MyTeacher','User','portal','C',3),
(6,0,'教师中心','/teach',NULL,'Suitcase','teach','M',5),
(601,6,'我的课表','schedule','teach/Schedule','Calendar','teach','C',1),
(602,6,'我的学生','students','teach/MyStudents','Avatar','teach','C',2),
(603,6,'收到的评价','evaluations','teach/Evaluations','Star','teach','C',3),
(604,6,'问题咨询','consults','teach/Consults','ChatDotRound','teach','C',4),
(605,6,'我的收入','income','teach/Income','Money','teach','C',5);

INSERT INTO `sys_role` (`id`,`tenant_id`,`name`,`code`,`remark`) VALUES
(1,1,'超级管理员','SUPER_ADMIN','拥有全部权限'),
(2,1,'教管人员','EDU_ADMIN','教务/统计管理'),
(3,1,'班主任','CLASS_TEACHER','班级及学生管理'),
(4,1,'教师','TEACHER','授课与个人事务'),
(5,1,'学生','STUDENT','选课与学习');

-- 角色-菜单：超管全部
INSERT INTO `sys_role_menu` (`role_id`,`menu_id`)
SELECT 1,`id` FROM `sys_menu`;
-- 教管人员：教务+统计+首页
INSERT INTO `sys_role_menu` (`role_id`,`menu_id`) VALUES
(2,2),(2,201),(2,202),(2,203),(2,204),(2,205),(2,206),(2,207),(2,208),(2,3),(2,4);
-- 班主任：教务+统计+首页
INSERT INTO `sys_role_menu` (`role_id`,`menu_id`) VALUES
(3,2),(3,201),(3,202),(3,203),(3,204),(3,205),(3,206),(3,207),(3,208),(3,3),(3,4);
-- 教师：教师中心+首页
INSERT INTO `sys_role_menu` (`role_id`,`menu_id`) VALUES
(4,6),(4,601),(4,602),(4,603),(4,604),(4,605),(4,4);
-- 学生：学生中心+首页
INSERT INTO `sys_role_menu` (`role_id`,`menu_id`) VALUES
(5,5),(5,501),(5,502),(5,503),(5,4);

-- 用户（密码均为 123456：MD5('123456edu')=60783a8aa981454b3ade403d6577befd）
INSERT INTO `sys_user` (`id`,`tenant_id`,`org_id`,`username`,`password`,`real_name`,`gender`,`phone`,`user_type`) VALUES
(1,0,NULL,'admin','60783a8aa981454b3ade403d6577befd','系统管理员',1,'13800000000',1),
(2,1,1,'edu01','60783a8aa981454b3ade403d6577befd','钱教务',2,'13800000010',2),
(3,1,1,'ct01','60783a8aa981454b3ade403d6577befd','孙班主任',1,'13800000011',3),
(4,1,1,'teacher01','60783a8aa981454b3ade403d6577befd','周老师',1,'13800000012',4),
(5,1,2,'teacher02','60783a8aa981454b3ade403d6577befd','吴老师',2,'13800000013',4),
(6,1,1,'student01','60783a8aa981454b3ade403d6577befd','小明',1,'13800000014',5);

INSERT INTO `sys_user_role` (`user_id`,`role_id`) VALUES
(1,1),(2,2),(3,3),(4,4),(5,4),(6,5);

INSERT INTO `edu_teacher` (`id`,`tenant_id`,`user_id`,`name`,`gender`,`phone`,`subject`,`title`,`intro`,`hourly_rate`) VALUES
(1,1,4,'周老师',1,'13800000012','数学','高级教师','十年数学教学经验，善于启发式教学。',200.00),
(2,1,5,'吴老师',2,'13800000013','英语','骨干教师','英语专业八级，口语流利。',180.00);

INSERT INTO `edu_student` (`id`,`tenant_id`,`user_id`,`name`,`gender`,`birth_date`,`school`,`grade`,`parent_name`,`parent_phone`) VALUES
(1,1,6,'小明',1,'2012-05-01','实验小学','五年级','明爸爸','13800000014'),
(2,1,NULL,'小红',2,'2011-09-10','实验中学','初一','红妈妈','13800000015'),
(3,1,NULL,'小刚',1,'2012-11-20','育才小学','五年级','刚爸爸','13800000016');

INSERT INTO `edu_course` (`id`,`tenant_id`,`name`,`course_type`,`subject`,`total_hours`,`price`,`discount`) VALUES
(1,1,'数学五年级培优班',1,'数学',48,150.00,90.00),
(2,1,'英语初一提高班',1,'英语',48,140.00,95.00),
(3,1,'数学一对一(五年级)',2,'数学',20,300.00,100.00);

INSERT INTO `edu_clazz` (`id`,`tenant_id`,`name`,`course_id`,`class_teacher_id`,`org_id`,`capacity`,`start_date`,`status`) VALUES
(1,1,'数学五年级培优A班',1,3,1,20,'2026-09-01',2),
(2,1,'英语初一提高班',2,3,2,20,'2026-09-05',2);

INSERT INTO `edu_enroll` (`id`,`tenant_id`,`student_id`,`course_id`,`clazz_id`,`teacher_id`,`total_hours`,`used_hours`,`price`,`discount`,`total_amount`,`paid_amount`,`status`) VALUES
(1,1,1,1,1,1,48,10,150.00,90.00,6480.00,6480.00,1),
(2,1,2,2,2,2,48,8,140.00,95.00,6384.00,6384.00,1),
(3,1,3,3,NULL,1,20,6,300.00,100.00,6000.00,6000.00,1);

INSERT INTO `edu_schedule` (`id`,`tenant_id`,`course_id`,`clazz_id`,`teacher_id`,`student_id`,`classroom`,`start_time`,`end_time`,`status`) VALUES
(1,1,1,1,1,NULL,'A101','2026-10-24 09:00:00','2026-10-24 11:00:00',1),
(2,1,1,1,1,NULL,'A101','2026-10-31 09:00:00','2026-10-31 11:00:00',0),
(3,1,2,2,2,NULL,'B201','2026-10-25 14:00:00','2026-10-25 16:00:00',1),
(4,1,2,2,2,NULL,'B201','2026-11-01 14:00:00','2026-11-01 16:00:00',0),
(5,1,3,NULL,1,3,'A102','2026-10-26 18:00:00','2026-10-26 20:00:00',0);

INSERT INTO `edu_payment` (`tenant_id`,`payment_no`,`student_id`,`course_id`,`enroll_id`,`amount`,`discount_amount`,`pay_type`,`operator`) VALUES
(1,'PAY20260901001',1,1,1,6480.00,720.00,2,'钱教务'),
(1,'PAY20260901002',2,2,2,6384.00,336.00,3,'钱教务'),
(1,'PAY20260905003',3,3,3,6000.00,0.00,4,'钱教务');

INSERT INTO `edu_evaluation` (`tenant_id`,`student_id`,`teacher_id`,`course_id`,`schedule_id`,`score`,`content`) VALUES
(1,1,1,1,1,5,'周老师讲课非常清楚，孩子很喜欢！'),
(1,2,2,2,3,4,'吴老师认真负责，发音标准。');

INSERT INTO `edu_consult` (`tenant_id`,`student_id`,`teacher_id`,`content`,`status`) VALUES
(1,1,1,'老师，下周的课程可以调整到周六下午吗？',0),
(1,2,2,'想问一下英语作业的提交方式。',1);
