-- =====================================================================
-- v2.15 行级租户隔离改造（拦截器方案）
-- 语义统一：tenant_id = 0 表示平台；业务租户从 1 起
-- =====================================================================

-- 1. 平台菜单模板 NULL -> 0（拦截器按等值匹配，禁止 NULL）
UPDATE sys_menu SET tenant_id = 0 WHERE tenant_id IS NULL;
ALTER TABLE sys_menu MODIFY COLUMN tenant_id BIGINT NOT NULL DEFAULT 0 COMMENT '租户ID(0=平台模板)';

-- 2. 用户：兜底归 0 并收紧为非空；用户名改为租户内唯一（跨租户可同名）
UPDATE sys_user SET tenant_id = 0 WHERE tenant_id IS NULL;
ALTER TABLE sys_user MODIFY COLUMN tenant_id BIGINT NOT NULL DEFAULT 0 COMMENT '租户ID(0=平台)';
ALTER TABLE sys_user DROP INDEX uk_username, ADD UNIQUE KEY uk_tenant_username (tenant_id, username);

-- 3. 角色：不同租户可存在同名角色（租户内唯一）
ALTER TABLE sys_role ADD UNIQUE KEY uk_tenant_role_name (tenant_id, name);

-- 4. 操作日志：空值归 0（平台操作）；此后新日志由拦截器/切面自动带租户
UPDATE sys_log SET tenant_id = 0 WHERE tenant_id IS NULL;

-- 5. 业务存量数据归位：tenant_id=0 的业务行均为总部校区（租户1）历史数据
UPDATE edu_contract      SET tenant_id = 1 WHERE tenant_id = 0;
UPDATE edu_payment       SET tenant_id = 1 WHERE tenant_id = 0;
UPDATE edu_payment_log   SET tenant_id = 1 WHERE tenant_id = 0;
UPDATE edu_schedule      SET tenant_id = 1 WHERE tenant_id = 0;
UPDATE edu_schedule_log  SET tenant_id = 1 WHERE tenant_id = 0;
UPDATE edu_student       SET tenant_id = 1 WHERE tenant_id = 0;
UPDATE edu_student_log   SET tenant_id = 1 WHERE tenant_id = 0;
UPDATE edu_course        SET tenant_id = 1 WHERE tenant_id = 0;
UPDATE edu_teacher       SET tenant_id = 1 WHERE tenant_id = 0;
UPDATE edu_clazz         SET tenant_id = 1 WHERE tenant_id = 0;
UPDATE edu_classroom     SET tenant_id = 1 WHERE tenant_id = 0;
UPDATE edu_enroll        SET tenant_id = 1 WHERE tenant_id = 0;
UPDATE edu_consume       SET tenant_id = 1 WHERE tenant_id = 0;
UPDATE edu_absence       SET tenant_id = 1 WHERE tenant_id = 0;
UPDATE edu_consult       SET tenant_id = 1 WHERE tenant_id = 0;
UPDATE edu_package       SET tenant_id = 1 WHERE tenant_id = 0;
UPDATE edu_refund        SET tenant_id = 1 WHERE tenant_id = 0;
UPDATE edu_evaluation    SET tenant_id = 1 WHERE tenant_id = 0;
UPDATE edu_leave         SET tenant_id = 1 WHERE tenant_id = 0;
UPDATE sys_notify_config SET tenant_id = 1 WHERE tenant_id = 0;

-- 6. 租户表补充平台管理字段：过期时间/套餐/备注/软删除
ALTER TABLE sys_tenant
  ADD COLUMN expire_time DATETIME NULL COMMENT '过期时间(NULL=永久有效)' AFTER status,
  ADD COLUMN package_code VARCHAR(32) NULL COMMENT '套餐编码' AFTER expire_time,
  ADD COLUMN remark VARCHAR(255) NULL COMMENT '备注' AFTER package_code,
  ADD COLUMN deleted TINYINT NOT NULL DEFAULT 0 COMMENT '软删除:0正常 1已删除' AFTER remark;

-- 7. 平台租户固定记录（tenant_id=0 的对应行，便于联表展示与状态维护）
INSERT INTO sys_tenant (id, name, code, status, remark)
VALUES (0, '平台', 'PLATFORM', 1, '平台租户：tenant_id=0 保留值');
