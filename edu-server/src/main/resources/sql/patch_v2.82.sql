-- v2.82 报名管理操作升级：停课（status=6 + 停课日期）/复课、续费（课时与费用累加）、结课（status=2）、转课（跳办理中心复用）
ALTER TABLE edu_enroll ADD COLUMN suspend_date DATE NULL COMMENT '停课日期（status=6 停课时记录，复课清空）' AFTER valid_date;
