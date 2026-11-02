-- =====================================================================
-- v2.22 学生账户体系：充值管理（教务）+ 退费入账 + 学生中心账户管理
-- 余额 = 累计充值 + 累计退费入账（按学生维度汇总流水）
-- =====================================================================

-- 1) 账户流水表
CREATE TABLE edu_account_log (
  id          BIGINT        NOT NULL AUTO_INCREMENT PRIMARY KEY COMMENT '主键',
  tenant_id   BIGINT        NOT NULL COMMENT '租户ID',
  student_id  BIGINT        NOT NULL COMMENT '学生ID',
  contract_id BIGINT        NULL COMMENT '关联合同ID',
  type        TINYINT       NOT NULL COMMENT '类型：1-充值 2-退费入账',
  amount      DECIMAL(10,2) NOT NULL COMMENT '金额（元）',
  hours       DECIMAL(10,1) NULL COMMENT '退费课时（退费入账时记录）',
  remark      VARCHAR(255)  NULL COMMENT '原因备注（如补差价）',
  operator    VARCHAR(64)   NULL COMMENT '操作人',
  create_time DATETIME      NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '操作时间',
  KEY idx_account_student (student_id),
  KEY idx_account_tenant (tenant_id)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COMMENT = '学生账户流水（充值/退费入账）';

-- 2) 教务管理 → 充值管理（模板 + 各企业 + 教管角色授权）
INSERT INTO sys_menu (tenant_id, scope, parent_id, name, path, component, icon, perm, type, sort, visible)
SELECT 0, 0, m.id, '充值管理', 'account', 'edu/Account', 'Wallet', 'edu:account', 'C', 12, 1
FROM sys_menu m WHERE m.tenant_id = 0 AND m.name = '教务管理' AND m.scope = 0;

INSERT INTO sys_menu (tenant_id, scope, parent_id, name, path, component, icon, perm, type, sort, visible)
SELECT t.id, 0, m.id, '充值管理', 'account', 'edu/Account', 'Wallet', 'edu:account', 'C', 12, 1
FROM sys_tenant t JOIN sys_menu m ON m.tenant_id = t.id AND m.name = '教务管理'
WHERE t.deleted = 0 AND t.id > 0;

INSERT INTO sys_role_menu (role_id, menu_id)
SELECT r.id, m.id FROM sys_role r JOIN sys_menu m ON m.tenant_id = r.tenant_id AND m.perm = 'edu:account'
WHERE r.tenant_id > 0 AND r.code IN ('EDU_ADMIN', 'SUPER_ADMIN');

-- 3) 学生中心 → 账户管理（模板 + 各企业 + 学生角色授权）
INSERT INTO sys_menu (tenant_id, scope, parent_id, name, path, component, icon, perm, type, sort, visible)
SELECT 0, 0, m.id, '账户管理', 'account', 'portal/Account', 'Wallet', 'portal', 'C', 4, 1
FROM sys_menu m WHERE m.tenant_id = 0 AND m.name = '学生中心' AND m.scope = 0;

INSERT INTO sys_menu (tenant_id, scope, parent_id, name, path, component, icon, perm, type, sort, visible)
SELECT t.id, 0, m.id, '账户管理', 'account', 'portal/Account', 'Wallet', 'portal', 'C', 4, 1
FROM sys_tenant t JOIN sys_menu m ON m.tenant_id = t.id AND m.name = '学生中心'
WHERE t.deleted = 0 AND t.id > 0;

INSERT INTO sys_role_menu (role_id, menu_id)
SELECT r.id, m.id FROM sys_role r JOIN sys_menu m ON m.tenant_id = r.tenant_id AND m.perm = 'portal' AND m.name = '账户管理'
WHERE r.tenant_id > 0 AND r.code = 'STUDENT';
