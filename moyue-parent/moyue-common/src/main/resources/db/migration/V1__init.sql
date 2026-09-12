-- =============================================================
--  墨阅小说网 · MySQL 8 建表脚本 (moyue-schema.sql)
--  Flyway 迁移 V1：由 deliverables/moyue-schema.sql 原样落地
--  引擎 InnoDB / 字符集 utf8mb4 / 主键统一雪花 ID (BIGINT)
--  约定：create_time · update_time 全表统一；is_deleted 逻辑删除
-- =============================================================

SET NAMES utf8mb4;
SET FOREIGN_KEY_CHECKS = 0;

-- -------------------------------------------------------------
-- 01  user  用户表
-- -------------------------------------------------------------
DROP TABLE IF EXISTS `user`;
CREATE TABLE `user` (
  `id`          BIGINT       NOT NULL                COMMENT '用户主键（雪花 ID）',
  `phone`       VARCHAR(11)  NOT NULL                COMMENT '手机号，登录账号',
  `nickname`    VARCHAR(32)  NOT NULL                COMMENT '用户昵称',
  `password`    VARCHAR(72)  NOT NULL                COMMENT 'BCrypt 密码哈希',
  `avatar_url`  VARCHAR(255) DEFAULT NULL            COMMENT '头像地址',
  `role`        TINYINT      NOT NULL DEFAULT 1      COMMENT '角色：1 读者 / 2 作者 / 3 管理员',
  `status`      TINYINT      NOT NULL DEFAULT 1      COMMENT '状态：0 禁用 / 1 正常',
  `is_deleted`  TINYINT(1)   NOT NULL DEFAULT 0      COMMENT '逻辑删除：0 否 / 1 是',
  `create_time` DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  `update_time` DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_phone` (`phone`),
  KEY `idx_role_status` (`role`, `status`)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COMMENT = '用户表（读者 / 作者 / 管理员）';

-- -------------------------------------------------------------
-- 02  book  作品表
-- -------------------------------------------------------------
DROP TABLE IF EXISTS `book`;
CREATE TABLE `book` (
  `id`          BIGINT        NOT NULL               COMMENT '作品主键',
  `author_id`   BIGINT        NOT NULL               COMMENT '作者 ID → user.id',
  `title`       VARCHAR(128)  NOT NULL               COMMENT '作品名称',
  `cover_url`   VARCHAR(255)  DEFAULT NULL           COMMENT '封面地址',
  `category_id` BIGINT        NOT NULL               COMMENT '分类 ID',
  `tags`        VARCHAR(128)  DEFAULT NULL           COMMENT '标签，逗号分隔',
  `intro`       VARCHAR(1000) DEFAULT NULL           COMMENT '作品简介',
  `status`      TINYINT       NOT NULL DEFAULT 1     COMMENT '状态：1 连载中 / 2 已完结 / 3 已下架',
  `word_count`  INT           NOT NULL DEFAULT 0     COMMENT '累计字数',
  `click_count` BIGINT        NOT NULL DEFAULT 0     COMMENT '累计点击',
  `is_deleted`  TINYINT(1)    NOT NULL DEFAULT 0     COMMENT '逻辑删除',
  `create_time` DATETIME      NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  `update_time` DATETIME      NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
  PRIMARY KEY (`id`),
  KEY `idx_author` (`author_id`),
  KEY `idx_category` (`category_id`),
  KEY `idx_status` (`status`)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COMMENT = '作品表';

-- -------------------------------------------------------------
-- 03  chapter  章节表
-- -------------------------------------------------------------
DROP TABLE IF EXISTS `chapter`;
CREATE TABLE `chapter` (
  `id`           BIGINT       NOT NULL               COMMENT '章节主键',
  `book_id`      BIGINT       NOT NULL               COMMENT '作品 ID → book.id',
  `chapter_no`   INT          NOT NULL               COMMENT '章节序号',
  `title`        VARCHAR(128) NOT NULL               COMMENT '章节标题',
  `content`      MEDIUMTEXT   NOT NULL               COMMENT '章节正文',
  `word_count`   INT          NOT NULL DEFAULT 0     COMMENT '本章字数',
  `status`       TINYINT      NOT NULL DEFAULT 0     COMMENT '状态：0 草稿 / 1 审核中 / 2 已发布 / 3 已驳回',
  `publish_time` DATETIME     DEFAULT NULL           COMMENT '发布时间（定时发布到点写入）',
  `is_deleted`   TINYINT(1)   NOT NULL DEFAULT 0     COMMENT '逻辑删除',
  `create_time`  DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  `update_time`  DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_book_no` (`book_id`, `chapter_no`),
  KEY `idx_book_status` (`book_id`, `status`)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COMMENT = '章节表';

-- -------------------------------------------------------------
-- 04  comment  评论表
-- -------------------------------------------------------------
DROP TABLE IF EXISTS `comment`;
CREATE TABLE `comment` (
  `id`          BIGINT        NOT NULL               COMMENT '评论主键',
  `user_id`     BIGINT        NOT NULL               COMMENT '评论人 ID → user.id',
  `book_id`     BIGINT        NOT NULL               COMMENT '作品 ID → book.id',
  `chapter_id`  BIGINT        DEFAULT NULL           COMMENT '章节 ID，书评为 NULL',
  `content`     VARCHAR(1000) NOT NULL               COMMENT '评论内容',
  `audit_score` DECIMAL(4,3)  DEFAULT NULL           COMMENT '机审风险分 0.000-1.000',
  `status`      TINYINT       NOT NULL DEFAULT 0     COMMENT '状态：0 待审 / 1 已通过 / 2 已驳回',
  `like_count`  INT           NOT NULL DEFAULT 0     COMMENT '点赞数',
  `is_deleted`  TINYINT(1)    NOT NULL DEFAULT 0     COMMENT '逻辑删除',
  `create_time` DATETIME      NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  `update_time` DATETIME      NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
  PRIMARY KEY (`id`),
  KEY `idx_book_status` (`book_id`, `status`),
  KEY `idx_user` (`user_id`)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COMMENT = '评论表';

-- -------------------------------------------------------------
-- 05  reward_order  打赏订单表
-- -------------------------------------------------------------
DROP TABLE IF EXISTS `reward_order`;
CREATE TABLE `reward_order` (
  `id`          BIGINT        NOT NULL               COMMENT '订单主键',
  `order_no`    VARCHAR(32)   NOT NULL               COMMENT '订单号，支付回调幂等键',
  `user_id`     BIGINT        NOT NULL               COMMENT '打赏人 ID → user.id',
  `book_id`     BIGINT        NOT NULL               COMMENT '作品 ID → book.id',
  `chapter_id`  BIGINT        DEFAULT NULL           COMMENT '章节 ID，可为空',
  `amount`      DECIMAL(10,2) NOT NULL               COMMENT '打赏金额（元）',
  `pay_channel` TINYINT       NOT NULL DEFAULT 1     COMMENT '支付渠道：1 微信 / 2 支付宝',
  `status`      TINYINT       NOT NULL DEFAULT 0     COMMENT '状态：0 待支付 / 1 已支付 / 2 已关闭 / 3 已退款',
  `pay_time`    DATETIME      DEFAULT NULL           COMMENT '支付完成时间',
  `create_time` DATETIME      NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  `update_time` DATETIME      NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_order_no` (`order_no`),
  KEY `idx_user` (`user_id`),
  KEY `idx_book` (`book_id`)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COMMENT = '打赏订单表';

-- -------------------------------------------------------------
-- 06  bookshelf  书架表
-- -------------------------------------------------------------
DROP TABLE IF EXISTS `bookshelf`;
CREATE TABLE `bookshelf` (
  `id`              BIGINT     NOT NULL              COMMENT '书架记录主键',
  `user_id`         BIGINT     NOT NULL              COMMENT '读者 ID → user.id',
  `book_id`         BIGINT     NOT NULL              COMMENT '书籍 ID → book.id',
  `last_chapter_id` BIGINT     DEFAULT NULL          COMMENT '最后阅读章节 → chapter.id',
  `is_deleted`      TINYINT(1) NOT NULL DEFAULT 0    COMMENT '逻辑删除',
  `create_time`     DATETIME   NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '加入书架时间',
  `update_time`     DATETIME   NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_user_book` (`user_id`, `book_id`),
  KEY `idx_user` (`user_id`)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COMMENT = '书架表（用户与书籍多对多）';

-- -------------------------------------------------------------
-- 07  稿酬流水表（作者收入，按月结算）
-- -------------------------------------------------------------
DROP TABLE IF EXISTS `author_income`;
CREATE TABLE `author_income` (
  `id`           BIGINT        NOT NULL              COMMENT '流水主键',
  `author_id`    BIGINT        NOT NULL              COMMENT '作者 ID → user.id',
  `book_id`      BIGINT        DEFAULT NULL          COMMENT '作品 ID',
  `order_no`     VARCHAR(32)   DEFAULT NULL          COMMENT '来源订单号（打赏分成）',
  `income_type`  TINYINT       NOT NULL              COMMENT '类型：1 订阅 / 2 打赏分成 / 3 全勤奖',
  `amount`       DECIMAL(10,2) NOT NULL              COMMENT '金额（元）',
  `settle_month` VARCHAR(7)    NOT NULL              COMMENT '结算月份 YYYY-MM',
  `create_time`  DATETIME      NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  PRIMARY KEY (`id`),
  KEY `idx_author_month` (`author_id`, `settle_month`)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COMMENT = '作者稿酬流水表';

-- -------------------------------------------------------------
-- 08  待审任务表（本地消息表，保证审核消息不丢）
-- -------------------------------------------------------------
DROP TABLE IF EXISTS `audit_task`;
CREATE TABLE `audit_task` (
  `id`          BIGINT      NOT NULL                 COMMENT '任务主键',
  `biz_type`    TINYINT     NOT NULL                 COMMENT '业务类型：1 章节 / 2 评论',
  `biz_id`      BIGINT      NOT NULL                 COMMENT '业务主键',
  `status`      TINYINT     NOT NULL DEFAULT 0       COMMENT '状态：0 待投递 / 1 已投递 / 2 已完成 / 3 死信',
  `retry_count` INT         NOT NULL DEFAULT 0       COMMENT '重试次数',
  `create_time` DATETIME    NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  `update_time` DATETIME    NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
  PRIMARY KEY (`id`),
  KEY `idx_status` (`status`),
  KEY `idx_biz` (`biz_type`, `biz_id`)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COMMENT = '审核任务本地消息表';

SET FOREIGN_KEY_CHECKS = 1;
