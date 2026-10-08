-- =============================================================================
-- V25  消息域：站内信表
-- -----------------------------------------------------------------------------
-- 对应 架构缺口补齐说明「填充业务模块」之 message（消息 / 通知）。
-- =============================================================================

CREATE TABLE IF NOT EXISTS `moyue_message` (
    `id`         bigint        NOT NULL COMMENT '主键（雪花 ID）',
    `from_user`  bigint        DEFAULT NULL COMMENT '发送人 ID（系统消息为 0）',
    `to_user`    bigint        DEFAULT NULL COMMENT '接收人 ID',
    `title`      varchar(256)  NOT NULL DEFAULT '' COMMENT '标题',
    `content`    varchar(1024) DEFAULT NULL COMMENT '正文',
    `type`       tinyint       NOT NULL DEFAULT 1 COMMENT '类型：1 系统 / 2 活动 / 3 私信',
    `read_flag`  tinyint       NOT NULL DEFAULT 0 COMMENT '已读标记：0 未读 / 1 已读',
    `create_by`  varchar(64)   DEFAULT NULL COMMENT '创建人',
    `create_time` datetime      DEFAULT NULL COMMENT '创建时间',
    `update_by`  varchar(64)   DEFAULT NULL COMMENT '更新人',
    `update_time` datetime      DEFAULT NULL COMMENT '更新时间',
    `remark`     varchar(512)  DEFAULT NULL COMMENT '备注',
    `is_deleted` tinyint(1)    NOT NULL DEFAULT 0 COMMENT '逻辑删除：0 未删 / 1 已删',
    PRIMARY KEY (`id`),
    KEY `idx_msg_to_user` (`to_user`, `read_flag`),
    KEY `idx_msg_type` (`type`, `is_deleted`)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COLLATE = utf8mb4_general_ci COMMENT = '站内信表';
