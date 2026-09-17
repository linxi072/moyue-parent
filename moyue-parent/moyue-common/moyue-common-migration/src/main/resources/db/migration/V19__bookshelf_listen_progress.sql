-- V19：智能朗读（P2-L）—— bookshelf 补充听书进度列
-- 复用 bookshelf 表（不新建表，对齐主理人 Q4 决策），避免重复落库与唯一键分裂。
-- 幂等：经 information_schema 判列是否存在 + PREPARE/EXECUTE 动态 ALTER（对齐 V18）。
-- 注：H2 测试库不直喂本脚本，由 tools/gen-h2-schema.py 生成等价 schema，此处语法以 MySQL 为准。

-- 听书进度：当前收听章节
SET @exist_lc := (SELECT COUNT(*) FROM information_schema.columns
                  WHERE table_schema = DATABASE() AND table_name = 'bookshelf' AND column_name = 'listen_chapter_id');
SET @sql_lc := IF(@exist_lc = 0,
    'ALTER TABLE `bookshelf` ADD COLUMN `listen_chapter_id` BIGINT DEFAULT NULL COMMENT ''听书进度：当前收听章节 → chapter.id'' AFTER `last_chapter_id`',
    'SELECT 1');
PREPARE stmt_lc FROM @sql_lc;
EXECUTE stmt_lc;
DEALLOCATE PREPARE stmt_lc;

-- 听书进度：章节内片段序号（断点续听）
SET @exist_ls := (SELECT COUNT(*) FROM information_schema.columns
                  WHERE table_schema = DATABASE() AND table_name = 'bookshelf' AND column_name = 'listen_segment_index');
SET @sql_ls := IF(@exist_ls = 0,
    'ALTER TABLE `bookshelf` ADD COLUMN `listen_segment_index` INT DEFAULT 0 COMMENT ''听书进度：章节内片段序号（断点续听）'' AFTER `listen_chapter_id`',
    'SELECT 1');
PREPARE stmt_ls FROM @sql_ls;
EXECUTE stmt_ls;
DEALLOCATE PREPARE stmt_ls;

-- 听书进度：片段内字符偏移
SET @exist_lo := (SELECT COUNT(*) FROM information_schema.columns
                  WHERE table_schema = DATABASE() AND table_name = 'bookshelf' AND column_name = 'listen_char_offset');
SET @sql_lo := IF(@exist_lo = 0,
    'ALTER TABLE `bookshelf` ADD COLUMN `listen_char_offset` INT DEFAULT 0 COMMENT ''听书进度：片段内字符偏移'' AFTER `listen_segment_index`',
    'SELECT 1');
PREPARE stmt_lo FROM @sql_lo;
EXECUTE stmt_lo;
DEALLOCATE PREPARE stmt_lo;
