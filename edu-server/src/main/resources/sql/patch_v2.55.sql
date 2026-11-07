-- =====================================================================
-- 智教云课 KaiEdu — 增量升级补丁 v2.41 → v2.55
-- 适用基线：v2.41 部署包（运行时导出 init.sql 初始化）的远程库
-- 可重复执行：重复列/重复表自动跳过；字典数据带存在性守卫
-- 执行方式：容器管理 → 数据库配置TAB → 补丁升级
-- =====================================================================

-- ==================== 课程管理升级 ====================
ALTER TABLE edu_course ADD COLUMN grade VARCHAR(32) NULL COMMENT '适用年级（字典grade）' AFTER subject;
ALTER TABLE edu_course ADD COLUMN teacher_id BIGINT NULL COMMENT '默认授课老师' AFTER grade;
ALTER TABLE edu_course ADD COLUMN capacity INT NULL COMMENT '满班人数' AFTER duration_minutes;
ALTER TABLE edu_course ADD COLUMN start_date DATE NULL COMMENT '开课日期' AFTER capacity;
ALTER TABLE edu_course ADD COLUMN end_date DATE NULL COMMENT '结课日期' AFTER start_date;
ALTER TABLE edu_course ADD COLUMN teach_mode INT NULL COMMENT '授课模式（班型字典class_type）' AFTER class_type;
ALTER TABLE edu_course ADD COLUMN campus VARCHAR(255) NULL COMMENT '开课校区（指定模式为逗号分隔，空=全部）' AFTER teach_mode;
ALTER TABLE edu_course ADD COLUMN campus_mode TINYINT NULL COMMENT '校区模式 1全部 2指定' AFTER campus;
ALTER TABLE edu_course ADD COLUMN charge_hour TINYINT NULL DEFAULT 0 COMMENT '按课时收费 0/1' AFTER campus_mode;
ALTER TABLE edu_course ADD COLUMN charge_time TINYINT NULL DEFAULT 0 COMMENT '按时间收费 0/1' AFTER charge_hour;
ALTER TABLE edu_course ADD COLUMN update_time DATETIME NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间' AFTER create_time;

-- 定价标准表（收费模式：按课时/按时间段，按校区分行）
CREATE TABLE IF NOT EXISTS edu_course_price (
  id BIGINT AUTO_INCREMENT PRIMARY KEY,
  tenant_id BIGINT NOT NULL DEFAULT 0,
  course_id BIGINT NOT NULL COMMENT '课程ID',
  charge_mode TINYINT NOT NULL COMMENT '1按课时 2按时间',
  campus VARCHAR(32) NOT NULL COMMENT '校区（全部校区时为：全部校区）',
  hours DECIMAL(10,2) NULL COMMENT '数量-课时（模式1，支持两位小数）',
  time_period VARCHAR(16) NULL COMMENT '时间段：天/周/月/季/年（模式2）',
  total_amount DECIMAL(10,2) NOT NULL COMMENT '总价(元)',
  sort INT DEFAULT 0,
  create_time DATETIME DEFAULT CURRENT_TIMESTAMP,
  KEY idx_course (course_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='课程定价标准';

-- ==================== 课时套餐改造（按科目/授课模式/年级售卖） ====================
ALTER TABLE edu_package ADD COLUMN category INT NULL COMMENT '科目（字典course_category）' AFTER name;
ALTER TABLE edu_package ADD COLUMN teach_mode INT NULL COMMENT '授课模式（字典class_type）' AFTER category;
ALTER TABLE edu_package ADD COLUMN grade VARCHAR(32) NULL COMMENT '适用年级' AFTER teach_mode;
ALTER TABLE edu_package MODIFY COLUMN course_id BIGINT NULL COMMENT '关联课程（历史字段，新版套餐不关联课程）';
UPDATE edu_package p JOIN edu_course c ON c.id = p.course_id
SET p.category = c.category, p.teach_mode = COALESCE(c.teach_mode, c.class_type), p.grade = c.grade
WHERE p.deleted = 0 AND p.category IS NULL;

-- ==================== 报名单（支付/优惠/经办信息） ====================
ALTER TABLE edu_enroll ADD COLUMN valid_date DATE NULL COMMENT '有效期（报名时勾选指定）' AFTER create_time;
ALTER TABLE edu_enroll ADD COLUMN remark VARCHAR(255) NULL COMMENT '备注（对内）' AFTER valid_date;
ALTER TABLE edu_enroll ADD COLUMN discount_amount DECIMAL(10,2) NULL COMMENT '优惠金额' AFTER total_amount;
ALTER TABLE edu_enroll ADD COLUMN discount_detail TEXT NULL COMMENT '优惠明细JSON [{type,amount}]（编辑回显不合并）' AFTER discount_amount;
ALTER TABLE edu_enroll ADD COLUMN source VARCHAR(32) NULL COMMENT '销售来源' AFTER remark;
ALTER TABLE edu_enroll ADD COLUMN handler VARCHAR(64) NULL COMMENT '经办人' AFTER source;
ALTER TABLE edu_enroll ADD COLUMN handle_date DATE NULL COMMENT '经办日期' AFTER handler;
ALTER TABLE edu_enroll ADD COLUMN order_taker VARCHAR(64) NULL COMMENT '采单员' AFTER handle_date;
ALTER TABLE edu_enroll ADD COLUMN consultant VARCHAR(64) NULL COMMENT '咨询顾问' AFTER order_taker;
ALTER TABLE edu_enroll ADD COLUMN pay_detail TEXT NULL COMMENT '支付明细JSON [{channel,account,serial,amount}]' AFTER paid_amount;

-- 报名/续报操作日志表
CREATE TABLE IF NOT EXISTS edu_enroll_log (
  id BIGINT NOT NULL AUTO_INCREMENT,
  tenant_id BIGINT NOT NULL DEFAULT 0,
  enroll_id BIGINT NOT NULL COMMENT '报名单ID',
  action VARCHAR(16) NOT NULL COMMENT 'CREATE/UPDATE',
  content VARCHAR(255) DEFAULT NULL COMMENT '操作内容',
  total_amount DECIMAL(10,2) DEFAULT NULL COMMENT '应收',
  paid_amount DECIMAL(10,2) DEFAULT NULL COMMENT '实收',
  pay_info VARCHAR(255) DEFAULT NULL COMMENT '支付信息',
  balance_change DECIMAL(10,2) DEFAULT 0.00 COMMENT '余额变动',
  operator VARCHAR(64) DEFAULT NULL COMMENT '修改人',
  create_time DATETIME DEFAULT CURRENT_TIMESTAMP COMMENT '修改时间',
  PRIMARY KEY (id),
  KEY idx_enroll (enroll_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='报名/续报操作日志';

-- ==================== 字典数据 ====================
-- 年级字典值改中文
UPDATE sys_dict SET value = label WHERE dict_type = 'grade';

-- 校区字典重建（全部校区/朝阳/东城/海淀/西城）
DELETE FROM sys_dict WHERE dict_type = 'campus';
INSERT INTO sys_dict (tenant_id, dict_type, label, value, sort)
SELECT t.id, 'campus', x.label, x.label, x.sort
FROM sys_tenant t
JOIN (SELECT '全部校区' AS label, 1 AS sort UNION SELECT '朝阳校区', 2 UNION SELECT '东城校区', 3 UNION SELECT '海淀校区', 4 UNION SELECT '西城校区', 5) x
WHERE NOT EXISTS (SELECT 1 FROM sys_dict d WHERE d.tenant_id = t.id AND d.dict_type = 'campus' AND d.label = x.label);

-- 授课模式字典（一对一/小班课/大班课/线上课）
INSERT INTO sys_dict (tenant_id, dict_type, label, value, sort)
SELECT t.id, 'teach_mode', x.label, x.val, x.sort
FROM sys_tenant t
JOIN (SELECT '一对一' AS label, '1' AS val, 1 AS sort UNION SELECT '小班课', '2', 2 UNION SELECT '大班课', '3', 3 UNION SELECT '线上课', '4', 4) x
WHERE NOT EXISTS (SELECT 1 FROM sys_dict d WHERE d.tenant_id = t.id AND d.dict_type = 'teach_mode' AND d.label = x.label);

-- 时间段字典（天/周/月/季/年）
INSERT INTO sys_dict (tenant_id, dict_type, label, value, sort)
SELECT t.id, 'time_period', x.label, x.label, x.sort
FROM sys_tenant t
JOIN (SELECT '天' AS label, 1 AS sort UNION SELECT '周', 2 UNION SELECT '月', 3 UNION SELECT '季', 4 UNION SELECT '年', 5) x
WHERE NOT EXISTS (SELECT 1 FROM sys_dict d WHERE d.tenant_id = t.id AND d.dict_type = 'time_period' AND d.label = x.label);

-- 字典类型注册（uk_tenant_dict_type 唯一键，INSERT IGNORE 幂等）
INSERT IGNORE INTO sys_dict_type (tenant_id, dict_type, name) SELECT id, 'grade', '年级' FROM sys_tenant;
INSERT IGNORE INTO sys_dict_type (tenant_id, dict_type, name) SELECT id, 'teach_mode', '授课模式' FROM sys_tenant;
INSERT IGNORE INTO sys_dict_type (tenant_id, dict_type, name) SELECT id, 'time_period', '时间段' FROM sys_tenant;
INSERT IGNORE INTO sys_dict_type (tenant_id, dict_type, name) SELECT id, 'campus', '所属校区' FROM sys_tenant;

-- 模板班型字典去重（同值保留一条）
DELETE t1 FROM sys_dict t1 JOIN sys_dict t2
ON t1.dict_type = t2.dict_type AND t1.tenant_id = t2.tenant_id AND t1.value = t2.value AND t1.id > t2.id
WHERE t1.dict_type = 'class_type';

-- ==================== 课程-校区多对多中间表 ====================
-- 课程与校区改为多对多：edu_course_campus 为唯一数据源，
-- "全部校区"保存时按当期校区字典物化为每校区一行（后续新增校区不自动扩散）；
-- 历史 edu_course.campus/campus_mode 保留做兼容，不再作为查询依据。
CREATE TABLE IF NOT EXISTS edu_course_campus (
  id BIGINT AUTO_INCREMENT PRIMARY KEY,
  tenant_id BIGINT NOT NULL DEFAULT 0,
  course_id BIGINT NOT NULL COMMENT '课程ID',
  campus VARCHAR(32) NOT NULL COMMENT '校区名（sys_dict campus，不含全部校区）',
  KEY idx_course (course_id),
  UNIQUE KEY uk_course_campus (course_id, campus)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='课程-校区关联（多对多中间表）';

-- 回填①：指定校区模式 — 逗号串拆为多行
INSERT IGNORE INTO edu_course_campus (tenant_id, course_id, campus)
SELECT c.tenant_id, c.id, TRIM(jt.campus)
FROM edu_course c
JOIN JSON_TABLE(CONCAT('["', REPLACE(c.campus, ',', '","'), '"]'), '$[*]' COLUMNS (campus VARCHAR(32) PATH '$')) jt
WHERE c.deleted = 0 AND c.campus_mode = 2 AND c.campus IS NOT NULL AND TRIM(c.campus) <> '';

-- 回填②：全部校区/未设置 — 按校区字典物化为每校区一行
INSERT IGNORE INTO edu_course_campus (tenant_id, course_id, campus)
SELECT c.tenant_id, c.id, d.label
FROM edu_course c
JOIN sys_dict d ON d.tenant_id = c.tenant_id AND d.dict_type = 'campus' AND d.label <> '全部校区'
WHERE c.deleted = 0 AND (c.campus_mode IS NULL OR c.campus_mode = 1 OR c.campus IS NULL OR TRIM(c.campus) = '');

-- ==================== 报名单校区（一单一校区） ====================
-- 报名/续报按校区分条记录：每条报名单归属一个校区（edu_course_campus 校区名）。
ALTER TABLE edu_enroll ADD COLUMN campus VARCHAR(32) NULL COMMENT '报名校区（edu_course_campus 校区名，一单一校区）' AFTER course_id;
-- 存量回填：取课程首个关联校区（套餐订阅无课程，保持空）
UPDATE edu_enroll e JOIN edu_course_campus cc ON cc.course_id = e.course_id
SET e.campus = cc.campus
WHERE e.campus IS NULL
  AND cc.id = (SELECT MIN(c2.id) FROM edu_course_campus c2 WHERE c2.course_id = e.course_id);

-- ==================== 账户流水：余额充值留档字段 ====================
-- 办理中心-余额充值：多账户支付明细与经办信息随单留档（旧数据为空，充值管理列表不受影响）。
ALTER TABLE edu_account_log
  ADD COLUMN campus VARCHAR(32) DEFAULT NULL COMMENT '经办校区' AFTER hours,
  ADD COLUMN pay_detail VARCHAR(1000) DEFAULT NULL COMMENT '支付明细JSON [{channel,account,serial,amount}]' AFTER campus,
  ADD COLUMN source VARCHAR(64) DEFAULT NULL COMMENT '销售来源' AFTER pay_detail,
  ADD COLUMN handle_date DATE DEFAULT NULL COMMENT '经办日期' AFTER source,
  ADD COLUMN order_taker VARCHAR(64) DEFAULT NULL COMMENT '采单员' AFTER handle_date,
  ADD COLUMN consultant VARCHAR(64) DEFAULT NULL COMMENT '咨询顾问' AFTER order_taker;

-- ==================== 菜单调整：报名/续报 提升为顶级「办理中心」 ====================
-- 报名/续报 → 顶级菜单「办理中心」（直达办理中心页，原入口瓷贴页）
UPDATE sys_menu SET parent_id = 0, name = '办理中心', path = '/reenroll', sort = 3
WHERE component = 'edu/Reenroll' AND type = 'C';
-- 原顶级目录「办理中心」(/finance，剩 合同管理/缴费管理/充值管理) 改名「收费管理」
UPDATE sys_menu SET name = '收费管理', sort = 4
WHERE type = 'M' AND path = '/finance';
-- 学生中心/教师中心 顺位后移
UPDATE sys_menu SET sort = 5 WHERE type = 'M' AND path = '/portal';
UPDATE sys_menu SET sort = 6 WHERE type = 'M' AND path = '/teach';

-- ==================== 系统字典：去重 + 全租户补齐 ====================
-- 新开企业自动携带系统字典（TenantProvisionService 种子）；基础包导出租户0字典。
-- ① 同租户同类型同值去重（保留最早一条，清理历史脚本造成的重复/同值别名脏行）
DELETE d FROM sys_dict d
JOIN sys_dict d2 ON d2.tenant_id = d.tenant_id AND d2.dict_type = d.dict_type
  AND d2.value = d.value AND d2.id < d.id;
-- ② 各租户缺失字典按「类型+值」从平台模板(租户0)补齐（不动租户已维护数据）
INSERT INTO sys_dict (tenant_id, dict_type, label, value, sort)
SELECT t.id, d.dict_type, d.label, d.value, d.sort
FROM sys_tenant t
JOIN sys_dict d ON d.tenant_id = 0
WHERE t.id <> 0
  AND NOT EXISTS (SELECT 1 FROM sys_dict x
                  WHERE x.tenant_id = t.id AND x.dict_type = d.dict_type AND x.value = d.value);
-- ③ 字典类型补齐（缺类型的租户补上类型中文名）
INSERT INTO sys_dict_type (tenant_id, dict_type, name)
SELECT t.id, d.dict_type, d.name
FROM sys_tenant t
JOIN sys_dict_type d ON d.tenant_id = 0
WHERE t.id <> 0
  AND NOT EXISTS (SELECT 1 FROM sys_dict_type x
                  WHERE x.tenant_id = t.id AND x.dict_type = d.dict_type);
