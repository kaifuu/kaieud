-- =====================================================================
-- v2.21 系统字典企业自维护
-- 1) sys_dict / sys_dict_type 租户化：每企业一套，独立维护（tenant 0 = 平台模板，仅作新企业初始化源）
-- 2) 系统字典菜单归还企业（此前收归平台）；平台运营页撤掉字典TAB
-- 3) 既有企业初始化字典副本
-- =====================================================================

-- 1) 字典表租户化
ALTER TABLE sys_dict ADD COLUMN tenant_id BIGINT NOT NULL DEFAULT 0 COMMENT '租户ID(0=平台模板)' AFTER id;
ALTER TABLE sys_dict ADD INDEX idx_dict_tenant (tenant_id);
ALTER TABLE sys_dict_type ADD COLUMN tenant_id BIGINT NOT NULL DEFAULT 0 COMMENT '租户ID(0=平台模板)' AFTER id;

-- 2) 模板菜单：系统字典归企业模板（scope=0，挂平台模板的系统管理下，供新企业复制）
INSERT INTO sys_menu (tenant_id, scope, parent_id, name, path, component, icon, perm, type, sort, visible)
SELECT 0, 0, m.id, '系统字典', 'dict', 'system/Dict', 'Collection', 'sys:dict', 'C', 9, 1
FROM sys_menu m WHERE m.tenant_id = 0 AND m.name = '系统管理' AND m.scope = 0
  AND NOT EXISTS (SELECT 1 FROM (SELECT id FROM sys_menu WHERE tenant_id = 0 AND perm = 'sys:dict') x);

-- 3) 既有企业：补系统字典菜单 + 复制字典/类型副本
INSERT INTO sys_menu (tenant_id, scope, parent_id, name, path, component, icon, perm, type, sort, visible)
SELECT t.id, 0, m.id, '系统字典', 'dict', 'system/Dict', 'Collection', 'sys:dict', 'C', 9, 1
FROM sys_tenant t JOIN sys_menu m ON m.tenant_id = t.id AND m.name = '系统管理'
WHERE t.deleted = 0 AND t.id > 0
  AND NOT EXISTS (SELECT 1 FROM (SELECT id FROM sys_menu WHERE tenant_id = t.id AND perm = 'sys:dict') x);

INSERT INTO sys_dict (tenant_id, dict_type, label, value, sort)
SELECT t.id, d.dict_type, d.label, d.value, d.sort
FROM sys_tenant t JOIN sys_dict d ON d.tenant_id = 0
WHERE t.deleted = 0 AND t.id > 0;

INSERT INTO sys_dict_type (tenant_id, dict_type, name)
SELECT t.id, dt.dict_type, dt.name
FROM sys_tenant t JOIN sys_dict_type dt ON dt.tenant_id = 0
WHERE t.deleted = 0 AND t.id > 0;
