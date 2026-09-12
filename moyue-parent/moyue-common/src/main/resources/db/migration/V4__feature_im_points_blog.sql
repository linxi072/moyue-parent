-- =============================================================
--  墨阅小说网 · MySQL 8 增量迁移 (V4)
--  聊天（单聊/群聊）· 积分商城 · 博客空间
--  引擎 InnoDB / 字符集 utf8mb4 / 主键统一雪花 ID (BIGINT)
--  约定：create_time · update_time 全表统一；is_deleted 逻辑删除
-- =============================================================

SET NAMES utf8mb4;
SET FOREIGN_KEY_CHECKS = 0;

-- -------------------------------------------------------------
-- 09  chat_conversation  会话表
-- -------------------------------------------------------------
DROP TABLE IF EXISTS `chat_conversation`;
CREATE TABLE `chat_conversation` (
  `id`                BIGINT       NOT NULL                COMMENT '会话主键',
  `type`              TINYINT      NOT NULL DEFAULT 1      COMMENT '类型：1 单聊 / 2 群聊',
  `title`             VARCHAR(64)  DEFAULT NULL            COMMENT '群聊名称；单聊为空',
  `owner_id`          BIGINT       DEFAULT NULL            COMMENT '群主 / 创建人 → user.id',
  `last_message`      VARCHAR(512) DEFAULT NULL            COMMENT '最近一条消息预览',
  `last_message_time` DATETIME     DEFAULT NULL            COMMENT '最近消息时间',
  `is_deleted`        TINYINT(1)   NOT NULL DEFAULT 0      COMMENT '逻辑删除',
  `create_time`       DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  `update_time`       DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
  PRIMARY KEY (`id`),
  KEY `idx_owner` (`owner_id`),
  KEY `idx_type` (`type`)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COMMENT = '聊天会话表（单聊 / 群聊）';

-- -------------------------------------------------------------
-- 10  chat_conversation_member  会话成员表
-- -------------------------------------------------------------
DROP TABLE IF EXISTS `chat_conversation_member`;
CREATE TABLE `chat_conversation_member` (
  `id`                   BIGINT       NOT NULL                COMMENT '主键',
  `conversation_id`      BIGINT       NOT NULL                COMMENT '会话 ID → chat_conversation.id',
  `user_id`              BIGINT       NOT NULL                COMMENT '成员 ID → user.id',
  `role`                 TINYINT      NOT NULL DEFAULT 2      COMMENT '角色：1 群主 / 2 成员',
  `last_read_message_id` BIGINT       DEFAULT NULL            COMMENT '最后已读消息 ID',
  `is_deleted`           TINYINT(1)   NOT NULL DEFAULT 0      COMMENT '逻辑删除',
  `create_time`          DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '加入时间',
  `update_time`          DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_conv_user` (`conversation_id`, `user_id`),
  KEY `idx_user` (`user_id`)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COMMENT = '会话成员关系表';

-- -------------------------------------------------------------
-- 11  chat_message  消息表
-- -------------------------------------------------------------
DROP TABLE IF EXISTS `chat_message`;
CREATE TABLE `chat_message` (
  `id`              BIGINT        NOT NULL                COMMENT '消息主键',
  `conversation_id` BIGINT        NOT NULL                COMMENT '会话 ID → chat_conversation.id',
  `sender_id`       BIGINT        NOT NULL                COMMENT '发送人 → user.id',
  `content`         VARCHAR(2000) NOT NULL                COMMENT '消息内容',
  `type`            TINYINT       NOT NULL DEFAULT 1      COMMENT '类型：1 文本 / 2 图片 / 3 系统',
  `status`          TINYINT       NOT NULL DEFAULT 0      COMMENT '状态：0 已发送 / 1 已读',
  `is_deleted`      TINYINT(1)    NOT NULL DEFAULT 0      COMMENT '逻辑删除',
  `create_time`     DATETIME      NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '发送时间',
  `update_time`     DATETIME      NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
  PRIMARY KEY (`id`),
  KEY `idx_conv_time` (`conversation_id`, `create_time`)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COMMENT = '聊天消息表';

-- -------------------------------------------------------------
-- 12  points_account  积分账户表（每用户一行）
-- -------------------------------------------------------------
DROP TABLE IF EXISTS `points_account`;
CREATE TABLE `points_account` (
  `user_id`      BIGINT     NOT NULL                COMMENT '用户 ID（主键）→ user.id',
  `balance`      INT        NOT NULL DEFAULT 0      COMMENT '当前积分余额',
  `total_earned` INT        NOT NULL DEFAULT 0      COMMENT '累计获得',
  `total_spent`  INT        NOT NULL DEFAULT 0      COMMENT '累计消费',
  `is_deleted`   TINYINT(1) NOT NULL DEFAULT 0      COMMENT '逻辑删除',
  `create_time`  DATETIME   NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  `update_time`  DATETIME   NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
  PRIMARY KEY (`user_id`)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COMMENT = '用户积分账户表';

-- -------------------------------------------------------------
-- 13  points_product  积分商品表
-- -------------------------------------------------------------
DROP TABLE IF EXISTS `points_product`;
CREATE TABLE `points_product` (
  `id`          BIGINT        NOT NULL               COMMENT '商品主键',
  `name`        VARCHAR(64)   NOT NULL               COMMENT '商品名称',
  `description` VARCHAR(255)  DEFAULT NULL           COMMENT '商品描述',
  `image_url`   VARCHAR(255)  DEFAULT NULL           COMMENT '商品图片',
  `cost_points` INT           NOT NULL DEFAULT 0     COMMENT '兑换所需积分',
  `stock`       INT           NOT NULL DEFAULT 0     COMMENT '库存',
  `status`      TINYINT       NOT NULL DEFAULT 1     COMMENT '状态：1 上架 / 2 下架',
  `is_deleted`  TINYINT(1)    NOT NULL DEFAULT 0     COMMENT '逻辑删除',
  `create_time` DATETIME      NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  `update_time` DATETIME      NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
  PRIMARY KEY (`id`),
  KEY `idx_status` (`status`)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COMMENT = '积分商城商品表';

-- -------------------------------------------------------------
-- 14  points_order  积分兑换订单表
-- -------------------------------------------------------------
DROP TABLE IF EXISTS `points_order`;
CREATE TABLE `points_order` (
  `id`           BIGINT        NOT NULL               COMMENT '订单主键',
  `user_id`      BIGINT        NOT NULL               COMMENT '兑换人 → user.id',
  `product_id`   BIGINT        NOT NULL               COMMENT '商品 ID → points_product.id',
  `product_name` VARCHAR(64)   DEFAULT NULL           COMMENT '商品名称快照',
  `cost_points`  INT           NOT NULL DEFAULT 0     COMMENT '兑换消耗积分',
  `status`       TINYINT       NOT NULL DEFAULT 0     COMMENT '状态：0 待兑换 / 1 已兑换 / 2 已取消',
  `is_deleted`   TINYINT(1)    NOT NULL DEFAULT 0     COMMENT '逻辑删除',
  `create_time`  DATETIME      NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  `update_time`  DATETIME      NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
  PRIMARY KEY (`id`),
  KEY `idx_user` (`user_id`),
  KEY `idx_product` (`product_id`)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COMMENT = '积分兑换订单表';

-- -------------------------------------------------------------
-- 15  blog_post  博客文章表
-- -------------------------------------------------------------
DROP TABLE IF EXISTS `blog_post`;
CREATE TABLE `blog_post` (
  `id`            BIGINT        NOT NULL               COMMENT '文章主键',
  `author_id`     BIGINT        NOT NULL               COMMENT '作者 → user.id',
  `title`         VARCHAR(128)  NOT NULL               COMMENT '标题',
  `cover_url`     VARCHAR(255)  DEFAULT NULL           COMMENT '封面',
  `summary`       VARCHAR(255)  DEFAULT NULL           COMMENT '摘要',
  `content`       MEDIUMTEXT    NOT NULL               COMMENT '正文',
  `status`        TINYINT       NOT NULL DEFAULT 1     COMMENT '状态：0 草稿 / 1 已发布 / 2 已下架',
  `like_count`    INT           NOT NULL DEFAULT 0     COMMENT '点赞数',
  `comment_count` INT           NOT NULL DEFAULT 0     COMMENT '评论数',
  `view_count`    INT           NOT NULL DEFAULT 0     COMMENT '浏览数',
  `is_deleted`    TINYINT(1)    NOT NULL DEFAULT 0     COMMENT '逻辑删除',
  `create_time`   DATETIME      NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  `update_time`   DATETIME      NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
  PRIMARY KEY (`id`),
  KEY `idx_author` (`author_id`),
  KEY `idx_status` (`status`)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COMMENT = '博客文章表';

-- -------------------------------------------------------------
-- 16  blog_comment  博客评论表
-- -------------------------------------------------------------
DROP TABLE IF EXISTS `blog_comment`;
CREATE TABLE `blog_comment` (
  `id`         BIGINT        NOT NULL               COMMENT '评论主键',
  `post_id`    BIGINT        NOT NULL               COMMENT '文章 ID → blog_post.id',
  `user_id`    BIGINT        NOT NULL               COMMENT '评论人 → user.id',
  `content`    VARCHAR(1000) NOT NULL               COMMENT '评论内容',
  `like_count` INT           NOT NULL DEFAULT 0     COMMENT '点赞数',
  `is_deleted` TINYINT(1)    NOT NULL DEFAULT 0     COMMENT '逻辑删除',
  `create_time` DATETIME      NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  `update_time` DATETIME      NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
  PRIMARY KEY (`id`),
  KEY `idx_post` (`post_id`),
  KEY `idx_user` (`user_id`)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COMMENT = '博客评论表';

-- -------------------------------------------------------------
-- 17  blog_like  博客点赞表（防重复点赞）
-- -------------------------------------------------------------
DROP TABLE IF EXISTS `blog_like`;
CREATE TABLE `blog_like` (
  `id`         BIGINT       NOT NULL                COMMENT '主键',
  `post_id`    BIGINT       NOT NULL                COMMENT '文章 ID → blog_post.id',
  `user_id`    BIGINT       NOT NULL                COMMENT '点赞人 → user.id',
  `is_deleted` TINYINT(1)   NOT NULL DEFAULT 0      COMMENT '逻辑删除',
  `create_time` DATETIME    NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  `update_time` DATETIME    NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_post_user` (`post_id`, `user_id`),
  KEY `idx_post` (`post_id`)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COMMENT = '博客点赞表';

-- -------------------------------------------------------------
-- 种子数据：演示积分账户 / 商品 / 博客
-- -------------------------------------------------------------
INSERT INTO `points_account` (`user_id`, `balance`, `total_earned`, `total_spent`)
VALUES (1, 500, 500, 0)
ON DUPLICATE KEY UPDATE `balance` = `balance`;

INSERT INTO `points_product` (`id`, `name`, `description`, `image_url`, `cost_points`, `stock`, `status`)
VALUES
  (2001, '书币月卡', '赠送 300 书币，30 天有效', NULL, 200, 100, 1),
  (2002, '墨阅定制书签', '金属书签一套', NULL, 150, 50, 1),
  (2003, '作者月度推荐位', '作品首页推荐 7 天', NULL, 800, 10, 1)
ON DUPLICATE KEY UPDATE `name` = VALUES(`name`);

INSERT INTO `blog_post` (`id`, `author_id`, `title`, `cover_url`, `summary`, `content`, `status`, `like_count`, `comment_count`, `view_count`)
VALUES (3001, 1, '我的写作心得：如何从零开始写一本小说', NULL,
        '分享三年创作路上的方法与踩坑。',
        '写作是一场马拉松，与其追求一天写一万字，不如先养成每天稳定的输出节奏……（正文示例）',
        1, 12, 3, 240)
ON DUPLICATE KEY UPDATE `title` = VALUES(`title`);

SET FOREIGN_KEY_CHECKS = 1;
