-- =============================================================================
-- V34  互动域：评论点赞记录表
-- -----------------------------------------------------------------------------
-- 对应 架构说明书 7.4《互动》：点赞切换用唯一键 (comment_id, user_id) 防重，
-- 命中即视为已赞（幂等），并返回 moyue_comment.like_count 当前值。
-- =============================================================================

CREATE TABLE IF NOT EXISTS `moyue_comment_like` (
    `id`          bigint        NOT NULL COMMENT '主键（雪花 ID）',
    `comment_id`  bigint        NOT NULL COMMENT '评论 ID',
    `user_id`     bigint        NOT NULL COMMENT '点赞用户（取网关注入头）',
    `create_by`   varchar(64)   DEFAULT NULL COMMENT '创建人',
    `create_time` datetime      DEFAULT NULL COMMENT '创建时间',
    `update_by`   varchar(64)   DEFAULT NULL COMMENT '更新人',
    `update_time` datetime      DEFAULT NULL COMMENT '更新时间',
    `remark`      varchar(512)  DEFAULT NULL COMMENT '备注',
    `is_deleted`  tinyint(1)    NOT NULL DEFAULT 0 COMMENT '逻辑删除：0 未删 / 1 已删',
    PRIMARY KEY (`id`),
    UNIQUE KEY `uk_comment_like` (`comment_id`, `user_id`),
    KEY `idx_comment_like_comment` (`comment_id`)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COLLATE = utf8mb4_general_ci COMMENT = '评论点赞记录表';
