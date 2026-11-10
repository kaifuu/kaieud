-- 清空课程/套餐/教室/排课及其关联数据（重新测试用）
-- 课程 + 课程校区关联 + 课程定价；课时套餐；教室；（排课及日志上一脚本已清，此处兜底）
-- 引用方（班级/报名/合同/消课等）已在先前脚本清空，无悬挂
TRUNCATE TABLE edu_course;
TRUNCATE TABLE edu_course_campus;
TRUNCATE TABLE edu_course_price;
TRUNCATE TABLE edu_package;
TRUNCATE TABLE edu_classroom;
TRUNCATE TABLE edu_schedule;
TRUNCATE TABLE edu_schedule_log;
