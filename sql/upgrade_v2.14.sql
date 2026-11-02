-- v2.14 租户数据隔离（企业级多租户）：每个租户拥有自己的 组织/用户/角色/菜单
-- 平台（tenant_id 为空）保留模板与平台账号；创建租户时复制模板初始化
ALTER TABLE sys_menu ADD COLUMN tenant_id BIGINT NULL DEFAULT NULL COMMENT '租户ID(NULL=平台模板)' AFTER id;
ALTER TABLE sys_tenant ADD COLUMN admin_username VARCHAR(32) NULL COMMENT '租户管理员账号(创建租户时自动生成)' AFTER contact_phone;
-- 替换 v2.13 的"功能授权"方案（改为整租户数据隔离），表废弃
DROP TABLE IF EXISTS sys_tenant_menu;
