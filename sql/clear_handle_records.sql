-- 清空办理中心列表相关业务数据（本地测试用）
-- 数据源 6 表：报名单 / 账户流水(充值·转课·退费) / 缴费(补费) / 积分流水 / 教材杂费 / 补费流水
-- 直属留痕 3 表：报名单操作日志 / 缴费操作日志 / 退费单
-- 注：账户余额与积分余额均由流水推算，清空后自动归零；TRUNCATE 同时重置自增单号
TRUNCATE TABLE edu_enroll;
TRUNCATE TABLE edu_enroll_log;
TRUNCATE TABLE edu_account_log;
TRUNCATE TABLE edu_payment;
TRUNCATE TABLE edu_payment_log;
TRUNCATE TABLE edu_score_log;
TRUNCATE TABLE edu_material_log;
TRUNCATE TABLE edu_supplement_log;
TRUNCATE TABLE edu_refund;
