-- =============================================================================
-- V26  风控域：审核工单表
-- -----------------------------------------------------------------------------
-- 对应 架构缺口补齐说明「填充业务模块」之 risk（风控 / 审核）。
-- =============================================================================

CREATE TABLE IF NOT EXISTS `moyue_audit_record` (
    `id`         bigint        NOT NULL COMMENT '主键（雪花 ID）',
    `biz_type`   tinyint       DEFAULT NULL COMMENT '业务类型：1 作品 / 2 评论 / 3 封面',
    `biz_id`     varchar(64)   DEFAULT NULL COMMENT '业务 ID（作品 / 评论等主键）',
    `content`    varchar(1024) DEFAULT NULL COMMENT '待审内容摘要',
    `status`     tinyint       NOT NULL DEFAULT 0 COMMENT '状态：0 待审 / 1 通过 / 2 驳回',
    `auditor`    varchar(128)  DEFAULT NULL COMMENT '审核人',
    `reason`     varchar(512)  DEFAULT NULL COMMENT '驳回原因',
    `create_by`  varchar(64)   DEFAULT NULL COMMENT '创建人',
    `create_time` datetime     DEFAULT NULL COMMENT '创建时间',
    `update_by`  varchar(64)   DEFAULT NULL COMMENT '更新人',
    `update_time` datetime     DEFAULT NULL COMMENT '更新时间',
    `remark`     varchar(512)  DEFAULT NULL COMMENT '备注',
    `is_deleted` tinyint(1)    NOT NULL DEFAULT 0 COMMENT '逻辑删除：0 未删 / 1 已删',
    PRIMARY KEY (`id`),
    KEY `idx_audit_status` (`status`, `is_deleted`),
    KEY `idx_audit_biz` (`biz_type`, `biz_id`)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COLLATE = utf8mb4_general_ci COMMENT = '审核工单表';
