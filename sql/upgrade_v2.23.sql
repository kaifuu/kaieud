-- =====================================================================
-- v2.23 新增「财务管理」一级菜单：合同管理/缴费管理/充值管理 归入其下
-- 平台模板(0) + 各企业同步调整；菜单ID不变，角色授权不受影响
-- =====================================================================

-- 1) 各租户（含模板）创建 财务管理 目录
INSERT INTO sys_menu (tenant_id, scope, parent_id, name, path, component, icon, type, sort, visible)
SELECT t.id, 0, 0, '财务管理', '/finance', NULL, 'Money', 'M', 3, 1
FROM sys_tenant t
WHERE (t.deleted = 0 OR t.id = 0) AND t.id >= 0
  AND NOT EXISTS (SELECT 1 FROM (SELECT id FROM sys_menu WHERE tenant_id = t.id AND name = '财务管理') x);

-- 2) 合同管理/缴费管理/充值管理 挂到本租户的 财务管理 目录下（并排序）
UPDATE sys_menu m
JOIN sys_menu f ON f.tenant_id = m.tenant_id AND f.name = '财务管理'
SET m.parent_id = f.id,
    m.sort = CASE m.name WHEN '合同管理' THEN 1 WHEN '缴费管理' THEN 2 WHEN '充值管理' THEN 3 ELSE m.sort END
WHERE m.name IN ('合同管理', '缴费管理', '充值管理');
