-- 清空教务管理各功能记录（重新测试用）
-- 排课+调课日志 / 请假 / 缺勤 / 咨询+咨询消息 / 报名+报名日志 / 消课 / 班级
-- 保留基础配置：课程/课程校区/定价、课时套餐、教室、学员、教师、用户、字典
TRUNCATE TABLE edu_schedule;
TRUNCATE TABLE edu_schedule_log;
TRUNCATE TABLE edu_leave;
TRUNCATE TABLE edu_absence;
TRUNCATE TABLE edu_consult;
TRUNCATE TABLE edu_consult_msg;
TRUNCATE TABLE edu_enroll;
TRUNCATE TABLE edu_enroll_log;
TRUNCATE TABLE edu_consume;
TRUNCATE TABLE edu_clazz;
