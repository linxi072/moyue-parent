-- 付费章节与订阅阅读模块：chapter 表新增计费字段 + chapter_entitlement / book_subscription 表
-- 约定：索引名加反引号，便于 tools/gen-h2-schema.py 做表前缀去重（H2 索引名 schema 全局唯一）
ALTER TABLE `chapter`
    ADD COLUMN `is_paid`            TINYINT(1)    NOT NULL DEFAULT 0     COMMENT '0 免费 / 1 付费',
    ADD COLUMN `price`               DECIMAL(10,2) NOT NULL DEFAULT 0.00 COMMENT '单章解锁价（元）',
    ADD COLUMN `free_preview_chars` INT           NOT NULL DEFAULT 0     COMMENT '免费预览字数（阅读端截断）';

CREATE TABLE `chapter_entitlement` (
    `id`          BIGINT        NOT NULL               COMMENT '单章解锁记录主键',
    `user_id`     BIGINT        NOT NULL               COMMENT '用户 → user.id',
    `chapter_id`  BIGINT        NOT NULL               COMMENT '章节 → chapter.id',
    `book_id`     BIGINT        NOT NULL               COMMENT '作品 → book.id',
    `order_no`    VARCHAR(64)   NOT NULL               COMMENT '订单号',
    `amount`      DECIMAL(10,2) NOT NULL               COMMENT '实付金额（元，已含会员折扣）',
    `channel`     VARCHAR(32)   NOT NULL DEFAULT 'stub' COMMENT '支付渠道 stub/real',
    `status`      TINYINT(1)    NOT NULL DEFAULT 1     COMMENT '0 待支付 / 1 已解锁 / 2 已退款',
    `expire_time` DATETIME      DEFAULT NULL           COMMENT '解锁有效期（NULL 表示永久）',
    `is_deleted`  TINYINT(1)    NOT NULL DEFAULT 0     COMMENT '逻辑删除',
    `create_time` DATETIME      NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    `update_time` DATETIME      NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
    PRIMARY KEY (`id`),
    UNIQUE KEY `chapter_entitlement_uk_user_chapter` (`user_id`, `chapter_id`),
    KEY `chapter_entitlement_idx_user` (`user_id`),
    KEY `chapter_entitlement_idx_chapter` (`chapter_id`)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COMMENT = '章节解锁（单章购买）记录表';

CREATE TABLE `book_subscription` (
    `id`          BIGINT        NOT NULL               COMMENT '整本订阅主键',
    `user_id`     BIGINT        NOT NULL               COMMENT '用户 → user.id',
    `book_id`     BIGINT        NOT NULL               COMMENT '作品 → book.id',
    `order_no`    VARCHAR(64)   NOT NULL               COMMENT '订单号',
    `amount`      DECIMAL(10,2) NOT NULL               COMMENT '订阅金额（元）',
    `channel`     VARCHAR(32)   NOT NULL DEFAULT 'stub' COMMENT '支付渠道 stub/real',
    `status`      TINYINT(1)    NOT NULL DEFAULT 1     COMMENT '0 待支付 / 1 生效中 / 2 已过期 / 3 已取消',
    `start_time`  DATETIME      DEFAULT NULL           COMMENT '生效开始',
    `end_time`    DATETIME      DEFAULT NULL           COMMENT '生效结束（NULL 表示永久）',
    `is_deleted`  TINYINT(1)    NOT NULL DEFAULT 0     COMMENT '逻辑删除',
    `create_time` DATETIME      NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    `update_time` DATETIME      NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
    PRIMARY KEY (`id`),
    UNIQUE KEY `book_subscription_uk_user_book` (`user_id`, `book_id`),
    KEY `book_subscription_idx_user` (`user_id`),
    KEY `book_subscription_idx_book` (`book_id`)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COMMENT = '整本订阅记录表';
