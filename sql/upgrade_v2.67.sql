-- v2.67 授课模式字典收敛为 班课/一对一：
--   大班课 → 班课（沿用值9，历史数据兼容）；删除一对二/三/四/五细分项
--   课程管理表单改为 radio 单选，其余引用（合同班型/选课筛选等）随字典自动同步
UPDATE sys_dict SET label = '班课' WHERE dict_type = 'class_type' AND label = '大班课';
DELETE FROM sys_dict WHERE dict_type = 'class_type' AND label IN ('一对二', '一对三', '一对四', '一对五');
