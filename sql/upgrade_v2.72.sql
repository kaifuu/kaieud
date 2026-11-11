-- v2.72 教室容纳人数拆为最低/最多（试听管理列表展示教室最低人数/最多可容纳人数）
ALTER TABLE edu_classroom ADD COLUMN min_capacity INT NULL COMMENT '最低容纳人数' AFTER capacity;
