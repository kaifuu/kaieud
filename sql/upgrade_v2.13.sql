-- v2.13 租户功能隔离：每个租户开通自己的系统功能（菜单子集）
-- 菜单为全局定义；本表记录租户开通的菜单，登录时 /auth/menus 按租户过滤；
-- 租户未配置任何记录 = 开通全部（向后兼容）。业务数据隔离沿用各表 tenant_id。
CREATE TABLE sys_tenant_menu (
  id        BIGINT NOT NULL AUTO_INCREMENT PRIMARY KEY COMMENT '主键',
  tenant_id BIGINT NOT NULL COMMENT '租户ID',
  menu_id   BIGINT NOT NULL COMMENT '菜单ID',
  UNIQUE KEY uk_tenant_menu (tenant_id, menu_id)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COMMENT = '租户功能开通（租户-菜单）';
