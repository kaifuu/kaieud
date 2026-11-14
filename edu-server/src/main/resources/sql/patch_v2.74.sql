-- v2.74 班级管理-成绩TAB：考试成绩表（录入/编辑/删除，按考试名称与学员检索）
CREATE TABLE edu_exam_score (
  id BIGINT AUTO_INCREMENT PRIMARY KEY,
  tenant_id BIGINT NULL COMMENT '租户ID',
  clazz_id BIGINT NULL COMMENT '录入时所在班级',
  student_id BIGINT NOT NULL COMMENT '学员',
  exam_name VARCHAR(100) NOT NULL COMMENT '考试名称',
  item VARCHAR(50) NULL COMMENT '项目（如数学/英语/阶段测试）',
  score DECIMAL(6,1) NULL COMMENT '成绩',
  source VARCHAR(20) NULL DEFAULT '录入' COMMENT '来源：录入/导入',
  exam_date DATE NULL COMMENT '考试日期',
  remark VARCHAR(200) NULL COMMENT '备注',
  operator VARCHAR(50) NULL COMMENT '录入人',
  create_time DATETIME NULL DEFAULT CURRENT_TIMESTAMP,
  update_time DATETIME NULL,
  KEY idx_clazz (clazz_id),
  KEY idx_student (student_id)
) COMMENT '考试成绩';
