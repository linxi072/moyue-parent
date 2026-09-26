-- V28：消息中心聚合 —— notice 表补充逻辑删除列 is_deleted（V3 建表时漏建，实体 NoticeEntity 已声明该字段）
-- 复用 V18/V19/V25 幂等 ALTER 惯用法：information_schema 判列是否存在 → SET @sql := IF(@exist=0,'真实DDL','SELECT 1') → PREPARE/EXECUTE/DEALLOCATE。
-- H2 测试库由 tools/gen-h2-schema.py 抽取「真实 DDL」分支生成等价 schema（V1__h2_schema.sql 的 notice 表已含 is_deleted）。
-- 本迁移严格遵循幂等约束，重复执行无 "Duplicate column" 报错；绝不回改既有 V3 建表语句，避免已应用环境 Flyway checksum 失配。

SET @exist_is_deleted := (SELECT COUNT(*) FROM information_schema.columns
                           WHERE table_schema = DATABASE() AND table_name = 'notice' AND column_name = 'is_deleted');
SET @sql_is_deleted := IF(@exist_is_deleted = 0,
    'ALTER TABLE `notice` ADD COLUMN `is_deleted` TINYINT(1) NOT NULL DEFAULT 0 COMMENT ''逻辑删除：0否/1是''',
    'SELECT 1');
PREPARE stmt_is_deleted FROM @sql_is_deleted;
EXECUTE stmt_is_deleted;
DEALLOCATE PREPARE stmt_is_deleted;
