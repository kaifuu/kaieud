-- v2.71 试听活动直接登记学员（无需关联班级）：clazz_id 改为可空
ALTER TABLE edu_audition_student MODIFY COLUMN clazz_id BIGINT NULL COMMENT '所属班级（活动直接登记时为空）';
