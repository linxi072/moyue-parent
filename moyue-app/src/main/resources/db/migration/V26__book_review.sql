-- 书评与评分模块：book 表新增评分聚合列 + book_review / review_like 表
-- 约定：索引名沿用全库范式（V1 init.sql 等）加反引号，便于 tools/gen-h2-schema.py 做表前缀去重
ALTER TABLE `book`
    ADD COLUMN `rating_avg`   DECIMAL(3,2) NOT NULL DEFAULT 0.00 COMMENT '评分均值 0.00~5.00',
    ADD COLUMN `rating_count` INT         NOT NULL DEFAULT 0     COMMENT '评分人数';

CREATE TABLE `book_review` (
    `id`          BIGINT        NOT NULL               COMMENT '书评主键',
    `user_id`     BIGINT        NOT NULL               COMMENT '评论人 → user.id',
    `book_id`     BIGINT        NOT NULL               COMMENT '作品 ID → book.id',
    `score`       TINYINT       NOT NULL               COMMENT '星级 1~5',
    `content`     VARCHAR(1000) DEFAULT NULL           COMMENT '书评内容',
    `audit_score` DECIMAL(4,3)  DEFAULT NULL           COMMENT '机审风险分 0.000~1.000',
    `status`      TINYINT       NOT NULL DEFAULT 0     COMMENT '0 待审 / 1 已通过 / 2 已驳回',
    `like_count`  INT           NOT NULL DEFAULT 0     COMMENT '点赞数',
    `is_deleted`  TINYINT(1)    NOT NULL DEFAULT 0     COMMENT '逻辑删除',
    `create_time` DATETIME      NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    `update_time` DATETIME      NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
    PRIMARY KEY (`id`),
    KEY `idx_book_status` (`book_id`, `status`)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COMMENT = '书评表';

CREATE TABLE `review_like` (
    `id`          BIGINT        NOT NULL               COMMENT '点赞主键',
    `review_id`   BIGINT        NOT NULL               COMMENT '书评 ID → book_review.id',
    `user_id`     BIGINT        NOT NULL               COMMENT '点赞人 → user.id',
    `is_deleted`  TINYINT(1)    NOT NULL DEFAULT 0     COMMENT '逻辑删除（取消点赞）',
    `create_time` DATETIME      NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    `update_time` DATETIME      NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
    PRIMARY KEY (`id`),
    UNIQUE KEY `uk_review_user` (`review_id`, `user_id`),
    KEY `idx_review` (`review_id`)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COMMENT = '书评点赞表';
