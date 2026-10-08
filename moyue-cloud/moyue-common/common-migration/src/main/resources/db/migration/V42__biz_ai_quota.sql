-- =============================================================================
-- V42  AI 域：AI 配额表
-- -----------------------------------------------------------------------------
-- 对应「7 个业务管理模块 · M5 ai」：用户运行 AI 任务前扣减配额（token），
-- 不足则拒绝执行。真实大模型调用为结构占位（沙箱无凭证），仅做配额状态机。
-- =============================================================================

CREATE TABLE IF NOT EXISTS `moyue_ai_quota` (
    `id`         bigint        NOT NULL COMMENT '主键（雪花 ID）',
    `user_id`    bigint        NOT NULL DEFAULT 0 COMMENT '用户 ID（唯一）',
    `total`      int           NOT NULL DEFAULT 0 COMMENT '配额总额（token）',
    `used`       int           NOT NULL DEFAULT 0 COMMENT '已用（token）',
    `remain`     int           NOT NULL DEFAULT 0 COMMENT '剩余（token）',
    `create_by`  varchar(64)   DEFAULT NULL COMMENT '创建人',
    `create_time` datetime     DEFAULT NULL COMMENT '创建时间',
    `update_by`  varchar(64)   DEFAULT NULL COMMENT '更新人',
    `update_time` datetime     DEFAULT NULL COMMENT '更新时间',
    `remark`     varchar(512)  DEFAULT NULL COMMENT '备注',
    `is_deleted` tinyint(1)    NOT NULL DEFAULT 0 COMMENT '逻辑删除：0 未删 / 1 已删',
    PRIMARY KEY (`id`),
    UNIQUE KEY `uk_ai_quota_user` (`user_id`),
    KEY `idx_ai_quota_remain` (`remain`)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COLLATE = utf8mb4_general_ci COMMENT = 'AI 配额表';
