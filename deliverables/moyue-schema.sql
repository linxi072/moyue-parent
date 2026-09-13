-- =============================================================
--  墨阅小说网 · MySQL 8 建表脚本 (moyue-schema.sql)
--  引擎 InnoDB / 字符集 utf8mb4 / 主键统一雪花 ID (BIGINT)
--  约定：create_time · update_time 全表统一；is_deleted 逻辑删除
--
--  ⚠️ 本文件是「当前状态快照」，内容与 Flyway 迁移 V1–V10 完全对齐（27 张表）。
--     实际部署以 Flyway 为准：各服务启动时自动执行
--     moyue-common/src/main/resources/db/migration 下的迁移。
--     本脚本会 DROP 同名表后重建，**仅用于初始化全新库或本地演示**，
--     切勿对已有数据的库执行。
--
--  同步说明（技术债 16-8 / 16-26）：
--    · 补齐 V3/V4/V5/V6 共 12 张表（原文件仅 8 张）
--    · reward_order 补 is_deleted 列（V1 建表漏建，V6 已修复；
--      实体含 isDeleted 且全局 logic-delete-field 生效，缺列会导致
--      任何查询追加 is_deleted=0 而报 Unknown column）
--    · 补齐 V7/V8/V9 共 7 张表：points_check_in / points_flow（积分获取渠道）、
--      merch_product / merch_cart / merch_order（商城周边）、ai_session / ai_message（AI 客服）
--    · audit_task 补 remark / operator_id 列（V10，审核意见落库 16-20）
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
  `remark`      VARCHAR(255) DEFAULT NULL            COMMENT '审核意见（通过/驳回理由，可空；V10 新增）',
  `operator_id` BIGINT      DEFAULT NULL             COMMENT '审核操作人用户 ID（网关注入，可空；V10 新增）',
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

-- -------------------------------------------------------------
-- 23  points_check_in  每日签到记录表（V7）
-- -------------------------------------------------------------
DROP TABLE IF EXISTS `points_check_in`;
CREATE TABLE `points_check_in` (
  `id`             BIGINT     NOT NULL              COMMENT '主键',
  `user_id`        BIGINT     NOT NULL              COMMENT '用户 ID（主键之一）→ user.id',
  `check_in_date`  DATE       NOT NULL              COMMENT '签到日期 YYYY-MM-DD',
  `points`         INT        NOT NULL DEFAULT 0    COMMENT '本次签到发放积分',
  `is_deleted`     TINYINT(1) NOT NULL DEFAULT 0    COMMENT '逻辑删除',
  `create_time`    DATETIME   NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '签到时间',
  `update_time`    DATETIME   NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_user_date` (`user_id`, `check_in_date`)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COMMENT = '每日签到记录表';

-- -------------------------------------------------------------
-- 24  points_flow  积分流水表（V7，只增不改的台账）
-- biz_type：1 签到 / 2 阅读时长 / 3 评论奖励 / 4 系统发放 / 5 兑换消费
-- points：正数为获得，负数为消费
-- -------------------------------------------------------------
DROP TABLE IF EXISTS `points_flow`;
CREATE TABLE `points_flow` (
  `id`          BIGINT       NOT NULL              COMMENT '主键',
  `user_id`     BIGINT       NOT NULL              COMMENT '用户 ID → user.id',
  `biz_type`    TINYINT      NOT NULL              COMMENT '业务类型：1 签到 / 2 阅读时长 / 3 评论奖励 / 4 系统发放 / 5 兑换消费',
  `points`      INT          NOT NULL              COMMENT '积分变动（正获得 / 负消费）',
  `remark`      VARCHAR(255) DEFAULT NULL          COMMENT '备注',
  `is_deleted`  TINYINT(1)   NOT NULL DEFAULT 0    COMMENT '逻辑删除',
  `create_time` DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '发生时间',
  `update_time` DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
  PRIMARY KEY (`id`),
  KEY `idx_user` (`user_id`)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COMMENT = '积分流水表（只增台账）';

-- -------------------------------------------------------------
-- 25  merch_product  周边商品表（V8）
-- -------------------------------------------------------------
DROP TABLE IF EXISTS `merch_product`;
CREATE TABLE `merch_product` (
  `id`          BIGINT        NOT NULL               COMMENT '商品主键',
  `name`        VARCHAR(128)  NOT NULL               COMMENT '商品名称',
  `description` VARCHAR(500)  DEFAULT NULL           COMMENT '商品描述',
  `image_url`   VARCHAR(255)  DEFAULT NULL           COMMENT '商品图片',
  `price`       DECIMAL(10,2) NOT NULL DEFAULT 0.00  COMMENT '售价（元）',
  `stock`       INT           NOT NULL DEFAULT 0     COMMENT '库存',
  `sales`       INT           NOT NULL DEFAULT 0     COMMENT '累计销量',
  `status`      TINYINT       NOT NULL DEFAULT 1     COMMENT '状态：1 上架 / 2 下架',
  `is_deleted`  TINYINT(1)    NOT NULL DEFAULT 0     COMMENT '逻辑删除',
  `create_time` DATETIME      NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  `update_time` DATETIME      NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
  PRIMARY KEY (`id`),
  KEY `idx_status` (`status`)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COMMENT = '周边商品表';

-- -------------------------------------------------------------
-- 26  merch_cart  周边购物车表（V8）
-- -------------------------------------------------------------
DROP TABLE IF EXISTS `merch_cart`;
CREATE TABLE `merch_cart` (
  `id`          BIGINT     NOT NULL              COMMENT '主键',
  `user_id`     BIGINT     NOT NULL              COMMENT '用户 ID → user.id',
  `product_id`  BIGINT     NOT NULL              COMMENT '商品 ID → merch_product.id',
  `quantity`    INT        NOT NULL DEFAULT 1    COMMENT '数量',
  `is_deleted`  TINYINT(1) NOT NULL DEFAULT 0    COMMENT '逻辑删除',
  `create_time` DATETIME   NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '加入时间',
  `update_time` DATETIME   NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_user_product` (`user_id`, `product_id`),
  KEY `idx_user` (`user_id`)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COMMENT = '周边购物车表';

-- -------------------------------------------------------------
-- 27  merch_order  周边订单表（V8，一行一商品，按 order_no 聚合整单）
-- status：0 待支付 / 1 已支付 / 2 已取消
-- -------------------------------------------------------------
DROP TABLE IF EXISTS `merch_order`;
CREATE TABLE `merch_order` (
  `id`           BIGINT        NOT NULL               COMMENT '订单主键',
  `order_no`     VARCHAR(32)   NOT NULL               COMMENT '订单号（一次结算一个，整单共用）',
  `user_id`      BIGINT        NOT NULL               COMMENT '下单人 → user.id',
  `product_id`   BIGINT        NOT NULL               COMMENT '商品 ID → merch_product.id',
  `product_name` VARCHAR(128)  DEFAULT NULL           COMMENT '商品名称快照',
  `quantity`     INT           NOT NULL DEFAULT 1     COMMENT '购买数量',
  `total_amount` DECIMAL(10,2) NOT NULL DEFAULT 0.00  COMMENT '本行金额（元）= price * quantity',
  `status`       TINYINT       NOT NULL DEFAULT 0     COMMENT '状态：0 待支付 / 1 已支付 / 2 已取消',
  `is_deleted`   TINYINT(1)    NOT NULL DEFAULT 0     COMMENT '逻辑删除',
  `create_time`  DATETIME      NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '下单时间',
  `update_time`  DATETIME      NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
  PRIMARY KEY (`id`),
  KEY `idx_order_no` (`order_no`),
  KEY `idx_user` (`user_id`)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COMMENT = '周边订单表（一行一商品）';

-- -------------------------------------------------------------
-- 28  ai_session  客服会话表（V9）
-- -------------------------------------------------------------
DROP TABLE IF EXISTS `ai_session`;
CREATE TABLE `ai_session` (
  `id`          BIGINT       NOT NULL              COMMENT '会话主键',
  `user_id`     BIGINT       NOT NULL              COMMENT '用户 ID → user.id',
  `title`       VARCHAR(128) DEFAULT NULL           COMMENT '会话标题（默认取首条提问截断）',
  `is_deleted`  TINYINT(1)   NOT NULL DEFAULT 0    COMMENT '逻辑删除',
  `create_time` DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  `update_time` DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
  PRIMARY KEY (`id`),
  KEY `idx_user` (`user_id`)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COMMENT = 'AI 客服会话表';

-- -------------------------------------------------------------
-- 29  ai_message  客服消息表（V9）
-- role：1 用户提问 / 2 助手回复
-- -------------------------------------------------------------
DROP TABLE IF EXISTS `ai_message`;
CREATE TABLE `ai_message` (
  `id`          BIGINT        NOT NULL              COMMENT '消息主键',
  `session_id`  BIGINT        NOT NULL              COMMENT '会话 ID → ai_session.id',
  `role`        TINYINT       NOT NULL DEFAULT 1    COMMENT '角色：1 用户 / 2 助手',
  `content`     VARCHAR(2000) NOT NULL              COMMENT '消息内容',
  `is_deleted`  TINYINT(1)    NOT NULL DEFAULT 0    COMMENT '逻辑删除',
  `create_time` DATETIME      NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '发送时间',
  `update_time` DATETIME      NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
  PRIMARY KEY (`id`),
  KEY `idx_session` (`session_id`)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COMMENT = 'AI 客服消息表';

-- -------------------------------------------------------------
-- 29  notice  站内信 / 通知表（V9）
-- -------------------------------------------------------------
DROP TABLE IF EXISTS `notice`;
CREATE TABLE IF NOT EXISTS `notice` (
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

-- -------------------------------------------------------------
-- 1) reward_order 补齐逻辑删除列
-- -------------------------------------------------------------
ALTER TABLE `reward_order`
    ADD COLUMN `is_deleted` TINYINT(1) NOT NULL DEFAULT 0 COMMENT '逻辑删除' AFTER `pay_time`;

-- -------------------------------------------------------------
-- 2) announcement  运营公告表
-- -------------------------------------------------------------
DROP TABLE IF EXISTS `announcement`;
CREATE TABLE `announcement` (
  `id`           BIGINT       NOT NULL                 COMMENT '公告主键',
  `title`        VARCHAR(128) NOT NULL                 COMMENT '标题',
  `content`      TEXT         NOT NULL                 COMMENT '正文',
  `type`         TINYINT      NOT NULL DEFAULT 1       COMMENT '类型：1 站内公告 / 2 活动 / 3 系统维护',
  `status`       TINYINT      NOT NULL DEFAULT 0       COMMENT '状态：0 草稿 / 1 已发布 / 2 已下线',
  `is_top`       TINYINT(1)   NOT NULL DEFAULT 0       COMMENT '是否置顶：0 否 / 1 是',
  `publish_time` DATETIME     DEFAULT NULL             COMMENT '发布时间',
  `is_deleted`   TINYINT(1)   NOT NULL DEFAULT 0       COMMENT '逻辑删除',
  `create_time`  DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  `update_time`  DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
                                PRIMARY KEY (`id`),
                                KEY `idx_status` (`status`),
                                KEY `idx_top_time` (`is_top`, `publish_time`)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COMMENT = '运营公告表';

-- -------------------------------------------------------------
-- 23  points_check_in  每日签到记录表
-- -------------------------------------------------------------
DROP TABLE IF EXISTS `points_check_in`;
CREATE TABLE `points_check_in` (
  `id`             BIGINT     NOT NULL              COMMENT '主键',
  `user_id`        BIGINT     NOT NULL              COMMENT '用户 ID（主键之一）→ user.id',
  `check_in_date`  DATE       NOT NULL              COMMENT '签到日期 YYYY-MM-DD',
  `points`         INT        NOT NULL DEFAULT 0    COMMENT '本次签到发放积分',
  `is_deleted`     TINYINT(1) NOT NULL DEFAULT 0    COMMENT '逻辑删除',
  `create_time`    DATETIME   NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '签到时间',
  `update_time`    DATETIME   NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
                                   PRIMARY KEY (`id`),
                                   UNIQUE KEY `uk_user_date` (`user_id`, `check_in_date`)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COMMENT = '每日签到记录表';

-- -------------------------------------------------------------
-- 24  points_flow  积分流水表（只增不改的台账）
-- biz_type：1 签到 / 2 阅读时长 / 3 评论奖励 / 4 系统发放 / 5 兑换消费
-- points：正数为获得，负数为消费
-- -------------------------------------------------------------
DROP TABLE IF EXISTS `points_flow`;
CREATE TABLE `points_flow` (
  `id`          BIGINT       NOT NULL              COMMENT '主键',
  `user_id`     BIGINT       NOT NULL              COMMENT '用户 ID → user.id',
  `biz_type`    TINYINT      NOT NULL              COMMENT '业务类型：1 签到 / 2 阅读时长 / 3 评论奖励 / 4 系统发放 / 5 兑换消费',
  `points`      INT          NOT NULL              COMMENT '积分变动（正获得 / 负消费）',
  `remark`      VARCHAR(255) DEFAULT NULL          COMMENT '备注',
  `is_deleted`  TINYINT(1)   NOT NULL DEFAULT 0    COMMENT '逻辑删除',
  `create_time` DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '发生时间',
  `update_time` DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
                               PRIMARY KEY (`id`),
                               KEY `idx_user` (`user_id`)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COMMENT = '积分流水表（只增台账）';

-- -------------------------------------------------------------
-- 25  merch_product  周边商品表
-- -------------------------------------------------------------
DROP TABLE IF EXISTS `merch_product`;
CREATE TABLE `merch_product` (
  `id`          BIGINT        NOT NULL               COMMENT '商品主键',
  `name`        VARCHAR(128)  NOT NULL               COMMENT '商品名称',
  `description` VARCHAR(500)  DEFAULT NULL           COMMENT '商品描述',
  `image_url`   VARCHAR(255)  DEFAULT NULL           COMMENT '商品图片',
  `price`       DECIMAL(10,2) NOT NULL DEFAULT 0.00  COMMENT '售价（元）',
  `stock`       INT           NOT NULL DEFAULT 0     COMMENT '库存',
  `sales`       INT           NOT NULL DEFAULT 0     COMMENT '累计销量',
  `status`      TINYINT       NOT NULL DEFAULT 1     COMMENT '状态：1 上架 / 2 下架',
  `is_deleted`  TINYINT(1)    NOT NULL DEFAULT 0     COMMENT '逻辑删除',
  `create_time` DATETIME      NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  `update_time` DATETIME      NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
                                 PRIMARY KEY (`id`),
                                 KEY `idx_status` (`status`)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COMMENT = '周边商品表';

-- -------------------------------------------------------------
-- 26  merch_cart  周边购物车表
-- -------------------------------------------------------------
DROP TABLE IF EXISTS `merch_cart`;
CREATE TABLE `merch_cart` (
  `id`          BIGINT     NOT NULL              COMMENT '主键',
  `user_id`     BIGINT     NOT NULL              COMMENT '用户 ID → user.id',
  `product_id`  BIGINT     NOT NULL              COMMENT '商品 ID → merch_product.id',
  `quantity`    INT        NOT NULL DEFAULT 1    COMMENT '数量',
  `is_deleted`  TINYINT(1) NOT NULL DEFAULT 0    COMMENT '逻辑删除',
  `create_time` DATETIME   NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '加入时间',
  `update_time` DATETIME   NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
                              PRIMARY KEY (`id`),
                              UNIQUE KEY `uk_user_product` (`user_id`, `product_id`),
                              KEY `idx_user` (`user_id`)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COMMENT = '周边购物车表';

-- -------------------------------------------------------------
-- 27  merch_order  周边订单表（一行一商品，按 order_no 聚合整单）
-- status：0 待支付 / 1 已支付 / 2 已取消
-- -------------------------------------------------------------
DROP TABLE IF EXISTS `merch_order`;
CREATE TABLE `merch_order` (
  `id`           BIGINT        NOT NULL               COMMENT '订单主键',
  `order_no`     VARCHAR(32)   NOT NULL               COMMENT '订单号（一次结算一个，整单共用）',
  `user_id`      BIGINT        NOT NULL               COMMENT '下单人 → user.id',
  `product_id`   BIGINT        NOT NULL               COMMENT '商品 ID → merch_product.id',
  `product_name` VARCHAR(128)  DEFAULT NULL           COMMENT '商品名称快照',
  `quantity`     INT           NOT NULL DEFAULT 1     COMMENT '购买数量',
  `total_amount` DECIMAL(10,2) NOT NULL DEFAULT 0.00  COMMENT '本行金额（元）= price * quantity',
  `status`       TINYINT       NOT NULL DEFAULT 0     COMMENT '状态：0 待支付 / 1 已支付 / 2 已取消',
  `is_deleted`   TINYINT(1)    NOT NULL DEFAULT 0     COMMENT '逻辑删除',
  `create_time`  DATETIME      NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '下单时间',
  `update_time`  DATETIME      NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
                               PRIMARY KEY (`id`),
                               KEY `idx_order_no` (`order_no`),
                               KEY `idx_user` (`user_id`)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COMMENT = '周边订单表（一行一商品）';

-- -------------------------------------------------------------
-- 28  ai_session  客服会话表
-- -------------------------------------------------------------
DROP TABLE IF EXISTS `ai_session`;
CREATE TABLE `ai_session` (
  `id`          BIGINT       NOT NULL              COMMENT '会话主键',
  `user_id`     BIGINT       NOT NULL              COMMENT '用户 ID → user.id',
  `title`       VARCHAR(128) DEFAULT NULL           COMMENT '会话标题（默认取首条提问截断）',
  `is_deleted`  TINYINT(1)   NOT NULL DEFAULT 0    COMMENT '逻辑删除',
  `create_time` DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  `update_time` DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
                              PRIMARY KEY (`id`),
                              KEY `idx_user` (`user_id`)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COMMENT = 'AI 客服会话表';

-- -------------------------------------------------------------
-- 29  ai_message  客服消息表
-- role：1 用户提问 / 2 助手回复
-- -------------------------------------------------------------
DROP TABLE IF EXISTS `ai_message`;
CREATE TABLE `ai_message` (
  `id`          BIGINT        NOT NULL              COMMENT '消息主键',
  `session_id`  BIGINT        NOT NULL              COMMENT '会话 ID → ai_session.id',
  `role`        TINYINT       NOT NULL DEFAULT 1    COMMENT '角色：1 用户 / 2 助手',
  `content`     VARCHAR(2000) NOT NULL              COMMENT '消息内容',
  `is_deleted`  TINYINT(1)    NOT NULL DEFAULT 0    COMMENT '逻辑删除',
  `create_time` DATETIME      NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '发送时间',
  `update_time` DATETIME      NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
                              PRIMARY KEY (`id`),
                              KEY `idx_session` (`session_id`)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COMMENT = 'AI 客服消息表';

-- V10: 审核意见与操作人落库（技术债 16-20）
-- audit_task 通过 / 驳回时记录审核意见（remark）与操作人（operator_id，取网关注入 X-User-Id），
-- 事后可追溯「谁以何理由驳回」。audit_task 为本地消息表，无逻辑删除字段，不做软删。
ALTER TABLE audit_task
    ADD COLUMN remark VARCHAR(255) NULL COMMENT '审核意见（通过/驳回理由，可空）' AFTER retry_count,
    ADD COLUMN operator_id BIGINT NULL COMMENT '审核操作人用户 ID（网关注入，可空）' AFTER remark;
-- V11: 作品完结申请审核流（P1-6）
-- 作者不能自行把作品置为「已完结」，须提交完结申请（status=0 待审核），
-- 由管理员（role=3）裁决：通过 → book.status 置 2（已完结）；驳回 → 记录审核意见。
-- 同一作品同时只允许存在一条在途（status=0）申请，由服务层保证。
CREATE TABLE IF NOT EXISTS `book_finish_apply` (
`id`            BIGINT       NOT NULL                COMMENT '申请主键（雪花 ID）',
`book_id`       BIGINT       NOT NULL                COMMENT '作品 ID → book.id',
`author_id`     BIGINT       NOT NULL                COMMENT '申请人（作者）ID → user.id',
`reason`        VARCHAR(500) DEFAULT NULL            COMMENT '完结申请理由（可空）',
  `status`        TINYINT      NOT NULL DEFAULT 0      COMMENT '审核状态：0 待审核 / 1 通过 / 2 驳回',
  `auditor_id`    BIGINT       DEFAULT NULL            COMMENT '审核人（管理员）ID → user.id',
  `audit_remark`  VARCHAR(255) DEFAULT NULL            COMMENT '审核意见（通过 / 驳回理由，可空）',
  `is_deleted`    TINYINT(1)   NOT NULL DEFAULT 0      COMMENT '逻辑删除：0 否 / 1 是',
  `create_time`   DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  `update_time`   DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
    PRIMARY KEY (`id`),
    KEY `idx_book_status` (`book_id`, `status`),
    KEY `idx_status_create` (`status`, `create_time`)
    ) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COMMENT = '作品完结申请表';

CREATE TABLE IF NOT EXISTS `sys_dept` (
  `id`          BIGINT       NOT NULL                COMMENT '部门主键（雪花 ID）',
  `parent_id`   BIGINT       NOT NULL DEFAULT 0      COMMENT '父部门 ID，0 表示顶级',
  `dept_name`   VARCHAR(50)  NOT NULL                COMMENT '部门名称',
  `order_num`   INT          NOT NULL DEFAULT 0      COMMENT '显示顺序',
  `leader`      VARCHAR(20)  DEFAULT NULL            COMMENT '负责人',
  `phone`       VARCHAR(11)  DEFAULT NULL            COMMENT '联系电话',
  `email`       VARCHAR(64)  DEFAULT NULL            COMMENT '邮箱',
  `status`      TINYINT      NOT NULL DEFAULT 1      COMMENT '状态：0 禁用 / 1 正常',
  `is_deleted`  TINYINT(1)   NOT NULL DEFAULT 0      COMMENT '逻辑删除：0 否 / 1 是',
  `create_time` DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  `update_time` DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
    PRIMARY KEY (`id`),
    KEY `idx_parent` (`parent_id`)
    ) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COMMENT = '系统部门表';

CREATE TABLE IF NOT EXISTS `sys_user` (
  `id`          BIGINT       NOT NULL                COMMENT '用户主键（雪花 ID）',
  `dept_id`     BIGINT       NOT NULL DEFAULT 0      COMMENT '部门 ID → sys_dept.id，0 未分配',
  `username`    VARCHAR(30)  NOT NULL                COMMENT '登录账号',
  `nickname`    VARCHAR(30)  NOT NULL                COMMENT '昵称',
  `password`    VARCHAR(72)  NOT NULL                COMMENT 'BCrypt 密码哈希',
  `email`       VARCHAR(64)  DEFAULT NULL            COMMENT '邮箱',
  `phone`       VARCHAR(11)  DEFAULT NULL            COMMENT '手机号',
  `status`      TINYINT      NOT NULL DEFAULT 1      COMMENT '状态：0 禁用 / 1 正常',
  `is_deleted`  TINYINT(1)   NOT NULL DEFAULT 0      COMMENT '逻辑删除：0 否 / 1 是',
  `create_time` DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  `update_time` DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
    PRIMARY KEY (`id`),
    UNIQUE KEY `uk_username` (`username`),
    KEY `idx_dept` (`dept_id`),
    KEY `idx_status` (`status`)
    ) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COMMENT = '系统用户表（运营后台操作员）';

CREATE TABLE IF NOT EXISTS `sys_role` (
  `id`          BIGINT       NOT NULL                COMMENT '角色主键（雪花 ID）',
  `role_name`   VARCHAR(30)  NOT NULL                COMMENT '角色名称',
  `role_key`    VARCHAR(50)  NOT NULL                COMMENT '角色标识（权限校验用，如 admin）',
  `data_scope`  TINYINT      NOT NULL DEFAULT 1      COMMENT '数据权限：1 全部 / 2 自定义部门 / 3 仅本人',
  `dept_ids`    VARCHAR(255) DEFAULT NULL            COMMENT '自定义数据权限部门 ID，逗号分隔（data_scope=2）',
  `status`      TINYINT      NOT NULL DEFAULT 1      COMMENT '状态：0 禁用 / 1 正常',
  `is_deleted`  TINYINT(1)   NOT NULL DEFAULT 0      COMMENT '逻辑删除：0 否 / 1 是',
  `create_time` DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  `update_time` DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
    PRIMARY KEY (`id`),
    UNIQUE KEY `uk_role_key` (`role_key`),
    KEY `idx_status` (`status`)
    ) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COMMENT = '系统角色表';

CREATE TABLE IF NOT EXISTS `sys_menu` (
  `id`          BIGINT       NOT NULL                COMMENT '菜单主键（雪花 ID）',
  `parent_id`   BIGINT       NOT NULL DEFAULT 0      COMMENT '父菜单 ID，0 表示顶级',
  `menu_name`   VARCHAR(50)  NOT NULL                COMMENT '菜单名称',
  `menu_type`   TINYINT      NOT NULL DEFAULT 1      COMMENT '类型：0 目录 / 1 菜单 / 2 按钮',
  `path`        VARCHAR(128) DEFAULT NULL            COMMENT '路由地址',
  `component`   VARCHAR(128) DEFAULT NULL            COMMENT '前端组件路径',
  `perms`       VARCHAR(128) DEFAULT NULL            COMMENT '权限标识（按钮级，如 system:user:add）',
  `icon`        VARCHAR(64)  DEFAULT NULL            COMMENT '图标',
  `order_num`   INT          NOT NULL DEFAULT 0      COMMENT '显示顺序',
  `status`      TINYINT      NOT NULL DEFAULT 1      COMMENT '状态：0 禁用 / 1 正常',
  `is_deleted`  TINYINT(1)   NOT NULL DEFAULT 0      COMMENT '逻辑删除：0 否 / 1 是',
  `create_time` DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  `update_time` DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
    PRIMARY KEY (`id`),
    KEY `idx_parent` (`parent_id`)
    ) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COMMENT = '系统菜单表（目录/菜单/按钮）';

CREATE TABLE IF NOT EXISTS `sys_user_role` (
  `user_id`     BIGINT       NOT NULL                COMMENT '用户 ID → sys_user.id',
  `role_id`     BIGINT       NOT NULL                COMMENT '角色 ID → sys_role.id',
                                               PRIMARY KEY (`user_id`, `role_id`),
    KEY `idx_role` (`role_id`)
    ) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COMMENT = '用户-角色关联表';

CREATE TABLE IF NOT EXISTS `sys_role_menu` (
  `role_id`     BIGINT       NOT NULL                COMMENT '角色 ID → sys_role.id',
  `menu_id`     BIGINT       NOT NULL                COMMENT '菜单 ID → sys_menu.id',
                                               PRIMARY KEY (`role_id`, `menu_id`),
    KEY `idx_menu` (`menu_id`)
    ) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COMMENT = '角色-菜单关联表';

CREATE TABLE IF NOT EXISTS `sensitive_word` (
  `id`          BIGINT       NOT NULL                COMMENT '主键（雪花 ID）',
  `word`        VARCHAR(64)  NOT NULL                COMMENT '敏感词',
  `level`       TINYINT      NOT NULL DEFAULT 1      COMMENT '等级：1 拦截 / 2 告警（转人工）',
  `category`    VARCHAR(32)  DEFAULT NULL            COMMENT '分类：政治/广告/谩骂/涉黄…',
  `status`      TINYINT      NOT NULL DEFAULT 1      COMMENT '状态：0 停用 / 1 启用',
  `is_deleted`  TINYINT(1)   NOT NULL DEFAULT 0      COMMENT '逻辑删除',
  `create_time` DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP,
  `update_time` DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    PRIMARY KEY (`id`),
    UNIQUE KEY `uk_word` (`word`),
    KEY `idx_status_level` (`status`, `level`)
    ) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='敏感词库';

CREATE TABLE IF NOT EXISTS `report` (
  `id`            BIGINT       NOT NULL              COMMENT '举报主键（雪花 ID）',
  `reporter_id`   BIGINT       NOT NULL              COMMENT '举报人 → user.id',
  `target_type`   TINYINT      NOT NULL              COMMENT '对象：1 书籍 / 2 章节 / 3 评论 / 4 用户',
  `target_id`     BIGINT       NOT NULL              COMMENT '对象主键',
  `reason_type`   TINYINT      NOT NULL DEFAULT 1    COMMENT '原因：1 违规内容 / 2 广告 / 3 侵权 / 4 其他',
  `reason`        VARCHAR(500) DEFAULT NULL          COMMENT '补充说明',
  `status`        TINYINT      NOT NULL DEFAULT 0    COMMENT '状态：0 待处理 / 1 属实 / 2 驳回',
  `handler_id`    BIGINT       DEFAULT NULL          COMMENT '处理人 → 管理员 ID',
  `handle_remark` VARCHAR(255) DEFAULT NULL          COMMENT '处理意见',
  `handle_time`   DATETIME     DEFAULT NULL          COMMENT '处理时间',
  `is_deleted`    TINYINT(1)   NOT NULL DEFAULT 0    COMMENT '逻辑删除',
  `create_time`   DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP,
  `update_time`   DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    PRIMARY KEY (`id`),
    KEY `idx_status` (`status`),
    KEY `idx_target` (`target_type`, `target_id`),
    KEY `idx_reporter` (`reporter_id`)
    ) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='举报表';

CREATE TABLE IF NOT EXISTS `message_template` (
  `id`          BIGINT        NOT NULL,
  `code`        VARCHAR(64)   NOT NULL              COMMENT '模板编码，如 AUDIT_PASS / REPORT_RESULT',
  `name`        VARCHAR(64)   NOT NULL              COMMENT '模板名称',
  `title_tpl`   VARCHAR(255)  DEFAULT NULL          COMMENT '标题模板，占位符 {param}',
  `content_tpl` VARCHAR(1000) NOT NULL              COMMENT '内容模板',
  `channels`    VARCHAR(64)   NOT NULL DEFAULT '1'  COMMENT '默认渠道，逗号分隔：1 站内信/2 邮件/3 短信/4 推送',
  `status`      TINYINT       NOT NULL DEFAULT 1    COMMENT '状态：0 停用 / 1 启用',
  `is_deleted`  TINYINT(1)    NOT NULL DEFAULT 0,
  `create_time` DATETIME      NOT NULL DEFAULT CURRENT_TIMESTAMP,
  `update_time` DATETIME      NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    PRIMARY KEY (`id`),
    UNIQUE KEY `uk_code` (`code`)
    ) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='消息模板表';

CREATE TABLE IF NOT EXISTS `message_channel_record` (
  `id`            BIGINT        NOT NULL,
  `user_id`       BIGINT        NOT NULL            COMMENT '接收用户 → user.id',
  `channel`       TINYINT       NOT NULL            COMMENT '渠道：1 站内信/2 邮件/3 短信/4 推送',
  `template_code` VARCHAR(64)   DEFAULT NULL        COMMENT '模板编码',
  `target`        VARCHAR(128)  DEFAULT NULL        COMMENT '投递地址（邮箱/手机号）',
  `title`         VARCHAR(128)  DEFAULT NULL,
  `content`       VARCHAR(1000) DEFAULT NULL,
  `biz_type`      VARCHAR(64)   DEFAULT NULL        COMMENT '业务类型，如 AUDIT / REPORT',
  `biz_id`        BIGINT        DEFAULT NULL        COMMENT '业务主键',
  `status`        TINYINT       NOT NULL DEFAULT 0  COMMENT '0 待发 / 1 成功 / 2 失败',
  `error_msg`     VARCHAR(255)  DEFAULT NULL,
  `retry_count`   INT           NOT NULL DEFAULT 0,
  `send_time`     DATETIME      DEFAULT NULL,
  `is_deleted`    TINYINT(1)    NOT NULL DEFAULT 0,
  `create_time`   DATETIME      NOT NULL DEFAULT CURRENT_TIMESTAMP,
  `update_time`   DATETIME      NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    PRIMARY KEY (`id`),
    KEY `idx_user` (`user_id`),
    KEY `idx_status` (`status`),
    KEY `idx_template` (`template_code`)
    ) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='消息触达记录表';

CREATE TABLE IF NOT EXISTS `sys_dict_type` (
  `id`          BIGINT       NOT NULL                COMMENT '字典类型主键（雪花 ID）',
  `dict_name`   VARCHAR(50)  NOT NULL                COMMENT '字典名称，如 小说状态',
  `dict_type`   VARCHAR(100) NOT NULL                COMMENT '字典类型编码，如 novel_status',
  `status`      TINYINT      NOT NULL DEFAULT 1      COMMENT '状态：0 停用 / 1 启用',
  `remark`      VARCHAR(255) DEFAULT NULL            COMMENT '备注',
  `is_deleted`  TINYINT(1)   NOT NULL DEFAULT 0      COMMENT '逻辑删除：0 否 / 1 是',
  `create_time` DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  `update_time` DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
    PRIMARY KEY (`id`),
    UNIQUE KEY `uk_dict_type` (`dict_type`)
    ) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COMMENT = '字典类型表';

CREATE TABLE IF NOT EXISTS `sys_dict_data` (
  `id`          BIGINT       NOT NULL                COMMENT '字典数据主键（雪花 ID）',
  `dict_type`   VARCHAR(100) NOT NULL                COMMENT '所属字典类型编码 → sys_dict_type.dict_type',
  `dict_label`  VARCHAR(100) NOT NULL                COMMENT '字典标签（展示用）',
  `dict_value`  VARCHAR(100) NOT NULL                COMMENT '字典键值（存储用）',
  `dict_sort`   INT          NOT NULL DEFAULT 0      COMMENT '显示顺序',
  `is_default`  TINYINT      NOT NULL DEFAULT 0      COMMENT '是否默认：0 否 / 1 是',
  `status`      TINYINT      NOT NULL DEFAULT 1      COMMENT '状态：0 停用 / 1 启用',
  `remark`      VARCHAR(255) DEFAULT NULL            COMMENT '备注',
  `create_time` DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  `update_time` DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
    PRIMARY KEY (`id`),
    KEY `idx_dict_type` (`dict_type`)
    ) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COMMENT = '字典数据表';

CREATE TABLE IF NOT EXISTS `sys_config` (
  `id`           BIGINT       NOT NULL               COMMENT '参数主键（雪花 ID）',
  `config_name`  VARCHAR(100) NOT NULL               COMMENT '参数名称，如 默认章节字数',
  `config_key`   VARCHAR(100) NOT NULL               COMMENT '参数键名，如 system.chapter.word-count',
  `config_value` VARCHAR(500) DEFAULT NULL           COMMENT '参数键值',
  `is_system`    TINYINT      NOT NULL DEFAULT 0     COMMENT '是否内置参数：0 否 / 1 是（内置不可删除）',
  `remark`       VARCHAR(255) DEFAULT NULL           COMMENT '备注',
  `create_time`  DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  `update_time`  DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
    PRIMARY KEY (`id`),
    UNIQUE KEY `uk_config_key` (`config_key`)
    ) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COMMENT = '系统参数配置表';

CREATE TABLE IF NOT EXISTS `sys_oper_log` (
  `id`            BIGINT      NOT NULL               COMMENT '日志主键（雪花 ID）',
  `module`        VARCHAR(50) DEFAULT NULL           COMMENT '业务模块，如 字典管理',
  `business_type` TINYINT     NOT NULL DEFAULT 0     COMMENT '业务类型：0 其它 / 1 新增 / 2 修改 / 3 删除 / 4 导出 / 5 强退 / 6 生成代码',
  `request_method` VARCHAR(10) DEFAULT NULL          COMMENT 'HTTP 请求方式：GET/POST/PUT/DELETE',
  `url`           VARCHAR(255) DEFAULT NULL          COMMENT '请求 URL',
  `operator_id`   BIGINT      DEFAULT NULL           COMMENT '操作人员 ID → sys_user.id',
  `operator_name` VARCHAR(50) DEFAULT NULL           COMMENT '操作人员名称',
  `ip`            VARCHAR(64) DEFAULT NULL           COMMENT '操作 IP',
  `param`         TEXT        DEFAULT NULL           COMMENT '请求参数（截断 2000 字符）',
  `result`        TEXT        DEFAULT NULL           COMMENT '返回结果（截断 2000 字符）',
  `status`        TINYINT     NOT NULL DEFAULT 0     COMMENT '操作状态：0 成功 / 1 失败',
  `error_msg`     TEXT        DEFAULT NULL           COMMENT '错误信息',
  `cost_ms`       INT         DEFAULT NULL           COMMENT '耗时（毫秒）',
  `oper_time`     DATETIME    DEFAULT NULL           COMMENT '操作时间',
    PRIMARY KEY (`id`),
    KEY `idx_operator` (`operator_id`),
    KEY `idx_module` (`module`),
    KEY `idx_oper_time` (`oper_time`)
    ) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COMMENT = '操作日志表';

CREATE TABLE IF NOT EXISTS `sys_logininfor` (
  `id`          BIGINT      NOT NULL                COMMENT '日志主键（雪花 ID）',
  `username`    VARCHAR(50) DEFAULT NULL           COMMENT '登录账号',
  `ip`          VARCHAR(64) DEFAULT NULL           COMMENT '登录 IP',
  `user_agent`  VARCHAR(255) DEFAULT NULL          COMMENT '浏览器 UA',
  `status`      TINYINT     NOT NULL DEFAULT 0     COMMENT '登录状态：0 成功 / 1 失败',
  `msg`         VARCHAR(255) DEFAULT NULL          COMMENT '提示信息，如 登录成功 / 账号或密码错误',
  `login_time`  DATETIME    DEFAULT NULL           COMMENT '登录时间',
    PRIMARY KEY (`id`),
    KEY `idx_username` (`username`),
    KEY `idx_login_time` (`login_time`)
    ) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COMMENT = '系统登录日志表';

SET FOREIGN_KEY_CHECKS = 1;

-- =============================================================
--  附：Flyway 迁移对照（以代码为准）
--    V1  user/book/chapter/comment/reward_order/bookshelf/author_income/audit_task
--    V2  书籍种子数据
--    V3  notice
--    V4  chat_* / points_* / blog_*
--    V5  comment_like
--    V6  announcement + reward_order 补 is_deleted
--    V7  points_check_in / points_flow（积分获取渠道）
--    V8  merch_product / merch_cart / merch_order（商城周边）
--    V9  ai_session / ai_message（AI 客服）
--    V10 audit_task 补 remark / operator_id（审核意见落库）
-- =============================================================
