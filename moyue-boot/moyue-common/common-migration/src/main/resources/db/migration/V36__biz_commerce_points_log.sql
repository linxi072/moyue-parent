-- =============================================================================
-- V36  商业化域：积分流水表
-- -----------------------------------------------------------------------------
-- 每笔积分变动（充值 / 打赏 / 消费 / 退款）均落一条流水，余额变动可追溯。
-- =============================================================================

CREATE TABLE IF NOT EXISTS `moyue_points_log` (
    `id`            bigint        NOT NULL COMMENT '主键（雪花 ID）',
    `user_id`       bigint        NOT NULL COMMENT '用户 ID',
    `biz_type`      tinyint       NOT NULL DEFAULT 1 COMMENT '业务类型：1 充值 / 2 打赏 / 3 消费 / 4 退款',
    `change_amount` decimal(12,2) NOT NULL DEFAULT 0.00 COMMENT '变动积分（正数增加 / 负数扣减）',
    `balance_after` decimal(12,2) NOT NULL DEFAULT 0.00 COMMENT '变动后账户余额',
    `ref_id`        varchar(64)   DEFAULT NULL COMMENT '关联业务单号（订单号 / 兑换单号 / ADMIN 调整号）',
    `remark`        varchar(512)  DEFAULT NULL COMMENT '备注',
    `create_by`     varchar(64)   DEFAULT NULL COMMENT '创建人',
    `create_time`   datetime      DEFAULT NULL COMMENT '创建时间',
    `update_by`     varchar(64)   DEFAULT NULL COMMENT '更新人',
    `update_time`   datetime      DEFAULT NULL COMMENT '更新时间',
    `is_deleted`    tinyint(1)    NOT NULL DEFAULT 0 COMMENT '逻辑删除：0 未删 / 1 已删',
    PRIMARY KEY (`id`),
    KEY `idx_points_log_user` (`user_id`),
    KEY `idx_points_log_ref` (`ref_id`),
    KEY `idx_points_log_biz` (`biz_type`)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COLLATE = utf8mb4_general_ci COMMENT = '积分流水表';
