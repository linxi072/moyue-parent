-- =============================================================
--  墨阅小说网 · MySQL 8 增量迁移 (V5)
--  评论点赞表：支撑「评论点赞」防重复点赞（呼应 V4 的 blog_like）
--  引擎 InnoDB / 字符集 utf8mb4 / 主键统一雪花 ID (BIGINT)
--  约定：create_time · update_time 全表统一；is_deleted 逻辑删除
-- =============================================================

SET NAMES utf8mb4;

-- -------------------------------------------------------------
-- 18  comment_like  评论点赞表（防重复点赞）
-- -------------------------------------------------------------
DROP TABLE IF EXISTS `comment_like`;
CREATE TABLE `comment_like` (
  `id`         BIGINT       NOT NULL                COMMENT '主键',
  `comment_id` BIGINT       NOT NULL                COMMENT '评论 ID → comment.id',
  `user_id`    BIGINT       NOT NULL                COMMENT '点赞人 → user.id',
  `is_deleted` TINYINT(1)   NOT NULL DEFAULT 0      COMMENT '逻辑删除',
  `create_time` DATETIME    NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  `update_time` DATETIME    NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_comment_user` (`comment_id`, `user_id`),
  KEY `idx_comment` (`comment_id`)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COMMENT = '评论点赞表';
