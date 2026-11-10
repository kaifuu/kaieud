-- v2.66 教室管理：所属校区改为系统字典校区
--   下拉来源由组织树(orgTree)改为 校区字典(dictType=campus)，存储字段由 org_id 改为 campus(校区名)
--   org_id 保留为历史字段不再写入；教室数据已清空的测试库无需迁移
ALTER TABLE edu_classroom
  ADD COLUMN campus VARCHAR(64) NULL COMMENT '所属校区（系统字典-校区名）' AFTER org_id;
