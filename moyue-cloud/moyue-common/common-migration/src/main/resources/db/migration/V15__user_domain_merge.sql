-- =============================================================================
-- V15  用户域合并（架构说明书 6.4 V4.0 新增）
-- -----------------------------------------------------------------------------
-- 1) sys_user 扩展 user_type / phone / email / avatar（login_* 与 dept_id 已在 V12 建成）
-- 2) 原 user 表数据迁入 sys_user（条件执行：仅当 user 表存在时）
-- 3) 建同名视图 user 做过渡兼容，确认无引用后由后续迁移 DROP
--
-- 【幂等性说明】MySQL 不支持 ADD COLUMN IF NOT EXISTS，因此下面的 ALTER 假定
--   本迁移在 V12 之后执行一次。对已有历史库请先确认 sys_user 是否已有同名列。
-- =============================================================================

-- ---------------------------------------------------------------- 1. 扩展字段
ALTER TABLE `sys_user`
    ADD COLUMN `user_type` tinyint      DEFAULT 3     COMMENT '主体性质：1 读者 / 2 作者 / 3 运营' AFTER `dept_id`,
    ADD COLUMN `phone`     varchar(11)  DEFAULT NULL  COMMENT '手机号，唯一索引；C 端可用作登录账号' AFTER `user_type`,
    ADD COLUMN `email`     varchar(128) DEFAULT NULL  COMMENT '邮箱' AFTER `phone`,
    ADD COLUMN `avatar`    varchar(512) DEFAULT NULL  COMMENT '头像 URL' AFTER `email`;

-- ---------------------------------------------------------------- 2. 索引
ALTER TABLE `sys_user`
    ADD UNIQUE KEY `uk_user_phone` (`phone`),
    ADD KEY `idx_user_type_status` (`user_type`, `status`, `is_deleted`);

-- ---------------------------------------------------------------- 3. 存量数据兜底
-- V12 初始化的运营账号统一标记为运营主体
UPDATE `sys_user` SET `user_type` = 3 WHERE `user_type` IS NULL;

-- ---------------------------------------------------------------- 4. 原 user 表迁移（条件执行）
DROP PROCEDURE IF EXISTS `moyue_merge_user_domain`;

DELIMITER $$
CREATE PROCEDURE `moyue_merge_user_domain`()
BEGIN
    DECLARE v_exists INT DEFAULT 0;

    SELECT COUNT(*) INTO v_exists
    FROM `information_schema`.`TABLES`
    WHERE `TABLE_SCHEMA` = DATABASE()
      AND `TABLE_NAME` = 'user';

    IF v_exists > 0 THEN
        -- 4.1 C 端用户迁入：id 沿用原值，保证 book.author_id / comment.user_id 等外键不失效
        INSERT INTO `sys_user` (
            `id`, `username`, `nickname`, `phone`, `email`, `avatar`,
            `user_type`, `status`, `create_time`, `is_deleted`
        )
        SELECT
            u.`id`,
            NULL                                        AS `username`,
            u.`nickname`,
            u.`phone`,
            u.`email`,
            u.`avatar`,
            IFNULL(u.`user_type`, 1)                    AS `user_type`,
            IFNULL(u.`status`, 1)                       AS `status`,
            IFNULL(u.`create_time`, NOW())              AS `create_time`,
            IFNULL(u.`is_deleted`, 0)                   AS `is_deleted`
        FROM `user` u
        WHERE NOT EXISTS (
            SELECT 1 FROM `sys_user` s WHERE s.`id` = u.`id`
        );

        -- 4.2 迁移统计写入备注，便于人工核对（不落日志表，避免依赖 V16）
        --    迁移完成后请在下一轮迁移中执行：DROP VIEW IF EXISTS `user`; DROP TABLE IF EXISTS `user`;
    END IF;
END$$
DELIMITER ;

CALL `moyue_merge_user_domain`();

DROP PROCEDURE IF EXISTS `moyue_merge_user_domain`;

-- ---------------------------------------------------------------- 5. 兼容视图（暂不创建）
-- 说明书建议废弃期间建同名视图做兼容，但视图与表不能同名共存：
-- 必须先 DROP TABLE `user` 才能 CREATE VIEW `user`。
-- 因此本迁移只迁数据、保留原表一个迭代；确认无引用后，在下一轮迁移中执行：
--     DROP TABLE IF EXISTS `user`;
--     CREATE OR REPLACE VIEW `user` AS
--         SELECT `id`, `nickname`, `phone`, `email`, `avatar`,
--                `user_type`, `status`, `create_time`, `update_time`, `is_deleted`
--         FROM `sys_user` WHERE `is_deleted` = 0;
-- 该视图仅为过渡兼容，最终目标仍是全部改为 sys_user。
