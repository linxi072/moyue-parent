-- =============================================================
-- V8  商城周边（moyue-merch）：商品 / 购物车 / 订单
-- 设计取舍：一次结算生成一个 order_no，按购物车行拆单（一行一订单），
--          支付按 order_no 整单幂等支付，避免引入订单明细表的中转复杂度。
-- =============================================================

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
