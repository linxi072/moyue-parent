-- =============================================================
--  Flyway 迁移 V2：演示数据
--  说明：用户(phone=13800000000)由 moyue-auth 启动时以 BCrypt 写入，
--        避免在此硬编码密码哈希；以下仅填充作品/章节/评论演示数据。
--  author_id / user_id 统一引用演示用户 id = 1。
-- =============================================================

INSERT INTO book (id, author_id, title, cover_url, category_id, tags, intro, status, word_count, click_count) VALUES
 (1001, 1, '万古剑尊', 'https://cover.moyue.com/1001.jpg', 1, '玄幻,热血', '少年持剑，踏碎凌霄，谱写一段热血传奇。', 1, 3280000, 102400),
 (1002, 1, '都市潜龙', 'https://cover.moyue.com/1002.jpg', 2, '都市', '隐世强者重归都市，笑看风云变幻。', 1, 1560000, 88110),
 (1003, 1, '医品风流', 'https://cover.moyue.com/1003.jpg', 2, '都市', '一手银针悬壶济世，一手妙手逆转乾坤。', 1, 980000, 55300),
 (1004, 1, '完美世界', 'https://cover.moyue.com/1004.jpg', 1, '玄幻', '一粒尘可填海，一根草斩尽日月星辰。', 2, 5200000, 230000),
 (1005, 1, '诡秘之主', 'https://cover.moyue.com/1005.jpg', 3, '悬疑', '蒸汽与机械的纪元，神秘与诡秘交织。', 1, 2400000, 176500);

INSERT INTO chapter (id, book_id, chapter_no, title, content, word_count, status, publish_time) VALUES
 (2001, 1001, 1, '第一章 觉醒', '剑气纵横三万里，一剑光寒十九洲。', 1200, 2, NOW()),
 (2002, 1001, 2, '第二章 试炼', '山门前的石阶泛着寒光，少年拾级而上。', 1500, 2, NOW()),
 (2003, 1004, 1, '第一章 误入', '石村的孩子名叫石昊，自石毅出生后便与众不同。', 1800, 2, NOW());

INSERT INTO comment (id, user_id, book_id, content, status, like_count) VALUES
 (3001, 1, 1001, '开篇即燃，追定了！', 1, 128),
 (3002, 1, 1004, '辰东的脑洞永远在线。', 1, 256);
