-- v2.78 作业详情改富文本（wangEditor，HTML 存储）：content 列放宽为 TEXT
ALTER TABLE edu_homework MODIFY COLUMN content TEXT NULL COMMENT '作业内容/要求（富文本HTML）';
