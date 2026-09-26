-- V25：P2-B 会员体系完整版 —— member_subscription 增加连续订阅（自动续费）字段 + 续费扫描索引
-- 复用 V18/V19 幂等 ALTER 惯用法：information_schema 判列/索引是否存在 → SET @sql := IF(@exist=0,'真实DDL','SELECT 1') → PREPARE/EXECUTE/DEALLOCATE。
-- H2 测试库不直喂本脚本，由 tools/gen-h2-schema.py 抽取「真实 DDL」（then 分支）直发生成等价 schema。
-- 注：本迁移严格遵循既有幂等约束，重复执行无 "Duplicate column / Duplicate index" 报错。

-- 自动续费开关：0 否 / 1 是
SET @exist_auto_renew := (SELECT COUNT(*) FROM information_schema.columns
                          WHERE table_schema = DATABASE() AND table_name = 'member_subscription' AND column_name = 'auto_renew');
SET @sql_auto_renew := IF(@exist_auto_renew = 0,
    'ALTER TABLE `member_subscription` ADD COLUMN `auto_renew` TINYINT(1) NOT NULL DEFAULT 0 COMMENT ''自动续费开关：0否/1是''',
    'SELECT 1');
PREPARE stmt_auto_renew FROM @sql_auto_renew;
EXECUTE stmt_auto_renew;
DEALLOCATE PREPARE stmt_auto_renew;

-- 续费周期快照（subscribe 时取 tier.durationDays）：续费按此值整期延长
SET @exist_renew_cycle_days := (SELECT COUNT(*) FROM information_schema.columns
                                WHERE table_schema = DATABASE() AND table_name = 'member_subscription' AND column_name = 'renew_cycle_days');
SET @sql_renew_cycle_days := IF(@exist_renew_cycle_days = 0,
    'ALTER TABLE `member_subscription` ADD COLUMN `renew_cycle_days` INT NOT NULL DEFAULT 30 COMMENT ''续费周期快照（天）：subscribe 取 tier.durationDays，整期续费按此延长''',
    'SELECT 1');
PREPARE stmt_renew_cycle_days FROM @sql_renew_cycle_days;
EXECUTE stmt_renew_cycle_days;
DEALLOCATE PREPARE stmt_renew_cycle_days;

-- 下次续费计划时刻 = endTime − 窗口（窗口 = max(1, renew_cycle_days/3) 天）
SET @exist_renew_at := (SELECT COUNT(*) FROM information_schema.columns
                        WHERE table_schema = DATABASE() AND table_name = 'member_subscription' AND column_name = 'renew_at');
SET @sql_renew_at := IF(@exist_renew_at = 0,
    'ALTER TABLE `member_subscription` ADD COLUMN `renew_at` DATETIME DEFAULT NULL COMMENT ''下次续费计划时刻：endTime − 窗口；扫描起点''',
    'SELECT 1');
PREPARE stmt_renew_at FROM @sql_renew_at;
EXECUTE stmt_renew_at;
DEALLOCATE PREPARE stmt_renew_at;

-- 最近一次续费触发时刻
SET @exist_last_renew_at := (SELECT COUNT(*) FROM information_schema.columns
                             WHERE table_schema = DATABASE() AND table_name = 'member_subscription' AND column_name = 'last_renew_at');
SET @sql_last_renew_at := IF(@exist_last_renew_at = 0,
    'ALTER TABLE `member_subscription` ADD COLUMN `last_renew_at` DATETIME DEFAULT NULL COMMENT ''最近一次续费触发时刻''',
    'SELECT 1');
PREPARE stmt_last_renew_at FROM @sql_last_renew_at;
EXECUTE stmt_last_renew_at;
DEALLOCATE PREPARE stmt_last_renew_at;

-- 续费连续失败次数
SET @exist_renew_fail_count := (SELECT COUNT(*) FROM information_schema.columns
                                WHERE table_schema = DATABASE() AND table_name = 'member_subscription' AND column_name = 'renew_fail_count');
SET @sql_renew_fail_count := IF(@exist_renew_fail_count = 0,
    'ALTER TABLE `member_subscription` ADD COLUMN `renew_fail_count` INT NOT NULL DEFAULT 0 COMMENT ''续费连续失败次数''',
    'SELECT 1');
PREPARE stmt_renew_fail_count FROM @sql_renew_fail_count;
EXECUTE stmt_renew_fail_count;
DEALLOCATE PREPARE stmt_renew_fail_count;

-- 最近续费结果：0 成功 / 1 失败 / NULL 未续费
SET @exist_renew_last_status := (SELECT COUNT(*) FROM information_schema.columns
                                 WHERE table_schema = DATABASE() AND table_name = 'member_subscription' AND column_name = 'renew_last_status');
SET @sql_renew_last_status := IF(@exist_renew_last_status = 0,
    'ALTER TABLE `member_subscription` ADD COLUMN `renew_last_status` TINYINT DEFAULT NULL COMMENT ''最近续费结果：0成功/1失败/NULL未续费''',
    'SELECT 1');
PREPARE stmt_renew_last_status FROM @sql_renew_last_status;
EXECUTE stmt_renew_last_status;
DEALLOCATE PREPARE stmt_renew_last_status;

-- 最近续费结果摘要（失败原因）
SET @exist_renew_last_msg := (SELECT COUNT(*) FROM information_schema.columns
                              WHERE table_schema = DATABASE() AND table_name = 'member_subscription' AND column_name = 'renew_last_msg');
SET @sql_renew_last_msg := IF(@exist_renew_last_msg = 0,
    'ALTER TABLE `member_subscription` ADD COLUMN `renew_last_msg` VARCHAR(255) DEFAULT NULL COMMENT ''最近续费结果摘要（失败原因）''',
    'SELECT 1');
PREPARE stmt_renew_last_msg FROM @sql_renew_last_msg;
EXECUTE stmt_renew_last_msg;
DEALLOCATE PREPARE stmt_renew_last_msg;

-- 续费扫描联合索引：status + auto_renew + renew_at（扫描「生效中 + 已开自动续费 + 到期窗口内」最优路径）
SET @exist_idx_renew := (SELECT COUNT(*) FROM information_schema.statistics
                         WHERE table_schema = DATABASE() AND table_name = 'member_subscription' AND index_name = 'idx_member_sub_renew');
SET @sql_idx_renew := IF(@exist_idx_renew = 0,
    'ALTER TABLE `member_subscription` ADD INDEX `idx_member_sub_renew` (`status`, `auto_renew`, `renew_at`)',
    'SELECT 1');
PREPARE stmt_idx_renew FROM @sql_idx_renew;
EXECUTE stmt_idx_renew;
DEALLOCATE PREPARE stmt_idx_renew;
