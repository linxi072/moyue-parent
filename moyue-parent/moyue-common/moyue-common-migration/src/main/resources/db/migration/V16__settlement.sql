-- V16：稿酬结算与打款闭环（P0-1）
-- 1. settlement_order：结算单主表（状态机 0待结算/1已结算待打款/2已打款/3打款失败）
-- 2. author_income 扩展 settlement_id：锁定已结算流水，防止重复结算
-- 风格对齐 V15：反引号表名、MySQL 语法、COMMENT、雪花 ID 由应用侧 ASSIGN_ID 生成、InnoDB utf8mb4。

CREATE TABLE `settlement_order` (
    `id`            BIGINT        NOT NULL COMMENT '结算单ID（雪花ID）',
    `author_id`     BIGINT        NOT NULL COMMENT '作者ID',
    `period`        VARCHAR(20)   NOT NULL COMMENT '结算月份 YYYY-MM',
    `total_amount`  DECIMAL(12,2) NOT NULL DEFAULT 0 COMMENT '结算总额（元）',
    `status`        TINYINT       NOT NULL DEFAULT 0 COMMENT '0待结算/1已结算待打款/2已打款/3打款失败',
    `pay_channel`   TINYINT       NULL     COMMENT '打款渠道 1微信/2支付宝',
    `pay_serial`    VARCHAR(64)   NULL     COMMENT '渠道流水号（幂等键）',
    `remark`        VARCHAR(255)  NULL     COMMENT '审核/打款备注',
    `operator_id`   BIGINT        NULL     COMMENT '审核/打款操作人',
    `create_time`   DATETIME      NULL     COMMENT '创建时间',
    `update_time`   DATETIME      NULL     COMMENT '更新时间',
    `is_deleted`    TINYINT       NOT NULL DEFAULT 0 COMMENT '逻辑删除 0否/1是',
    PRIMARY KEY (`id`),
    KEY `idx_settlement_author_period` (`author_id`, `period`),
    KEY `idx_settlement_status` (`status`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='稿酬结算单';

ALTER TABLE `author_income`
    ADD COLUMN `settlement_id` BIGINT NULL COMMENT '结算单ID（锁定已结算流水）' AFTER `settle_month`;
