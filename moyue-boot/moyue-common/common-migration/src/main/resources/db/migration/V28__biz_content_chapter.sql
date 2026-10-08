-- =============================================================================
-- V28  内容域：章节表
-- -----------------------------------------------------------------------------
-- 对应 架构说明书 7.3《内容消费》：作品 / 章节 / 阅读进度 / 书架。
-- 章节生命周期：草稿(0) → 发布(1) / 定时发布(2)；序号 chapter_no 从 1 递增，
-- 发布时取 max+1 自动排定。审计列与 is_deleted 按 6.5 规范补齐（对齐 V14）。
-- =============================================================================

CREATE TABLE IF NOT EXISTS `moyue_chapter` (
    `id`           bigint        NOT NULL COMMENT '章节主键（雪花 ID）',
    `book_id`      bigint        NOT NULL COMMENT '所属作品',
    `chapter_no`   int           NOT NULL DEFAULT 1 COMMENT '章节序号，从 1 递增',
    `title`        varchar(200)  NOT NULL DEFAULT '' COMMENT '章节标题',
    `content`      longtext      COMMENT '正文',
    `word_count`   int           NOT NULL DEFAULT 0 COMMENT '本章字数',
    `status`       tinyint       NOT NULL DEFAULT 0 COMMENT '状态：0 草稿 / 1 已发布 / 2 定时发布',
    `publish_time` datetime      DEFAULT NULL COMMENT '发布时间（定时发布时为计划时间）',
    `create_by`    varchar(64)   DEFAULT NULL COMMENT '创建人',
    `create_time`  datetime      DEFAULT NULL COMMENT '创建时间',
    `update_by`    varchar(64)   DEFAULT NULL COMMENT '更新人',
    `update_time`  datetime      DEFAULT NULL COMMENT '更新时间',
    `remark`       varchar(512)  DEFAULT NULL COMMENT '备注',
    `is_deleted`   tinyint(1)    NOT NULL DEFAULT 0 COMMENT '逻辑删除：0 未删 / 1 已删',
    PRIMARY KEY (`id`),
    KEY `idx_chapter_book` (`book_id`, `chapter_no`),
    KEY `idx_chapter_publish` (`status`, `publish_time`)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COLLATE = utf8mb4_general_ci COMMENT = '章节表';
