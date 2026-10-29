-- =====================================================
-- K12教务系统 V2 升级脚本（对标 PRD 2.0）
-- =====================================================
USE edu_course;

-- ---------- 字典表 ----------
CREATE TABLE IF NOT EXISTS sys_dict (
  id BIGINT NOT NULL AUTO_INCREMENT,
  dict_type VARCHAR(32) NOT NULL COMMENT '字典类型',
  label VARCHAR(32) NOT NULL,
  value VARCHAR(32) NOT NULL,
  sort INT DEFAULT 0,
  PRIMARY KEY (id), KEY idx_type (dict_type)
) COMMENT '系统字典';
INSERT INTO sys_dict (dict_type,label,value,sort) VALUES
('class_type','一对一','1',1),('class_type','小班课','2',2),('class_type','大班课','3',3),
('course_category','文化课','1',1),('course_category','艺术','2',2),('course_category','素养','3',3),
('pay_channel','微信','1',1),('pay_channel','支付宝','2',2),('pay_channel','现金','3',3),('pay_channel','对公转账','4',4),
('student_status','在读','1',1),('student_status','停课','2',2),('student_status','结课','3',3),('student_status','退费','4',4),
('contract_status','待缴费','0',1),('contract_status','部分缴费','1',2),('contract_status','已结清','2',3),('contract_status','已退费','3',4),('contract_status','已到期','4',5);

-- ---------- 课时套餐模板 ----------
CREATE TABLE IF NOT EXISTS edu_package (
  id BIGINT NOT NULL AUTO_INCREMENT,
  tenant_id BIGINT NOT NULL DEFAULT 1,
  name VARCHAR(64) NOT NULL COMMENT '套餐名称',
  course_id BIGINT NOT NULL COMMENT '关联课程',
  class_type TINYINT DEFAULT 1 COMMENT '班型1一对一2小班3大班',
  unit_price DECIMAL(10,2) NOT NULL COMMENT '售卖单价元/课时',
  hours DECIMAL(6,2) NOT NULL DEFAULT 0 COMMENT '购买课时数',
  gift_hours DECIMAL(6,2) DEFAULT 0 COMMENT '赠送课时数',
  valid_days INT DEFAULT 365 COMMENT '有效期天',
  enabled TINYINT DEFAULT 1,
  create_by VARCHAR(32),
  create_time DATETIME DEFAULT CURRENT_TIMESTAMP,
  deleted TINYINT DEFAULT 0,
  PRIMARY KEY (id)
) COMMENT '课时套餐模板';

-- ---------- 合同 ----------
CREATE TABLE IF NOT EXISTS edu_contract (
  id BIGINT NOT NULL AUTO_INCREMENT,
  tenant_id BIGINT NOT NULL DEFAULT 1,
  contract_no VARCHAR(32) NOT NULL COMMENT '合同编号CT+日期+序号',
  student_id BIGINT NOT NULL,
  package_id BIGINT COMMENT '引用套餐',
  course_id BIGINT NOT NULL,
  class_type TINYINT DEFAULT 1,
  purchased_hours DECIMAL(6,2) NOT NULL DEFAULT 0 COMMENT '购买课时',
  gift_hours DECIMAL(6,2) NOT NULL DEFAULT 0 COMMENT '赠送课时',
  used_purchased DECIMAL(6,2) DEFAULT 0 COMMENT '已消购买课时',
  used_gift DECIMAL(6,2) DEFAULT 0 COMMENT '已消赠送课时',
  unit_price DECIMAL(10,2) NOT NULL COMMENT '合同单价(一客一价)',
  total_amount DECIMAL(12,2) NOT NULL DEFAULT 0 COMMENT '合同总金额',
  paid_amount DECIMAL(12,2) DEFAULT 0 COMMENT '累计缴费',
  start_date DATE COMMENT '生效日期',
  end_date DATE COMMENT '到期日期',
  status TINYINT DEFAULT 0 COMMENT '0待缴费1部分缴费2已结清3已退费4已到期',
  attachment_url VARCHAR(255) COMMENT '合同附件',
  refund_hours DECIMAL(6,2) DEFAULT 0 COMMENT '退费课时',
  refund_amount DECIMAL(12,2) DEFAULT 0 COMMENT '退费金额',
  remark VARCHAR(255),
  create_by VARCHAR(32),
  create_time DATETIME DEFAULT CURRENT_TIMESTAMP,
  deleted TINYINT DEFAULT 0,
  PRIMARY KEY (id), UNIQUE KEY uk_no (contract_no), KEY idx_student (student_id)
) COMMENT '合同';

-- ---------- 消课台账 ----------
CREATE TABLE IF NOT EXISTS edu_consume (
  id BIGINT NOT NULL AUTO_INCREMENT,
  tenant_id BIGINT NOT NULL DEFAULT 1,
  consume_no VARCHAR(32) COMMENT '消课编号',
  contract_id BIGINT NOT NULL,
  schedule_id BIGINT COMMENT '关联排课',
  student_id BIGINT NOT NULL,
  course_id BIGINT NOT NULL,
  teacher_id BIGINT NOT NULL,
  class_type TINYINT DEFAULT 1,
  hours DECIMAL(6,2) NOT NULL DEFAULT 1 COMMENT '消耗课时(支持小数)',
  before_hours DECIMAL(6,2) COMMENT '消课前剩余',
  after_hours DECIMAL(6,2) COMMENT '消课后剩余',
  consume_type TINYINT DEFAULT 0 COMMENT '0正常上课 1补课',
  operator VARCHAR(32),
  canceled TINYINT DEFAULT 0 COMMENT '1已撤销',
  cancel_time DATETIME,
  create_time DATETIME DEFAULT CURRENT_TIMESTAMP,
  deleted TINYINT DEFAULT 0,
  PRIMARY KEY (id), KEY idx_student (student_id), KEY idx_teacher (teacher_id), KEY idx_ctime (create_time)
) COMMENT '消课台账';

-- ---------- 学员变更记录 ----------
CREATE TABLE IF NOT EXISTS edu_student_log (
  id BIGINT NOT NULL AUTO_INCREMENT,
  tenant_id BIGINT NOT NULL DEFAULT 1,
  student_id BIGINT NOT NULL,
  change_type VARCHAR(20) COMMENT '停课/复课/退费/编辑',
  remark VARCHAR(255),
  operator VARCHAR(32),
  create_time DATETIME DEFAULT CURRENT_TIMESTAMP,
  PRIMARY KEY (id), KEY idx_student (student_id)
) COMMENT '学员变更记录';

-- ---------- 存量表升级 ----------
ALTER TABLE edu_teacher
  ADD COLUMN rate_one2one DECIMAL(10,2) DEFAULT 0 COMMENT '一对一提成元/课时',
  ADD COLUMN rate_class DECIMAL(10,2) DEFAULT 0 COMMENT '班课提成元/课时',
  ADD COLUMN class_types VARCHAR(50) COMMENT '可带班型,逗号分隔123',
  ADD COLUMN teach_course_ids VARCHAR(255) COMMENT '可教授课程ID,逗号分隔',
  ADD COLUMN ban_times TEXT COMMENT '固定禁排时段JSON:[{week,start,end}]',
  ADD COLUMN deleted TINYINT DEFAULT 0;

ALTER TABLE edu_course
  ADD COLUMN category TINYINT DEFAULT 1 COMMENT '分类1文化课2艺术3素养',
  ADD COLUMN class_type TINYINT DEFAULT 1 COMMENT '班型',
  ADD COLUMN duration_minutes INT DEFAULT 60 COMMENT '单课时标准时长分钟',
  ADD COLUMN deleted TINYINT DEFAULT 0;

ALTER TABLE edu_student
  ADD COLUMN backup_phone VARCHAR(20) COMMENT '备用电话',
  ADD COLUMN advisor VARCHAR(32) COMMENT '归属顾问',
  ADD COLUMN tags VARCHAR(255) COMMENT '学员标签逗号分隔',
  ADD COLUMN status TINYINT DEFAULT 1 COMMENT '1在读2停课3结课4退费',
  ADD COLUMN deleted TINYINT DEFAULT 0;

ALTER TABLE edu_payment
  ADD COLUMN contract_id BIGINT COMMENT '关联合同',
  ADD COLUMN voucher_url VARCHAR(255) COMMENT '凭证附件',
  ADD COLUMN reversed TINYINT DEFAULT 0 COMMENT '1红字冲销';

-- ---------- 角色：财务人员 + 菜单授权 ----------
INSERT INTO sys_role (tenant_id,name,code,remark) VALUES (1,'财务人员','FINANCE','合同查看、缴费录入台账、成本毛利报表');

INSERT INTO sys_menu (id,parent_id,name,path,component,icon,perm,type,sort) VALUES
(211,2,'课时套餐','package','edu/Package','Present','edu:package','C',5),
(212,2,'合同管理','contract','edu/Contract','Tickets','edu:contract','C',6),
(213,2,'签到消课','consume','edu/Consume','Finished','edu:consume','C',10),
(214,1,'数据导入','dataimport','system/DataImport','Upload','sys:import','C',8),
(220,0,'成本毛利报表','/cost','cost/Index','TrendCharts','edu:cost','C',4);

-- 教务/班主任：+套餐 合同 消课（不含成本报表220）
INSERT IGNORE INTO sys_role_menu (role_id,menu_id) VALUES (2,211),(2,212),(2,213),(3,211),(3,212),(3,213);
-- 财务：首页+合同+缴费+成本报表+统计
INSERT INTO sys_role_menu (role_id,menu_id) VALUES (6,4),(6,212),(6,206),(6,220),(6,3);
-- 超管：全部新菜单
INSERT IGNORE INTO sys_role_menu (role_id,menu_id) VALUES (1,211),(1,212),(1,213),(1,214),(1,220);

-- ---------- 示例套餐 ----------
INSERT INTO edu_package (tenant_id,name,course_id,class_type,unit_price,hours,gift_hours,valid_days,enabled,create_by) VALUES
(1,'数学一对一20课时包',3,1,300.00,20,2,365,1,'admin'),
(1,'数学五年级培优48课时包',1,2,150.00,48,4,365,1,'admin');

SELECT 'V2升级完成' AS result;
