-- =============================================================================
-- V43  互动域：评论 / IM 会话运营端字段
-- -----------------------------------------------------------------------------
-- 对应「7 个业务管理模块 · M7 social」：复用 V31 comment / V32-V34 IM，不新增表，
-- 仅补充运营端所需的治理字段：
--   moyue_comment.status   审核状态（0 正常 / 1 待审核 / 2 已下架）
--   moyue_comment.top      运营置顶（0 否 / 1 是）
--   moyue_im_conversation.disabled  会话禁用（0 正常 / 1 禁用）
-- =============================================================================

ALTER TABLE `moyue_comment`
    ADD COLUMN `status` tinyint NOT NULL DEFAULT 0 COMMENT '审核状态：0 正常 / 1 待审核 / 2 已下架' AFTER `like_count`,
    ADD COLUMN `top`    tinyint NOT NULL DEFAULT 0 COMMENT '运营置顶：0 否 / 1 是' AFTER `status`;

ALTER TABLE `moyue_im_conversation`
    ADD COLUMN `disabled` tinyint NOT NULL DEFAULT 0 COMMENT '会话禁用：0 正常 / 1 禁用' AFTER `last_message_time`;
