-- =============================================================================
-- V32  互动域：IM 会话与成员表
-- -----------------------------------------------------------------------------
-- 对应 架构说明书 7.4《互动》：会话（单聊/群聊）+ 成员（含已读位点）。
-- 已读通过 member.last_read_message_id 标记，消息列表据此计算未读数。
-- =============================================================================

CREATE TABLE IF NOT EXISTS `moyue_im_conversation` (
    `id`                bigint        NOT NULL COMMENT '会话主键（雪花 ID）',
    `type`              tinyint       NOT NULL DEFAULT 1 COMMENT '类型：1 单聊 / 2 群聊',
    `title`             varchar(128)  DEFAULT NULL COMMENT '群聊名称（单聊为空）',
    `owner_id`          bigint        DEFAULT NULL COMMENT '创建者',
    `last_message`      varchar(512)  DEFAULT NULL COMMENT '最近一条消息摘要',
    `last_message_time` datetime      DEFAULT NULL COMMENT '最近消息时间',
    `create_by`         varchar(64)   DEFAULT NULL COMMENT '创建人',
    `create_time`       datetime      DEFAULT NULL COMMENT '创建时间',
    `update_by`         varchar(64)   DEFAULT NULL COMMENT '更新人',
    `update_time`       datetime      DEFAULT NULL COMMENT '更新时间',
    `remark`            varchar(512)  DEFAULT NULL COMMENT '备注',
    `is_deleted`        tinyint(1)    NOT NULL DEFAULT 0 COMMENT '逻辑删除：0 未删 / 1 已删',
    PRIMARY KEY (`id`),
    KEY `idx_im_conv_owner` (`owner_id`)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COLLATE = utf8mb4_general_ci COMMENT = 'IM 会话表';

CREATE TABLE IF NOT EXISTS `moyue_im_member` (
    `id`                  bigint        NOT NULL COMMENT '主键（雪花 ID）',
    `conversation_id`     bigint        NOT NULL COMMENT '会话 ID',
    `user_id`             bigint        NOT NULL COMMENT '成员用户 ID',
    `role`                tinyint       NOT NULL DEFAULT 1 COMMENT '成员角色：1 普通 / 2 管理员',
    `muted`               tinyint       NOT NULL DEFAULT 0 COMMENT '是否免扰：0 否 / 1 是',
    `last_read_message_id` bigint       DEFAULT NULL COMMENT '已读位点（最新已读消息 ID）',
    `create_by`           varchar(64)   DEFAULT NULL COMMENT '创建人',
    `create_time`         datetime      DEFAULT NULL COMMENT '创建时间',
    `update_by`           varchar(64)   DEFAULT NULL COMMENT '更新人',
    `update_time`         datetime      DEFAULT NULL COMMENT '更新时间',
    `remark`              varchar(512)  DEFAULT NULL COMMENT '备注',
    `is_deleted`          tinyint(1)    NOT NULL DEFAULT 0 COMMENT '逻辑删除：0 未删 / 1 已删',
    PRIMARY KEY (`id`),
    UNIQUE KEY `uk_im_member` (`conversation_id`, `user_id`),
    KEY `idx_im_member_user` (`user_id`)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COLLATE = utf8mb4_general_ci COMMENT = 'IM 会话成员表';
