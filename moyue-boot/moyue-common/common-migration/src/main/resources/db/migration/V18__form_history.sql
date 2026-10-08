-- =============================================================================
-- V18  表单 Schema 历史版本表（缺口 G-8）
-- -----------------------------------------------------------------------------
-- 背景：架构说明书 7.6 ⑯ 在线构建器列有
--       GET  /forms/{formId}/history                      历史版本列表
--       POST /forms/{formId}/history/{versionId}/rollback 回滚到指定版本
--   但 6.3 表清单中只给了 sys_form.version（一个整数），无法承载「版本快照内容」，
--   回滚与历史列表无从落地。故补 sys_form_history 一张表。
--
-- 设计取舍：快照内容以 JSON 整包存储（config + items），而非再做一张
--   sys_form_item_history 明细表。理由：回滚是「整包替换」语义，JSON 快照最简单
--   且天然与 sys_form_item 结构演进解耦；代价是历史版本不支持按字段检索 —— 本期
--   不需要该能力（架构说明书 ⑯ 范围说明明确排除联动规则等高级能力）。
-- =============================================================================

CREATE TABLE IF NOT EXISTS `sys_form_history` (
    `id`           bigint       NOT NULL COMMENT '历史版本主键（雪花 ID），回滚时作为 versionId 传入',
    `form_id`      bigint       NOT NULL COMMENT '归属表单 ID → sys_form.id',
    `version`      int          NOT NULL COMMENT '快照对应的表单版本号（发布时的 sys_form.version）',
    `schema_json`  longtext COMMENT 'Schema 整包快照（JSON：config + items），回滚时整体写回',
    `publish_by`   varchar(64)  DEFAULT NULL COMMENT '发布人账号（快照）',
    `publish_time` datetime     DEFAULT NULL COMMENT '发布时间',
    `remark`       varchar(512) DEFAULT NULL COMMENT '备注，回滚自动留档时会写入说明',
    `create_by`    varchar(64)  DEFAULT NULL COMMENT '创建人',
    `create_time`  datetime     DEFAULT NULL COMMENT '创建时间',
    `update_by`    varchar(64)  DEFAULT NULL COMMENT '更新人',
    `update_time`  datetime     DEFAULT NULL COMMENT '更新时间',
    `is_deleted`   tinyint(1)   NOT NULL DEFAULT 0 COMMENT '逻辑删除：0 未删除 / 1 已删除',
    PRIMARY KEY (`id`),
    KEY `idx_fh_form` (`form_id`, `version`)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COLLATE = utf8mb4_general_ci COMMENT = '表单 Schema 历史版本';
