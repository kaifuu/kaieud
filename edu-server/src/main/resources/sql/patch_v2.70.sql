-- v2.70 试听学员快照补充：办试听时间 / 助教（列表展示）
ALTER TABLE edu_audition_student
  ADD COLUMN audition_date DATE NULL COMMENT '办试听时间' AFTER campus,
  ADD COLUMN assistant_name VARCHAR(64) NULL COMMENT '助教' AFTER audition_date;
