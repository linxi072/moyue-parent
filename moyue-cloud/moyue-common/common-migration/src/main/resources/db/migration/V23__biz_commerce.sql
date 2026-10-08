-- =============================================================================
-- V23  商业化域：付费订单表
-- -----------------------------------------------------------------------------
-- 对应 架构缺口补齐说明「填充业务模块」之 commerce（书币 / 会员 / 打赏）。
-- =============================================================================

CREATE TABLE IF NOT EXISTS `moyue_pay_order` (
    `id`           bigint        NOT NULL COMMENT '订单主键（雪花 ID）',
    `order_no`     varchar(64)   NOT NULL DEFAULT '' COMMENT '订单号，唯一索引',
    `user_id`      bigint        DEFAULT NULL COMMENT '用户 ID',
    `product_name` varchar(256)  NOT NULL DEFAULT '' COMMENT '商品名称',
    `product_type` tinyint       NOT NULL DEFAULT 1 COMMENT '商品类型：1 书币 / 2 会员 / 3 打赏',
    `amount`       decimal(10,2) NOT NULL DEFAULT 0.00 COMMENT '金额（元）',
    `quantity`     int           NOT NULL DEFAULT 1 COMMENT '数量',
    `pay_channel`  tinyint       DEFAULT NULL COMMENT '支付渠道：1 微信 / 2 支付宝 / 3 余额',
    `status`       tinyint       NOT NULL DEFAULT 0 COMMENT '状态：0 待付 / 1 已付 / 2 退款 / 3 关闭',
    `trade_no`     varchar(128)  DEFAULT NULL COMMENT '第三方交易号',
    `create_by`    varchar(64)   DEFAULT NULL COMMENT '创建人',
    `create_time`  datetime      DEFAULT NULL COMMENT '创建时间',
    `update_by`    varchar(64)   DEFAULT NULL COMMENT '更新人',
    `update_time`  datetime      DEFAULT NULL COMMENT '更新时间',
    `remark`       varchar(512)  DEFAULT NULL COMMENT '备注',
    `is_deleted`   tinyint(1)    NOT NULL DEFAULT 0 COMMENT '逻辑删除：0 未删 / 1 已删',
    PRIMARY KEY (`id`),
    UNIQUE KEY `uk_order_no` (`order_no`),
    KEY `idx_order_user` (`user_id`),
    KEY `idx_order_status` (`status`, `is_deleted`)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COLLATE = utf8mb4_general_ci COMMENT = '付费订单表';
