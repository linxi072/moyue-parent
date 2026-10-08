-- =============================================================================
-- V19  补齐审计列（create_by / update_by / update_time）
-- -----------------------------------------------------------------------------
-- 【启动期真实缺陷】V12–V18 中，关联表与审计表的建表语句漏写了审计列，
-- 但 common-mybatis 的 MetaObjectFillHandler 会自动填充
--   create_by / create_time / update_by / update_time
-- MyBatis-Plus 据此生成的 INSERT / SELECT 引用这些列，运行到对应表时直接
--   Unknown column 'create_by' in 'field list'
-- 实测触发：sys_oper_log / sys_login_log 的查询与写入、sys_user_role 等关联表写入。
--
-- 修复方式：补列（不回改 V12–V18 源文件——Flyway 已基线到 v18，源改动不会重跑，
-- 反而会和新库冲突）。本迁移在已迁移库上补齐，新建库也会按 V12..V18 + V19 顺序一致落地。
-- 风格与 V12 的 sys_user / sys_dept 对齐（varchar(64) 可空 + datetime 可空）。
-- =============================================================================

ALTER TABLE `sys_user_role`
    ADD COLUMN `create_by`  varchar(64) DEFAULT NULL COMMENT '创建人',
    ADD COLUMN `update_by`  varchar(64) DEFAULT NULL COMMENT '更新人',
    ADD COLUMN `update_time` datetime    DEFAULT NULL COMMENT '更新时间';

ALTER TABLE `sys_role_menu`
    ADD COLUMN `create_by`  varchar(64) DEFAULT NULL COMMENT '创建人',
    ADD COLUMN `update_by`  varchar(64) DEFAULT NULL COMMENT '更新人',
    ADD COLUMN `update_time` datetime    DEFAULT NULL COMMENT '更新时间';

ALTER TABLE `sys_role_dept`
    ADD COLUMN `create_by`  varchar(64) DEFAULT NULL COMMENT '创建人',
    ADD COLUMN `update_by`  varchar(64) DEFAULT NULL COMMENT '更新人',
    ADD COLUMN `update_time` datetime    DEFAULT NULL COMMENT '更新时间';

ALTER TABLE `sys_oper_log`
    ADD COLUMN `create_by`  varchar(64) DEFAULT NULL COMMENT '创建人',
    ADD COLUMN `update_by`  varchar(64) DEFAULT NULL COMMENT '更新人',
    ADD COLUMN `update_time` datetime    DEFAULT NULL COMMENT '更新时间';

ALTER TABLE `sys_login_log`
    ADD COLUMN `create_by`  varchar(64) DEFAULT NULL COMMENT '创建人',
    ADD COLUMN `update_by`  varchar(64) DEFAULT NULL COMMENT '更新人',
    ADD COLUMN `update_time` datetime    DEFAULT NULL COMMENT '更新时间';

ALTER TABLE `sys_user_online`
    ADD COLUMN `create_by`  varchar(64) DEFAULT NULL COMMENT '创建人',
    ADD COLUMN `update_by`  varchar(64) DEFAULT NULL COMMENT '更新人',
    ADD COLUMN `update_time` datetime    DEFAULT NULL COMMENT '更新时间';
