-- v2.77 布置作业升级：发送学员定向、图片/视频/录音附件、可见性与家长提交开关、草稿
ALTER TABLE edu_homework ADD COLUMN target_students TEXT NULL COMMENT '发送学员ID集（逗号分隔，空=全班在读）' AFTER teacher_id;
ALTER TABLE edu_homework ADD COLUMN images TEXT NULL COMMENT '图片附件JSON[{url,name}]' AFTER content;
ALTER TABLE edu_homework ADD COLUMN videos TEXT NULL COMMENT '视频附件JSON[{url,name}]' AFTER images;
ALTER TABLE edu_homework ADD COLUMN audios TEXT NULL COMMENT '录音附件JSON[{url,name}]' AFTER videos;
ALTER TABLE edu_homework ADD COLUMN visible_all TINYINT NULL DEFAULT 1 COMMENT '学员提交后对本班可见：0否 1是' AFTER status;
ALTER TABLE edu_homework ADD COLUMN parent_submit TINYINT NULL DEFAULT 1 COMMENT '家长端可提交：0否 1是' AFTER visible_all;
-- 通用上传通道放宽（配套 application.yml multipart 500MB）：mp4/mp3 附件
