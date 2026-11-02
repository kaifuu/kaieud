-- =====================================================================
-- v2.16 真·多租户：企业域登录 + 每租户独立 admin + 平台运营与租户彻底分离
-- =====================================================================

-- 1) 菜单范围：0=租户模板（租户可见、创建租户时复制） 1=平台专属（仅平台运营可见）
ALTER TABLE sys_menu ADD COLUMN scope TINYINT NOT NULL DEFAULT 0 COMMENT '菜单范围:0=租户模板 1=平台专属' AFTER visible;
UPDATE sys_menu SET scope = 1 WHERE tenant_id = 0 AND perm = 'sys:tenant';

-- 2) 平台运营方的首页（平台专属，顶级菜单；租户模板里的首页概览保持 scope=0 供复制）
INSERT INTO sys_menu (tenant_id, scope, parent_id, name, path, component, icon, type, sort, visible)
VALUES (0, 1, 0, '首页概览', 'home', 'home/Index', 'DataLine', 'C', 0, 1);

-- 3) 每个企业租户一个独立 admin 账号（用户名租户内唯一，初始密码 123456）
INSERT INTO sys_user (tenant_id, username, password, real_name, user_type, status)
SELECT t.id, 'admin', '60783a8aa981454b3ade403d6577befd', CONCAT(t.name, '管理员'), 1, 1
FROM sys_tenant t
WHERE t.deleted = 0 AND t.id > 0
  AND NOT EXISTS (SELECT 1 FROM sys_user u WHERE u.tenant_id = t.id AND u.username = 'admin');
UPDATE sys_tenant SET admin_username = 'admin' WHERE deleted = 0 AND id > 0;
