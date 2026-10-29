-- =====================================================
-- V2.1 变更：移除「人员管理」目录及 学生管理/班主任管理/教师管理 菜单
-- 说明：仅删除菜单入口（导航与授权），学生/教师等页面组件、
--       后端接口与数据表全部保留，合同/消课/排课等功能不受影响。
-- =====================================================
USE edu_course;

DELETE FROM sys_role_menu WHERE menu_id IN (201, 202, 215, 230);
DELETE FROM sys_menu WHERE id IN (201, 202, 215, 230);

-- =====================================================
-- 恢复语句（如需找回，执行以下内容即可）
-- =====================================================
-- INSERT INTO sys_menu (id,parent_id,name,path,component,icon,perm,type,sort) VALUES
-- (201,230,'学生管理','student','edu/Student','Avatar','edu:student','C',1),
-- (202,230,'班主任管理','class-teacher','edu/ClassTeacher','UserFilled','edu:classTeacher','C',2),
-- (215,230,'教师管理','teacher','edu/Teacher','UserFilled','edu:teacher','C',3),
-- (230,0,'人员管理','/people',NULL,NULL,NULL,'M',3);
-- INSERT INTO sys_role_menu (role_id,menu_id) VALUES (1,201),(1,202),(1,215),(1,230),(2,201),(2,202),(2,215),(2,230),(3,201),(3,202),(3,215),(3,230);
