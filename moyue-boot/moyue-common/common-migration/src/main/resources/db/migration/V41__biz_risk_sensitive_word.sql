-- =============================================================================
-- V41  风控域：敏感词表
-- -----------------------------------------------------------------------------
-- 对应「7 个业务管理模块 · M4 risk」：运营维护敏感词分级（1 拦截 / 2 告警），
-- SensitiveService.contains(text) 供后续 content/social 在上架 / 评论时调用。
-- =============================================================================

CREATE TABLE IF NOT EXISTS `moyue_sensitive_word` (
    `id`         bigint        NOT NULL COMMENT '主键（雪花 ID）',
    `word`       varchar(128)  NOT NULL DEFAULT '' COMMENT '敏感词（唯一）',
    `level`      tinyint       NOT NULL DEFAULT 1 COMMENT '级别：1 拦截 / 2 告警',
    `enabled`    tinyint       NOT NULL DEFAULT 1 COMMENT '是否启用：0 停用 / 1 启用',
    `hit_count`  int           NOT NULL DEFAULT 0 COMMENT '累计命中次数',
    `create_by`  varchar(64)   DEFAULT NULL COMMENT '创建人',
    `create_time` datetime     DEFAULT NULL COMMENT '创建时间',
    `update_by`  varchar(64)   DEFAULT NULL COMMENT '更新人',
    `update_time` datetime     DEFAULT NULL COMMENT '更新时间',
    `remark`     varchar(512)  DEFAULT NULL COMMENT '备注',
    `is_deleted` tinyint(1)    NOT NULL DEFAULT 0 COMMENT '逻辑删除：0 未删 / 1 已删',
    PRIMARY KEY (`id`),
    UNIQUE KEY `uk_sensitive_word` (`word`),
    KEY `idx_sensitive_level_enabled` (`level`, `enabled`)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COLLATE = utf8mb4_general_ci COMMENT = '敏感词表';
