-- v2.75 班级管理-课堂点评TAB：课次×学员点评（小红花+内容），按课次聚合到课人数/点评状态/阅读率
CREATE TABLE edu_lesson_comment (
  id BIGINT AUTO_INCREMENT PRIMARY KEY,
  tenant_id BIGINT NULL COMMENT '租户ID',
  clazz_id BIGINT NULL COMMENT '班级',
  schedule_id BIGINT NOT NULL COMMENT '课次',
  student_id BIGINT NOT NULL COMMENT '学员',
  teacher_id BIGINT NULL COMMENT '点评教师',
  flowers INT NULL DEFAULT 0 COMMENT '小红花数',
  content VARCHAR(300) NULL COMMENT '点评内容',
  read_flag TINYINT NULL DEFAULT 0 COMMENT '家长已读：0未读 1已读（家长端点亮）',
  create_time DATETIME NULL DEFAULT CURRENT_TIMESTAMP,
  update_time DATETIME NULL,
  KEY idx_schedule (schedule_id),
  KEY idx_student (student_id)
) COMMENT '课堂点评（课次×学员）';
