-- =============================================================
--  墨阅小说网 · MySQL 8 建表脚本 (moyue-schema.sql)
--  引擎 InnoDB / 字符集 utf8mb4 / 主键统一雪花 ID (BIGINT)
--  约定：create_time · update_time 全表统一；is_deleted 逻辑删除
--
--  ⚠️ 本文件是「当前状态快照」，内容与 Flyway 迁移 V1–V6 完全对齐（20 张表）。
--     实际部署以 Flyway 为准：各服务启动时自动执行
--     moyue-common/src/main/resources/db/migration 下的迁移。
--     本脚本会 DROP 同名表后重建，**仅用于初始化全新库或本地演示**，
--     切勿对已有数据的库执行。
--
--  同步说明（技术债 16-8）：
--    · 补齐 V3/V4/V5/V6 共 12 张表（原文件仅 8 张）
--    · reward_order 补 is_deleted 列（V1 建表漏建，V6 已修复；
--      实体含 isDeleted 且全局 logic-delete-field 生效，缺列会导致
--      任何查询追加 is_deleted=0 而报 Unknown column）
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
-- 05  comment_like  评论点赞表（V5，防重复点赞）
-- -------------------------------------------------------------
DROP TABLE IF EXISTS `comment_like`;
CREATE TABLE `comment_like` (
  `id`          BIGINT       NOT NULL                COMMENT '主键',
  `comment_id`  BIGINT       NOT NULL                COMMENT '评论 ID → comment.id',
  `user_id`     BIGINT       NOT NULL                COMMENT '点赞人 → user.id',
  `is_deleted`  TINYINT(1)   NOT NULL DEFAULT 0      COMMENT '逻辑删除',
  `create_time` DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  `update_time` DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_comment_user` (`comment_id`, `user_id`),
  KEY `idx_comment` (`comment_id`)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COMMENT = '评论点赞表';

-- -------------------------------------------------------------
-- 06  reward_order  打赏订单表
--     注：is_deleted 为 V6 补齐，V1 建表时漏建
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
  `is_deleted`  TINYINT(1)    NOT NULL DEFAULT 0     COMMENT '逻辑删除',
  `create_time` DATETIME      NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  `update_time` DATETIME      NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_order_no` (`order_no`),
  KEY `idx_user` (`user_id`),
  KEY `idx_book` (`book_id`)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COMMENT = '打赏订单表';

-- -------------------------------------------------------------
-- 07  bookshelf  书架表
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
-- 08  author_income  稿酬流水表（作者收入，按月结算）
--     注：本表无 is_deleted 列（结算流水不物理/逻辑删除）
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
-- 09  audit_task  待审任务表（本地消息表，保证审核消息不丢）
--     注：本表无 is_deleted 列
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

-- -------------------------------------------------------------
-- 10  announcement  运营公告表（V6）
-- -------------------------------------------------------------
DROP TABLE IF EXISTS `announcement`;
CREATE TABLE `announcement` (
  `id`           BIGINT       NOT NULL               COMMENT '公告主键',
  `title`        VARCHAR(128) NOT NULL               COMMENT '标题',
  `content`      TEXT         NOT NULL               COMMENT '正文',
  `type`         TINYINT      NOT NULL DEFAULT 1     COMMENT '类型：1 站内公告 / 2 活动 / 3 系统维护',
  `status`       TINYINT      NOT NULL DEFAULT 0     COMMENT '状态：0 草稿 / 1 已发布 / 2 已下线',
  `is_top`       TINYINT(1)   NOT NULL DEFAULT 0     COMMENT '是否置顶：0 否 / 1 是',
  `publish_time` DATETIME     DEFAULT NULL           COMMENT '发布时间',
  `is_deleted`   TINYINT(1)   NOT NULL DEFAULT 0     COMMENT '逻辑删除',
  `create_time`  DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  `update_time`  DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
  PRIMARY KEY (`id`),
  KEY `idx_status` (`status`),
  KEY `idx_top_time` (`is_top`, `publish_time`)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COMMENT = '运营公告表';

-- -------------------------------------------------------------
-- 11  notice  站内信/通知表（V3）
-- -------------------------------------------------------------
DROP TABLE IF EXISTS `notice`;
CREATE TABLE `notice` (
  `id`          BIGINT       NOT NULL                COMMENT '通知主键',
  `user_id`     BIGINT       NOT NULL                COMMENT '接收用户 → user.id',
  `title`       VARCHAR(128) NOT NULL                COMMENT '标题',
  `content`     VARCHAR(500) DEFAULT NULL            COMMENT '内容',
  `type`        TINYINT      NOT NULL DEFAULT 1      COMMENT '类型：1 系统 / 2 互动 / 3 审核',
  `is_read`     TINYINT(1)   NOT NULL DEFAULT 0      COMMENT '是否已读',
  `create_time` DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  PRIMARY KEY (`id`),
  KEY `idx_user` (`user_id`)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COMMENT = '站内信/通知表';

-- -------------------------------------------------------------
-- 12  chat_conversation  聊天会话表（V4）
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
-- 13  chat_conversation_member  会话成员表（V4）
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
-- 14  chat_message  聊天消息表（V4）
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
-- 15  points_account  积分账户表（V4，每用户一行）
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
-- 16  points_product  积分商品表（V4）
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
-- 17  points_order  积分兑换订单表（V4）
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
-- 18  blog_post  博客文章表（V4）
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
-- 19  blog_comment  博客评论表（V4）
-- -------------------------------------------------------------
DROP TABLE IF EXISTS `blog_comment`;
CREATE TABLE `blog_comment` (
  `id`          BIGINT        NOT NULL               COMMENT '评论主键',
  `post_id`     BIGINT        NOT NULL               COMMENT '文章 ID → blog_post.id',
  `user_id`     BIGINT        NOT NULL               COMMENT '评论人 → user.id',
  `content`     VARCHAR(1000) NOT NULL               COMMENT '评论内容',
  `like_count`  INT           NOT NULL DEFAULT 0     COMMENT '点赞数',
  `is_deleted`  TINYINT(1)    NOT NULL DEFAULT 0     COMMENT '逻辑删除',
  `create_time` DATETIME      NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  `update_time` DATETIME      NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
  PRIMARY KEY (`id`),
  KEY `idx_post` (`post_id`),
  KEY `idx_user` (`user_id`)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COMMENT = '博客评论表';

-- -------------------------------------------------------------
-- 20  blog_like  博客点赞表（V4，防重复点赞）
-- -------------------------------------------------------------
DROP TABLE IF EXISTS `blog_like`;
CREATE TABLE `blog_like` (
  `id`          BIGINT       NOT NULL                COMMENT '主键',
  `post_id`     BIGINT       NOT NULL                COMMENT '文章 ID → blog_post.id',
  `user_id`     BIGINT       NOT NULL                COMMENT '点赞人 → user.id',
  `is_deleted`  TINYINT(1)   NOT NULL DEFAULT 0      COMMENT '逻辑删除',
  `create_time` DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  `update_time` DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_post_user` (`post_id`, `user_id`),
  KEY `idx_post` (`post_id`)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COMMENT = '博客点赞表';

SET FOREIGN_KEY_CHECKS = 1;

-- =============================================================
--  附：Flyway 迁移对照（以代码为准）
--    V1  user/book/chapter/comment/reward_order/bookshelf/author_income/audit_task
--    V2  书籍种子数据
--    V3  notice
--    V4  chat_* / points_* / blog_*
--    V5  comment_like
--    V6  announcement + reward_order 补 is_deleted
-- =============================================================
