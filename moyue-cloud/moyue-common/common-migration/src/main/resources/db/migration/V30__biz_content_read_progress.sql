-- =============================================================================
-- V30  内容域：阅读进度表
-- -----------------------------------------------------------------------------
-- 跨端阅读进度回写：每个 (user_id, book_id) 一行，upsert 语义（章节号 + 段内位置）。
-- 与书架表分离，便于进度高频更新不影响书架统计。
-- =============================================================================

CREATE TABLE IF NOT EXISTS `moyue_read_progress` (
    `id`           bigint        NOT NULL COMMENT '主键（雪花 ID）',
    `user_id`      bigint        NOT NULL COMMENT '读者 ID',
    `book_id`      bigint        NOT NULL COMMENT '作品 ID',
    `chapter_no`   int           NOT NULL DEFAULT 0 COMMENT '最近阅读章节号',
    `position`     int           NOT NULL DEFAULT 0 COMMENT '段内位置（字符偏移）',
    `create_by`    varchar(64)   DEFAULT NULL COMMENT '创建人',
    `create_time`  datetime      DEFAULT NULL COMMENT '创建时间',
    `update_by`    varchar(64)   DEFAULT NULL COMMENT '更新人',
    `update_time`  datetime      DEFAULT NULL COMMENT '更新时间',
    `remark`       varchar(512)  DEFAULT NULL COMMENT '备注',
    `is_deleted`   tinyint(1)    NOT NULL DEFAULT 0 COMMENT '逻辑删除：0 未删 / 1 已删',
    PRIMARY KEY (`id`),
    UNIQUE KEY `uk_progress_user_book` (`user_id`, `book_id`),
    KEY `idx_progress_user` (`user_id`)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COLLATE = utf8mb4_general_ci COMMENT = '阅读进度表';
