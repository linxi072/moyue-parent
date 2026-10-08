-- =============================================================================
-- V27  AI 域：AI 任务表
-- -----------------------------------------------------------------------------
-- 对应 架构缺口补齐说明「填充业务模块」之 ai（续写 / 润色 / 摘要 / 大纲）。
-- =============================================================================

CREATE TABLE IF NOT EXISTS `moyue_ai_task` (
    `id`          bigint        NOT NULL COMMENT '主键（雪花 ID）',
    `task_type`   tinyint       DEFAULT NULL COMMENT '任务类型：1 续写 / 2 润色 / 3 摘要 / 4 大纲',
    `prompt`      varchar(2048) DEFAULT NULL COMMENT '提示词',
    `model`       varchar(128)  DEFAULT NULL COMMENT '模型标识',
    `status`      tinyint       NOT NULL DEFAULT 0 COMMENT '状态：0 待处理 / 1 成功 / 2 失败',
    `result`      varchar(4096) DEFAULT NULL COMMENT '生成结果',
    `cost_tokens` int           DEFAULT NULL COMMENT '消耗 token 数',
    `user_id`     bigint        DEFAULT NULL COMMENT '发起用户',
    `create_by`   varchar(64)   DEFAULT NULL COMMENT '创建人',
    `create_time` datetime      DEFAULT NULL COMMENT '创建时间',
    `update_by`   varchar(64)   DEFAULT NULL COMMENT '更新人',
    `update_time` datetime      DEFAULT NULL COMMENT '更新时间',
    `remark`      varchar(512)  DEFAULT NULL COMMENT '备注',
    `is_deleted`  tinyint(1)    NOT NULL DEFAULT 0 COMMENT '逻辑删除：0 未删 / 1 已删',
    PRIMARY KEY (`id`),
    KEY `idx_ai_status` (`status`, `is_deleted`),
    KEY `idx_ai_user` (`user_id`)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COLLATE = utf8mb4_general_ci COMMENT = 'AI 任务表';
