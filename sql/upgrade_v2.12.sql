-- v2.12 通知定向接收与消息历史
-- 1) 通知支持指定接收用户（用户级定向，如"需A教务审批"只发A教务），
--    与角色接收对象 target 并存：可见 = FIND_IN_SET(角色, target) 或 FIND_IN_SET(用户ID, target_users)
ALTER TABLE sys_notice
  ADD COLUMN target_users VARCHAR(512) NULL COMMENT '指定接收用户ID(逗号分隔)' AFTER target,
  ADD COLUMN create_user_id BIGINT NULL COMMENT '创建人用户ID(本人草稿/发布始终可见)' AFTER target_users,
  ADD COLUMN create_by VARCHAR(64) NULL COMMENT '创建人姓名(消息历史展示)' AFTER create_user_id;
