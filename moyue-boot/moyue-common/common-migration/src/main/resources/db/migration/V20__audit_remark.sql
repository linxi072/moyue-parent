-- =============================================================================
-- V20  补齐 remark 列
-- -----------------------------------------------------------------------------
-- 【续 V19】上一步补了 create_by / update_by / update_time，但 sys_user_role 等六张表
-- 还缺 remark 列（来自 common-mybatis BaseEntity）。MyBatis-Plus 生成的 SELECT 引用 remark，
-- 仍会 Unknown column 'remark'。本迁移补齐，与 V12 的 sys_user / sys_dept 对齐。
-- =============================================================================

ALTER TABLE `sys_user_role`   ADD COLUMN `remark` varchar(512) DEFAULT NULL COMMENT '备注';
ALTER TABLE `sys_role_menu`   ADD COLUMN `remark` varchar(512) DEFAULT NULL COMMENT '备注';
ALTER TABLE `sys_role_dept`   ADD COLUMN `remark` varchar(512) DEFAULT NULL COMMENT '备注';
ALTER TABLE `sys_oper_log`    ADD COLUMN `remark` varchar(512) DEFAULT NULL COMMENT '备注';
ALTER TABLE `sys_login_log`   ADD COLUMN `remark` varchar(512) DEFAULT NULL COMMENT '备注';
ALTER TABLE `sys_user_online` ADD COLUMN `remark` varchar(512) DEFAULT NULL COMMENT '备注';
