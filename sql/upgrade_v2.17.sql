-- =====================================================================
-- v2.17 平台运营域成型
-- 1) 「租户管理」菜单更名「平台运营」
-- 2) 基础设置(品牌/登录页/首页广告)、系统字典 归平台专属（企业不可见不可改）
--    —— 修复此前遗留：企业 admin 改 LOGO/广告/字典会影响全平台
-- 3) 从既有企业菜单副本中移除这两项，并清理角色引用
-- =====================================================================

-- 1) 更名
UPDATE sys_menu SET name = '平台运营' WHERE tenant_id = 0 AND perm = 'sys:tenant';

-- 2) 平台专属化（scope=1：平台运营可见；不进入企业模板复制源）
UPDATE sys_menu SET scope = 1
WHERE tenant_id = 0 AND perm IN ('sys:config', 'sys:dict');

-- 3) 企业副本移除 基础设置/系统字典（先清角色引用再删菜单）
DELETE rm FROM sys_role_menu rm
JOIN sys_menu m ON m.id = rm.menu_id
WHERE m.tenant_id > 0 AND m.perm IN ('sys:config', 'sys:dict');
DELETE FROM sys_menu WHERE tenant_id > 0 AND perm IN ('sys:config', 'sys:dict');
