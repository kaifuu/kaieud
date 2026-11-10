-- v2.64 班级管理升级：
--   容量拆为 最少开班人数(min_capacity) + 满班人数(capacity 沿用)
--   每次上课：学生扣除课时(student_hours) / 教师获得课时(teacher_hours)
--   （开班日期 start_date / 结班日期 end_date 列已存在，结班日期本次在页面暴露）
ALTER TABLE edu_clazz
  ADD COLUMN min_capacity INT NULL COMMENT '最少开班人数' AFTER capacity,
  ADD COLUMN student_hours DECIMAL(6,1) NULL COMMENT '每次上课学生扣除课时' AFTER min_capacity,
  ADD COLUMN teacher_hours DECIMAL(6,1) NULL COMMENT '每次上课教师获得课时' AFTER student_hours;
