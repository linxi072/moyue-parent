-- =============================================================================
-- V37  商业化域：积分兑换商品表
-- -----------------------------------------------------------------------------
-- 运营上架的书币 / 会员等兑换商品；兑换由 ProductService.exchange 驱动：
-- 校验上架 + 扣积分（事务）+ 减库存 + 落支付订单。
-- =============================================================================

CREATE TABLE IF NOT EXISTS `moyue_product` (
    `id`           bigint        NOT NULL COMMENT '主键（雪花 ID）',
    `name`         varchar(128)  NOT NULL DEFAULT '' COMMENT '商品名称',
    `type`         tinyint       NOT NULL DEFAULT 1 COMMENT '类型：1 书币 / 2 会员',
    `price_amount` decimal(10,2) NOT NULL DEFAULT 0.00 COMMENT '售价（元）',
    `points`       int           NOT NULL DEFAULT 0 COMMENT '兑换所需积分',
    `stock`        int           NOT NULL DEFAULT 0 COMMENT '库存',
    `status`       tinyint       NOT NULL DEFAULT 0 COMMENT '状态：0 下架 / 1 上架',
    `sort`         int           NOT NULL DEFAULT 0 COMMENT '排序（越大越靠前）',
    `create_by`    varchar(64)   DEFAULT NULL COMMENT '创建人',
    `create_time`  datetime      DEFAULT NULL COMMENT '创建时间',
    `update_by`    varchar(64)   DEFAULT NULL COMMENT '更新人',
    `update_time`  datetime      DEFAULT NULL COMMENT '更新时间',
    `remark`       varchar(512)  DEFAULT NULL COMMENT '备注',
    `is_deleted`   tinyint(1)    NOT NULL DEFAULT 0 COMMENT '逻辑删除：0 未删 / 1 已删',
    PRIMARY KEY (`id`),
    KEY `idx_product_status_sort` (`status`, `sort`)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COLLATE = utf8mb4_general_ci COMMENT = '积分兑换商品表';
