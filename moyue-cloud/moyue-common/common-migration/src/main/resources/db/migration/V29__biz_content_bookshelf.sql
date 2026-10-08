-- =============================================================================
-- V29  内容域：书架表
-- -----------------------------------------------------------------------------
-- 读者书架：加入 / 移出（逻辑删除）/ 进度回写。唯一键 (user_id, book_id) 保证
-- 一人一书仅一行：移出仅置 is_deleted=1，重新加入时「复活」该行而非插入新行，
-- 实现「唯一键冲突自动复活」（见架构缺口补齐说明 7.3 书架幂等约定）。
-- =============================================================================

CREATE TABLE IF NOT EXISTS `moyue_bookshelf` (
    `id`             bigint        NOT NULL COMMENT '主键（雪花 ID）',
    `user_id`        bigint        NOT NULL COMMENT '读者 ID（取网关注入 X-User-Id，不接受前端传入）',
    `book_id`        bigint        NOT NULL COMMENT '作品 ID',
    `last_chapter_no` int          NOT NULL DEFAULT 0 COMMENT '最近阅读章节号',
    `create_by`      varchar(64)   DEFAULT NULL COMMENT '创建人',
    `create_time`    datetime      DEFAULT NULL COMMENT '创建时间',
    `update_by`      varchar(64)   DEFAULT NULL COMMENT '更新人',
    `update_time`    datetime      DEFAULT NULL COMMENT '更新时间',
    `remark`         varchar(512)  DEFAULT NULL COMMENT '备注',
    `is_deleted`     tinyint(1)    NOT NULL DEFAULT 0 COMMENT '逻辑删除：0 未删 / 1 已删',
    PRIMARY KEY (`id`),
    UNIQUE KEY `uk_shelf_user_book` (`user_id`, `book_id`),
    KEY `idx_shelf_user` (`user_id`, `is_deleted`)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COLLATE = utf8mb4_general_ci COMMENT = '书架表';
