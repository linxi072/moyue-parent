-- =============================================================================
-- V22  社区域：社区帖子表
-- -----------------------------------------------------------------------------
-- 对应 架构缺口补齐说明「填充业务模块」之 social（社区 / 互动）。
-- =============================================================================

CREATE TABLE IF NOT EXISTS `moyue_community_post` (
    `id`           bigint        NOT NULL COMMENT '帖子主键（雪花 ID）',
    `user_id`      bigint        DEFAULT NULL COMMENT '发布用户 ID',
    `user_name`    varchar(128)  NOT NULL DEFAULT '' COMMENT '发布用户名',
    `title`        varchar(256)  NOT NULL DEFAULT '' COMMENT '标题',
    `content`      varchar(1024) DEFAULT NULL COMMENT '正文',
    `topic`        varchar(128)  DEFAULT NULL COMMENT '话题标签',
    `like_count`   int           NOT NULL DEFAULT 0 COMMENT '点赞数',
    `comment_count` int          NOT NULL DEFAULT 0 COMMENT '评论数',
    `status`       tinyint       NOT NULL DEFAULT 1 COMMENT '状态：0 待审 / 1 已发 / 2 下架',
    `create_by`    varchar(64)   DEFAULT NULL COMMENT '创建人',
    `create_time`  datetime      DEFAULT NULL COMMENT '创建时间',
    `update_by`    varchar(64)   DEFAULT NULL COMMENT '更新人',
    `update_time`  datetime      DEFAULT NULL COMMENT '更新时间',
    `remark`       varchar(512)  DEFAULT NULL COMMENT '备注',
    `is_deleted`   tinyint(1)    NOT NULL DEFAULT 0 COMMENT '逻辑删除：0 未删 / 1 已删',
    PRIMARY KEY (`id`),
    KEY `idx_post_status` (`status`, `is_deleted`),
    KEY `idx_post_user` (`user_id`)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COLLATE = utf8mb4_general_ci COMMENT = '社区帖子表';
