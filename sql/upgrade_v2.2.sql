-- V2.2：多校区数据隔离 + 课消审核流 + 学管师负责关系
USE edu_course;

ALTER TABLE edu_student ADD COLUMN org_id BIGINT COMMENT '所属校区', ADD COLUMN manager_id BIGINT COMMENT '学管师用户ID';
ALTER TABLE edu_contract ADD COLUMN org_id BIGINT COMMENT '所属校区';
ALTER TABLE edu_payment ADD COLUMN org_id BIGINT COMMENT '所属校区';
ALTER TABLE edu_consume
  ADD COLUMN org_id BIGINT COMMENT '所属校区',
  ADD COLUMN audit_status TINYINT DEFAULT 1 COMMENT '0待审核 1已审核 2已驳回',
  ADD COLUMN auditor VARCHAR(32), ADD COLUMN audit_time DATETIME, ADD COLUMN audit_remark VARCHAR(255);

-- 存量数据归属总校，历史消课视为已审核
UPDATE edu_student SET org_id = 1 WHERE org_id IS NULL;
UPDATE edu_contract SET org_id = 1 WHERE org_id IS NULL;
UPDATE edu_payment SET org_id = 1 WHERE org_id IS NULL;
UPDATE edu_consume SET org_id = 1, audit_status = 1 WHERE org_id IS NULL;

-- 消课审核按钮权限（总部教务/运营负责人）
INSERT IGNORE INTO sys_menu (id,parent_id,name,path,component,icon,perm,type,sort) VALUES
(231,213,'消课审核',NULL,NULL,NULL,'edu:consume:audit','B',1);
INSERT IGNORE INTO sys_role_menu (role_id,menu_id) VALUES (1,231),(2,231);
