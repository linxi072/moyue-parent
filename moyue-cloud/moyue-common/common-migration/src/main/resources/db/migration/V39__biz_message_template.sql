-- =============================================================================
-- V39  消息域：消息模板表
-- -----------------------------------------------------------------------------
-- 对应「7 个业务管理模块 · M3 message」：运营群发站内信时引用模板（支持 name 占位渲染）。
-- 真实短信 / 邮件渠道为结构占位（沙箱无凭证），仅落库站内信。
-- =============================================================================

CREATE TABLE IF NOT EXISTS `moyue_message_template` (
    `id`         bigint        NOT NULL COMMENT '主键（雪花 ID）',
    `code`       varchar(64)   NOT NULL DEFAULT '' COMMENT '模板编码（唯一）',
    `title`      varchar(256)  NOT NULL DEFAULT '' COMMENT '标题',
    `content`    text          COMMENT '正文（支持 name 占位符，群发时渲染收件人）',
    `type`       tinyint       NOT NULL DEFAULT 1 COMMENT '类型：1 系统 / 2 活动 / 3 私信',
    `enabled`    tinyint       NOT NULL DEFAULT 1 COMMENT '是否启用：0 停用 / 1 启用',
    `create_by`  varchar(64)   DEFAULT NULL COMMENT '创建人',
    `create_time` datetime     DEFAULT NULL COMMENT '创建时间',
    `update_by`  varchar(64)   DEFAULT NULL COMMENT '更新人',
    `update_time` datetime     DEFAULT NULL COMMENT '更新时间',
    `remark`     varchar(512)  DEFAULT NULL COMMENT '备注',
    `is_deleted` tinyint(1)    NOT NULL DEFAULT 0 COMMENT '逻辑删除：0 未删 / 1 已删',
    PRIMARY KEY (`id`),
    UNIQUE KEY `uk_template_code` (`code`),
    KEY `idx_template_type_enabled` (`type`, `enabled`)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COLLATE = utf8mb4_general_ci COMMENT = '消息模板表';
