-- v2.76 班级管理-作业TAB：布置作业 + 学员提交/教师点评（点评进度、未提交、未读统计）
CREATE TABLE edu_homework (
  id BIGINT AUTO_INCREMENT PRIMARY KEY,
  tenant_id BIGINT NULL COMMENT '租户ID',
  clazz_id BIGINT NOT NULL COMMENT '班级',
  title VARCHAR(100) NOT NULL COMMENT '作业标题',
  content VARCHAR(1000) NULL COMMENT '作业内容/要求',
  teacher_id BIGINT NULL COMMENT '布置教师',
  deadline DATE NULL COMMENT '截止日期',
  status TINYINT NULL DEFAULT 0 COMMENT '0开放 1已关闭',
  create_time DATETIME NULL DEFAULT CURRENT_TIMESTAMP COMMENT '布置时间',
  update_time DATETIME NULL,
  KEY idx_clazz (clazz_id)
) COMMENT '班级作业';

CREATE TABLE edu_homework_submit (
  id BIGINT AUTO_INCREMENT PRIMARY KEY,
  tenant_id BIGINT NULL COMMENT '租户ID',
  homework_id BIGINT NOT NULL COMMENT '作业',
  student_id BIGINT NOT NULL COMMENT '学员',
  content VARCHAR(1000) NULL COMMENT '提交内容',
  submit_time DATETIME NULL COMMENT '提交时间',
  review_content VARCHAR(300) NULL COMMENT '教师点评',
  review_time DATETIME NULL COMMENT '点评时间',
  read_flag TINYINT NULL DEFAULT 0 COMMENT '家长已读：0未读 1已读',
  KEY idx_homework (homework_id),
  KEY idx_student (student_id)
) COMMENT '作业提交（学员×作业）';
