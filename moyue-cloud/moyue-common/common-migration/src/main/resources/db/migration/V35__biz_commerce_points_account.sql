-- =============================================================================
-- V35  商业化域：用户积分账户表
-- -----------------------------------------------------------------------------
-- 对应「7 个业务管理模块 · M1 commerce」：积分账户作为打赏 / 兑换 / 充值
-- 的唯一余额来源，余额不足时业务直接拒绝（见 PointsService.changePoints）。
-- =============================================================================

CREATE TABLE IF NOT EXISTS `moyue_points_account` (
    `id`           bigint        NOT NULL COMMENT '主键（雪花 ID）',
    `user_id`      bigint        NOT NULL COMMENT '用户 ID',
    `balance`      decimal(12,2) NOT NULL DEFAULT 0.00 COMMENT '可用积分',
    `total_income` decimal(12,2) NOT NULL DEFAULT 0.00 COMMENT '累计获得积分',
    `total_consume` decimal(12,2) NOT NULL DEFAULT 0.00 COMMENT '累计消费积分',
    `frozen`       decimal(12,2) NOT NULL DEFAULT 0.00 COMMENT '冻结积分',
    `create_by`    varchar(64)   DEFAULT NULL COMMENT '创建人',
    `create_time`  datetime      DEFAULT NULL COMMENT '创建时间',
    `update_by`    varchar(64)   DEFAULT NULL COMMENT '更新人',
    `update_time`  datetime      DEFAULT NULL COMMENT '更新时间',
    `remark`       varchar(512)  DEFAULT NULL COMMENT '备注',
    `is_deleted`   tinyint(1)    NOT NULL DEFAULT 0 COMMENT '逻辑删除：0 未删 / 1 已删',
    PRIMARY KEY (`id`),
    UNIQUE KEY `uk_points_user` (`user_id`),
    KEY `idx_points_balance` (`balance`)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COLLATE = utf8mb4_general_ci COMMENT = '用户积分账户表';
