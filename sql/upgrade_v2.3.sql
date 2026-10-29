-- V2.3：教室管理
USE edu_course;

CREATE TABLE IF NOT EXISTS edu_classroom (
  id BIGINT NOT NULL AUTO_INCREMENT,
  tenant_id BIGINT NOT NULL DEFAULT 1,
  org_id BIGINT COMMENT '所属校区',
  name VARCHAR(64) NOT NULL COMMENT '教室名称',
  capacity INT DEFAULT 0 COMMENT '可容纳人数',
  deleted TINYINT DEFAULT 0,
  create_time DATETIME DEFAULT CURRENT_TIMESTAMP,
  PRIMARY KEY (id), KEY idx_tenant (tenant_id)
) COMMENT '教室表';

ALTER TABLE edu_schedule ADD COLUMN classroom_id BIGINT COMMENT '教室ID';
ALTER TABLE edu_clazz   ADD COLUMN classroom_id BIGINT COMMENT '固定教室ID';

INSERT IGNORE INTO sys_menu (id,parent_id,name,path,component,icon,perm,type,sort) VALUES
(232,2,'教室管理','classroom','edu/Classroom','OfficeBuilding','edu:classroom','C',3);
INSERT IGNORE INTO sys_role_menu (role_id,menu_id) VALUES (1,232),(2,232),(3,232);

SELECT 'V2.3 ok' AS result;
