-- 清空合同管理相关数据（重新测试用）
-- 合同 + 合同历史 + 关联记录（缴费/缴费日志/消课/退费/合同关联的账户流水）
TRUNCATE TABLE edu_contract;
TRUNCATE TABLE edu_contract_log;
TRUNCATE TABLE edu_payment;
TRUNCATE TABLE edu_payment_log;
TRUNCATE TABLE edu_consume;
TRUNCATE TABLE edu_refund;
-- 账户流水仅清合同关联部分（余额充值/退费入账均挂合同），保留其他流水口径不破坏
DELETE FROM edu_account_log WHERE contract_id IS NOT NULL;
