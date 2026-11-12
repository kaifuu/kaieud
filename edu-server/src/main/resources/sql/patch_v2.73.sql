-- v2.73 班级排课升级：排课方式（自由/重复）+ 助教 + 上课主题；节假日走字典 holiday
ALTER TABLE edu_schedule ADD COLUMN assistant_id BIGINT NULL COMMENT '助教教师ID' AFTER teacher_id;
ALTER TABLE edu_schedule ADD COLUMN topic VARCHAR(20) NULL COMMENT '上课主题（最多20字）' AFTER classroom;
ALTER TABLE edu_schedule ADD COLUMN sched_type TINYINT NULL DEFAULT 0 COMMENT '排课方式：0自由排课 1重复排课' AFTER status;
-- 节假日字典类型（重复排课"节假日不排课"过滤用）：label=节日名 value=yyyy-MM-dd，租户在【系统管理-字典管理】维护
INSERT INTO sys_dict_type (tenant_id, dict_type, name)
SELECT t.id, 'holiday', '节假日'
FROM sys_tenant t
WHERE NOT EXISTS (SELECT 1 FROM sys_dict_type d WHERE d.tenant_id = t.id AND d.dict_type = 'holiday');
