-- ============================================================
-- V2.8 日历删除课次：
-- 日/周/月视图已排课区域支持删除（软删除，业务查询自动过滤），
-- 删除时在排课历史留痕；日志表加快照字段，删除后历史仍可完整展示
-- ============================================================

ALTER TABLE edu_schedule
    ADD COLUMN deleted TINYINT DEFAULT 0
        COMMENT '逻辑删除：0正常 1已删除（日历删除课次）';

ALTER TABLE edu_schedule_log
    ADD COLUMN course_name VARCHAR(100) DEFAULT NULL COMMENT '课程快照（课次删除后历史展示）',
    ADD COLUMN teacher_name VARCHAR(50)  DEFAULT NULL COMMENT '教师快照',
    ADD COLUMN classroom   VARCHAR(50)  DEFAULT NULL COMMENT '教室快照',
    ADD COLUMN class_time  VARCHAR(60)  DEFAULT NULL COMMENT '上课时间快照';
