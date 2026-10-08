-- =============================================================================
-- V31  互动域：评论表
-- -----------------------------------------------------------------------------
-- 对应 架构说明书 7.4《互动》：评论按 book_id 聚合，支持楼中楼（reply_to）。
-- 点赞数由唯一键防重的点赞记录维护（本期简化为计数字段 + 切换接口）。
-- =============================================================================

CREATE TABLE IF NOT EXISTS `moyue_comment` (
    `id`          bigint        NOT NULL COMMENT '评论主键（雪花 ID）',
    `book_id`     bigint        NOT NULL COMMENT '作品 ID',
    `chapter_id`  bigint        DEFAULT NULL COMMENT '章节 ID（可空，章内评论）',
    `user_id`     bigint        NOT NULL COMMENT '评论人（取网关注入头，防伪造）',
    `reply_to`    bigint        DEFAULT NULL COMMENT '回复的评论 ID（楼中楼）',
    `content`     varchar(1000) NOT NULL DEFAULT '' COMMENT '评论内容',
    `like_count`  int           NOT NULL DEFAULT 0 COMMENT '点赞数',
    `create_by`   varchar(64)   DEFAULT NULL COMMENT '创建人',
    `create_time` datetime      DEFAULT NULL COMMENT '创建时间',
    `update_by`   varchar(64)   DEFAULT NULL COMMENT '更新人',
    `update_time` datetime      DEFAULT NULL COMMENT '更新时间',
    `remark`      varchar(512)  DEFAULT NULL COMMENT '备注',
    `is_deleted`  tinyint(1)    NOT NULL DEFAULT 0 COMMENT '逻辑删除：0 未删 / 1 已删',
    PRIMARY KEY (`id`),
    KEY `idx_comment_book` (`book_id`, `chapter_id`, `is_deleted`),
    KEY `idx_comment_user` (`user_id`)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COLLATE = utf8mb4_general_ci COMMENT = '评论表';
