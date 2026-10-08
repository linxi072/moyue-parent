-- =============================================================================
-- V21  内容域：作品（小说）表
-- -----------------------------------------------------------------------------
-- 对应 架构缺口补齐说明「填充业务模块」之 content（作品 / 章节 / 阅读进度 / 书架 / 封面）。
-- 本轮落地核心聚合 Book；审计列与 is_deleted 按 6.5 规范补齐（对齐 V14）。
-- =============================================================================

CREATE TABLE IF NOT EXISTS `moyue_book` (
    `id`          bigint        NOT NULL COMMENT '作品主键（雪花 ID）',
    `title`       varchar(256)  NOT NULL DEFAULT '' COMMENT '书名',
    `author_name` varchar(128)  NOT NULL DEFAULT '' COMMENT '作者名',
    `category_id` bigint        DEFAULT NULL COMMENT '分类 → sys_dict_data(dict_type = book_category)',
    `status`      tinyint       NOT NULL DEFAULT 0 COMMENT '状态：0 连载中 / 1 已完结 / 2 已下架',
    `word_count`  bigint        NOT NULL DEFAULT 0 COMMENT '总字数',
    `intro`       varchar(1024) DEFAULT NULL COMMENT '简介',
    `cover_url`   varchar(512)  DEFAULT NULL COMMENT '封面 URL',
    `create_by`   varchar(64)   DEFAULT NULL COMMENT '创建人',
    `create_time` datetime      DEFAULT NULL COMMENT '创建时间',
    `update_by`   varchar(64)   DEFAULT NULL COMMENT '更新人',
    `update_time` datetime      DEFAULT NULL COMMENT '更新时间',
    `remark`      varchar(512)  DEFAULT NULL COMMENT '备注',
    `is_deleted`  tinyint(1)    NOT NULL DEFAULT 0 COMMENT '逻辑删除：0 未删 / 1 已删',
    PRIMARY KEY (`id`),
    KEY `idx_book_status` (`status`, `is_deleted`),
    KEY `idx_book_category` (`category_id`)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COLLATE = utf8mb4_general_ci COMMENT = '作品（小说）表';
