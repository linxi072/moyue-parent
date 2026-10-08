-- =============================================================================
-- V33  互动域：IM 消息表
-- -----------------------------------------------------------------------------
-- 对应 架构说明书 7.4《互动》：消息落库 + WS 广播。status 表达生命周期：
-- 1 正常 / 2 已撤回 / 0 已删除。
-- =============================================================================

CREATE TABLE IF NOT EXISTS `moyue_im_message` (
    `id`             bigint        NOT NULL COMMENT '消息主键（雪花 ID）',
    `conversation_id` bigint        NOT NULL COMMENT '会话 ID',
    `sender_id`      bigint        NOT NULL COMMENT '发送者',
    `content`        varchar(2048) NOT NULL DEFAULT '' COMMENT '消息内容',
    `type`           tinyint       NOT NULL DEFAULT 1 COMMENT '类型：1 文本 / 2 图片 / 3 系统',
    `status`         tinyint       NOT NULL DEFAULT 1 COMMENT '状态：1 正常 / 2 已撤回 / 0 已删',
    `create_by`      varchar(64)   DEFAULT NULL COMMENT '创建人',
    `create_time`    datetime      DEFAULT NULL COMMENT '创建时间',
    `update_by`      varchar(64)   DEFAULT NULL COMMENT '更新人',
    `update_time`    datetime      DEFAULT NULL COMMENT '更新时间',
    `remark`         varchar(512)  DEFAULT NULL COMMENT '备注',
    `is_deleted`     tinyint(1)    NOT NULL DEFAULT 0 COMMENT '逻辑删除：0 未删 / 1 已删',
    PRIMARY KEY (`id`),
    KEY `idx_im_msg_conv` (`conversation_id`, `id`),
    KEY `idx_im_msg_sender` (`sender_id`)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COLLATE = utf8mb4_general_ci COMMENT = 'IM 消息表';
