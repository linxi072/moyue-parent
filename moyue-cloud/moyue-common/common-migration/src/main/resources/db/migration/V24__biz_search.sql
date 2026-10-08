-- =============================================================================
-- V24  搜索域：搜索热词表
-- -----------------------------------------------------------------------------
-- 对应 架构缺口补齐说明「填充业务模块」之 search（搜索历史 / 热词）。
-- 热词搜索本身走 ES / 倒排索引，本表仅沉淀运营可维护的热词与权重。
-- =============================================================================

CREATE TABLE IF NOT EXISTS `moyue_search_hot_word` (
    `id`         bigint        NOT NULL COMMENT '主键（雪花 ID）',
    `word`       varchar(128)  NOT NULL DEFAULT '' COMMENT '热词',
    `hit_count`  int           NOT NULL DEFAULT 0 COMMENT '命中次数',
    `weight`     int           NOT NULL DEFAULT 0 COMMENT '权重（排序用）',
    `enabled`    tinyint       NOT NULL DEFAULT 1 COMMENT '是否启用：0 停用 / 1 启用',
    `create_by`  varchar(64)   DEFAULT NULL COMMENT '创建人',
    `create_time` datetime     DEFAULT NULL COMMENT '创建时间',
    `update_by`  varchar(64)   DEFAULT NULL COMMENT '更新人',
    `update_time` datetime     DEFAULT NULL COMMENT '更新时间',
    `remark`     varchar(512)  DEFAULT NULL COMMENT '备注',
    `is_deleted` tinyint(1)    NOT NULL DEFAULT 0 COMMENT '逻辑删除：0 未删 / 1 已删',
    PRIMARY KEY (`id`),
    UNIQUE KEY `uk_hw_word` (`word`),
    KEY `idx_hw_enabled_weight` (`enabled`, `weight`)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COLLATE = utf8mb4_general_ci COMMENT = '搜索热词表';
