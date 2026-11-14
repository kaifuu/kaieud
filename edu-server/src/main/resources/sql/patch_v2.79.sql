-- v2.79 办理中心列表升级：到款确认（pay_status）+ 订单作废（status=5，原因记入备注）
ALTER TABLE edu_enroll ADD COLUMN pay_status TINYINT NULL DEFAULT 0 COMMENT '到款状态：0未确认 1已确认到款' AFTER paid_amount;
