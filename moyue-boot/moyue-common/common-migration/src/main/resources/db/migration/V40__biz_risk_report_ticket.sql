-- =============================================================================
-- V40  风控域：举报工单表
-- -----------------------------------------------------------------------------
-- 对应「7 个业务管理模块 · M4 risk」：用户举报作品 / 评论 / 用户 / 帖子后，
-- 运营在风控台提交、列表查看并流转处理（0 待处理 / 1 已处理 / 2 驳回）。
-- 审核结果由运营在对应管理端手动处置（本期不反向调 content/social，避免 Feign 链）。
-- =============================================================================

CREATE TABLE IF NOT EXISTS `moyue_report_ticket` (
    `id`          bigint        NOT NULL COMMENT '主键（雪花 ID）',
    `biz_type`    tinyint       NOT NULL DEFAULT 1 COMMENT '业务类型：1 作品 / 2 评论 / 3 用户 / 4 帖子',
    `biz_id`      varchar(64)   NOT NULL DEFAULT '' COMMENT '被举报对象业务 ID',
    `reporter_id` bigint        NOT NULL DEFAULT 0 COMMENT '举报人用户 ID',
    `reason`      varchar(512)  DEFAULT NULL COMMENT '举报原因',
    `content`     varchar(1024) DEFAULT NULL COMMENT '被举报内容摘要（快照）',
    `status`      tinyint       NOT NULL DEFAULT 0 COMMENT '状态：0 待处理 / 1 已处理 / 2 驳回',
    `handler`     varchar(64)   DEFAULT NULL COMMENT '处理人',
    `handle_reason` varchar(512) DEFAULT NULL COMMENT '处理说明',
    `create_by`   varchar(64)   DEFAULT NULL COMMENT '创建人',
    `create_time` datetime      DEFAULT NULL COMMENT '创建时间',
    `update_by`   varchar(64)   DEFAULT NULL COMMENT '更新人',
    `update_time` datetime      DEFAULT NULL COMMENT '更新时间',
    `remark`      varchar(512)  DEFAULT NULL COMMENT '备注',
    `is_deleted`  tinyint(1)    NOT NULL DEFAULT 0 COMMENT '逻辑删除：0 未删 / 1 已删',
    PRIMARY KEY (`id`),
    KEY `idx_report_biz` (`biz_type`, `biz_id`),
    KEY `idx_report_status` (`status`)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COLLATE = utf8mb4_general_ci COMMENT = '举报工单表';
