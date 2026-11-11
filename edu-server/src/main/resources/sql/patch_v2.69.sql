-- v2.69 班级试听学员
CREATE TABLE IF NOT EXISTS `edu_audition_student` (
  `id` bigint NOT NULL AUTO_INCREMENT,
  `tenant_id` bigint NOT NULL DEFAULT '0',
  `clazz_id` bigint NOT NULL COMMENT '所属班级',
  `student_id` bigint NOT NULL COMMENT '学员ID',
  `student_name` varchar(64) DEFAULT NULL COMMENT '学员姓名快照',
  `source` tinyint DEFAULT '1' COMMENT '1试听活动 2日程试听',
  `audition_id` bigint DEFAULT NULL COMMENT '关联试听活动ID（日程试听为空）',
  `audition_name` varchar(128) DEFAULT NULL COMMENT '试听名称快照（活动名或班级名）',
  `course_name` varchar(128) DEFAULT NULL,
  `teacher_name` varchar(64) DEFAULT NULL,
  `classroom_name` varchar(64) DEFAULT NULL,
  `time_period` varchar(64) DEFAULT NULL,
  `campus` varchar(64) DEFAULT NULL,
  `operator` varchar(64) DEFAULT NULL,
  `create_time` datetime DEFAULT CURRENT_TIMESTAMP,
  PRIMARY KEY (`id`),
  KEY `idx_clazz` (`clazz_id`),
  KEY `idx_student` (`student_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_general_ci COMMENT='班级试听学员';
