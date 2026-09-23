-- V23：P2-B 订阅 / 会员体系（会员套餐 member_tier + 订阅记录 member_subscription）
-- 逻辑删除逐实体显式 @TableLogic(value="0", delval="1")（全局未启用，不写会静默物理删）；
-- 两表均含 is_deleted TINYINT(1) NOT NULL DEFAULT 0。
-- 幂等：CREATE TABLE IF NOT EXISTS；改完重跑 python tools/gen-h2-schema.py（H2 索引名加表前缀全局唯一）。

CREATE TABLE IF NOT EXISTS `member_tier` (
  `id`            BIGINT        NOT NULL                COMMENT '主键（雪花 ID）',
  `tier_code`     VARCHAR(32)   NOT NULL                COMMENT '套餐编码（全局唯一，如 MONTHLY_BASIC）',
  `tier_name`     VARCHAR(64)   NOT NULL                COMMENT '套餐名称',
  `monthly_price` DECIMAL(10,2) NOT NULL DEFAULT 0.00   COMMENT '包月价格（元）',
  `duration_days` INT           NOT NULL DEFAULT 30     COMMENT '有效期天数',
  `ad_free`       TINYINT(1)    NOT NULL DEFAULT 0      COMMENT '免广告权益：0 否 / 1 是',
  `discount_rate` DECIMAL(4,2)  NOT NULL DEFAULT 1.00   COMMENT '折扣率（1.00 无折扣，0.90=9 折）',
  `badge`         VARCHAR(64)   DEFAULT NULL            COMMENT '专属徽章名',
  `sort`          INT           NOT NULL DEFAULT 0      COMMENT '展示排序',
  `is_deleted`    TINYINT(1)    NOT NULL DEFAULT 0      COMMENT '逻辑删除：0 否 / 1 是',
  `create_time`   DATETIME      NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  `update_time`   DATETIME      NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_member_tier_code` (`tier_code`),
  KEY `idx_member_tier_sort` (`sort`)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COMMENT = '会员套餐配置表';

CREATE TABLE IF NOT EXISTS `member_subscription` (
  `id`          BIGINT       NOT NULL                COMMENT '主键（雪花 ID）',
  `user_id`     BIGINT       NOT NULL                COMMENT '订阅用户 ID → user.id',
  `tier_code`   VARCHAR(32)  NOT NULL                COMMENT '套餐编码 → member_tier.tier_code',
  `tier_name`   VARCHAR(64)  DEFAULT NULL            COMMENT '反规范化快照：套餐名称',
  `status`      TINYINT      NOT NULL DEFAULT 0      COMMENT '订阅状态：0 待支付 / 1 生效中 / 2 已过期 / 3 已取消',
  `start_time`  DATETIME     DEFAULT NULL            COMMENT '生效开始时间',
  `end_time`    DATETIME     DEFAULT NULL            COMMENT '生效结束时间（到期依据）',
  `order_no`    VARCHAR(64)  DEFAULT NULL            COMMENT '订阅订单号',
  `pay_serial`  VARCHAR(64)  DEFAULT NULL            COMMENT '支付渠道流水号',
  `channel`     VARCHAR(32)  DEFAULT NULL            COMMENT '支付渠道标识',
  `is_deleted`  TINYINT(1)   NOT NULL DEFAULT 0      COMMENT '逻辑删除：0 否 / 1 是',
  `create_time` DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  `update_time` DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
  PRIMARY KEY (`id`),
  KEY `idx_member_sub_user` (`user_id`),
  KEY `idx_member_sub_status` (`status`),
  KEY `idx_member_sub_end` (`end_time`)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COMMENT = '会员订阅记录表';
