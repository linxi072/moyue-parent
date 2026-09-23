-- V18：触达渠道补全（P1-2）—— user 表补充联系字段
-- 1. email：邮件渠道收件邮箱（可空；未绑定则不投邮件，邮件渠道静默 pending）。
-- 2. device_token：推送渠道设备令牌（可空；未绑定则不投推送，推送渠道静默 pending）。
-- 幂等：经 information_schema 判断列是否存在，避免本地重跑 / 回退后重复执行报 "Duplicate column"。
-- 注：H2 测试库不直喂本脚本，由 tools/gen-h2-schema.py 生成等价 schema，此处语法以 MySQL 为准。

-- 邮箱列
SET @exist_email := (SELECT COUNT(*) FROM information_schema.columns
                     WHERE table_schema = DATABASE() AND table_name = 'user' AND column_name = 'email');
SET @sql_email := IF(@exist_email = 0,
    'ALTER TABLE `user` ADD COLUMN `email` VARCHAR(128) DEFAULT NULL COMMENT ''邮箱（触达渠道：邮件；可空）'' AFTER `avatar_url`',
    'SELECT 1');
PREPARE stmt_email FROM @sql_email;
EXECUTE stmt_email;
DEALLOCATE PREPARE stmt_email;

-- 设备推送令牌列
SET @exist_token := (SELECT COUNT(*) FROM information_schema.columns
                     WHERE table_schema = DATABASE() AND table_name = 'user' AND column_name = 'device_token');
SET @sql_token := IF(@exist_token = 0,
    'ALTER TABLE `user` ADD COLUMN `device_token` VARCHAR(512) DEFAULT NULL COMMENT ''设备推送令牌（触达渠道：推送；可空）'' AFTER `email`',
    'SELECT 1');
PREPARE stmt_token FROM @sql_token;
EXECUTE stmt_token;
DEALLOCATE PREPARE stmt_token;
