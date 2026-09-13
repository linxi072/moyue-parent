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


-- -------------------------------------------------------------
-- 种子数据：演示积分账户 / 商品 / 博客
-- -------------------------------------------------------------
INSERT INTO `points_account` (`user_id`, `balance`, `total_earned`, `total_spent`) VALUES
(1, 500, 500, 0);

INSERT INTO `points_product` (`id`, `name`, `description`, `image_url`, `cost_points`, `stock`, `status`) VALUES
(2001, '书币月卡', '赠送 300 书币，30 天有效', NULL, 200, 100, 1),
(2002, '墨阅定制书签', '金属书签一套', NULL, 150, 50, 1),
(2003, '作者月度推荐位', '作品首页推荐 7 天', NULL, 800, 10, 1);

INSERT INTO `blog_post` (`id`, `author_id`, `title`, `cover_url`, `summary`, `content`, `status`, `like_count`, `comment_count`, `view_count`) VALUES
(3001, 1, '我的写作心得：如何从零开始写一本小说', NULL, '分享三年创作路上的方法与踩坑。','写作是一场马拉松，与其追求一天写一万字，不如先养成每天稳定的输出节奏……（正文示例）',1, 12, 3, 240);

-- 种子：敏感词（分级）
INSERT IGNORE INTO `sensitive_word` (`id`,`word`,`level`,`category`) VALUES
 (910000000000000001,'示例敏感词A',1,'政治'),
 (910000000000000002,'示例敏感词B',1,'广告'),
 (910000000000000003,'示例灰词C',2,'谩骂');

-- 种子：消息模板
INSERT IGNORE INTO `message_template` (`id`,`code`,`name`,`title_tpl`,`content_tpl`,`channels`) VALUES
 (920000000000000001,'AUDIT_PASS','审核通过','{bizName}审核通过','您提交的{bizName}已通过审核。','1,2'),
 (920000000000000002,'AUDIT_REJECT','审核驳回','{bizName}审核驳回','您提交的{bizName}未通过审核：{reason}','1,2'),
 (920000000000000003,'REPORT_RESULT','举报处理结果','您的举报已处理','您对{targetDesc}的举报处理结果：{result}。','1,2');

-- -------------------------------------------------------------
-- 种子：系统参数（2 条内置示例）
-- -------------------------------------------------------------
INSERT IGNORE INTO `sys_config` (`id`, `config_name`, `config_key`, `config_value`, `is_system`, `remark`) VALUES
 (940000000000000001, '默认章节字数下限', 'system.chapter.min-word-count', '1000', 1, '发布章节的最小字数校验阈值'),
 (940000000000000002, '打赏单笔金额上限', 'system.reward.max-amount', '10000', 1, '单笔打赏允许的最大金额（元）');

-- -------------------------------------------------------------
-- 种子：示例字典（3 组类型 + 数据）
-- -------------------------------------------------------------
INSERT IGNORE INTO `sys_dict_type` (`id`, `dict_name`, `dict_type`, `status`, `remark`) VALUES
 (940000000000000101, '小说状态', 'novel_status', 1, '书籍/小说发布状态字典'),
 (940000000000000102, '小说类型', 'novel_type', 1, '小说题材分类字典'),
 (940000000000000103, '公告类型', 'announcement_type', 1, '平台公告类型字典');

INSERT IGNORE INTO `sys_dict_data` (`id`, `dict_type`, `dict_label`, `dict_value`, `dict_sort`, `is_default`, `status`, `remark`) VALUES
 (940000000000000201, 'novel_status', '连载中', '1', 1, 1, 1, '状态 1：连载'),
 (940000000000000202, 'novel_status', '已完结', '2', 2, 0, 1, '状态 2：完结'),
 (940000000000000203, 'novel_status', '已下架', '0', 3, 0, 1, '状态 0：下架'),
 (940000000000000204, 'novel_type', '玄幻', '1', 1, 1, 1, '题材 1：玄幻'),
 (940000000000000205, 'novel_type', '都市', '2', 2, 0, 1, '题材 2：都市'),
 (940000000000000206, 'novel_type', '悬疑', '3', 3, 0, 1, '题材 3：悬疑'),
 (940000000000000207, 'announcement_type', '系统公告', '1', 1, 1, 1, '平台级系统公告'),
 (940000000000000208, 'announcement_type', '活动公告', '2', 2, 0, 1, '运营活动公告'),
 (940000000000000209, 'announcement_type', '维护公告', '3', 3, 0, 1, '停机维护公告');

-- -------------------------------------------------------------
-- 种子：后台菜单 + 按钮权限码（对齐 V12 sys_menu 结构，parent 约定同 V12）
-- 结构：系统管理 / 系统监控 / 开发管理 三个顶级目录（parent_id = 0）
-- -------------------------------------------------------------
INSERT IGNORE INTO `sys_menu` (`id`, `parent_id`, `menu_name`, `menu_type`, `path`, `component`, `perms`, `icon`, `order_num`, `status`) VALUES
 -- 顶级目录
 (940000000000001001, 0, '系统管理', 0, 'system',    NULL, NULL, 'system',    1, 1),
 (940000000000001002, 0, '系统监控', 0, 'monitor',   NULL, NULL, 'monitor',   2, 1),
 (940000000000001003, 0, '开发管理', 0, 'tool',      NULL, NULL, 'tool',      3, 1),
 -- 系统管理 → 菜单
 (940000000000001101, 940000000000001001, '字典管理', 1, 'dict',   'system/dict/index',   NULL, 'dict',   1, 1),
 (940000000000001102, 940000000000001001, '参数设置', 1, 'config', 'system/config/index', NULL, 'edit',   2, 1),
 -- 系统监控 → 菜单
 (940000000000001201, 940000000000001002, '操作日志', 1, 'operlog',    'monitor/operlog/index',    NULL, 'form',     1, 1),
 (940000000000001202, 940000000000001002, '登录日志', 1, 'logininfor', 'monitor/logininfor/index', NULL, 'form',     2, 1),
 (940000000000001203, 940000000000001002, '在线用户', 1, 'online',     'monitor/online/index',     NULL, 'user',     3, 1),
 (940000000000001204, 940000000000001002, '服务监控', 1, 'server',     'monitor/server/index',     NULL, 'dashboard',4, 1),
 (940000000000001205, 940000000000001002, '数据监控', 1, 'dbpool',     'monitor/dbpool/index',     NULL, 'chart',    5, 1),
 (940000000000001206, 940000000000001002, '定时任务', 1, 'job',        'monitor/job/index',        NULL, 'job',      6, 1),
 -- 开发管理 → 菜单
 (940000000000001301, 940000000000001003, '代码生成', 1, 'gen',        'tool/gen/index',           NULL, 'code',     1, 1);

INSERT IGNORE INTO `sys_menu` (`id`, `parent_id`, `menu_name`, `menu_type`, `path`, `component`, `perms`, `icon`, `order_num`, `status`) VALUES
 -- 字典管理按钮：system:dict:*
 (940000000000002101, 940000000000001101, '字典查询', 2, NULL, NULL, 'system:dict:list',    NULL, 1, 1),
 (940000000000002102, 940000000000001101, '字典新增', 2, NULL, NULL, 'system:dict:add',     NULL, 2, 1),
 (940000000000002103, 940000000000001101, '字典修改', 2, NULL, NULL, 'system:dict:edit',    NULL, 3, 1),
 (940000000000002104, 940000000000001101, '字典删除', 2, NULL, NULL, 'system:dict:remove',  NULL, 4, 1),
 (940000000000002105, 940000000000001101, '字典刷新', 2, NULL, NULL, 'system:dict:refresh', NULL, 5, 1),
 -- 参数设置按钮：system:config:*
 (940000000000002111, 940000000000001102, '参数查询', 2, NULL, NULL, 'system:config:list',    NULL, 1, 1),
 (940000000000002112, 940000000000001102, '参数新增', 2, NULL, NULL, 'system:config:add',     NULL, 2, 1),
 (940000000000002113, 940000000000001102, '参数修改', 2, NULL, NULL, 'system:config:edit',    NULL, 3, 1),
 (940000000000002114, 940000000000001102, '参数删除', 2, NULL, NULL, 'system:config:remove',  NULL, 4, 1),
 (940000000000002115, 940000000000001102, '参数刷新', 2, NULL, NULL, 'system:config:refresh', NULL, 5, 1),
 -- 操作日志按钮：system:operlog:*
 (940000000000002121, 940000000000001201, '操作日志查询', 2, NULL, NULL, 'system:operlog:list',   NULL, 1, 1),
 (940000000000002122, 940000000000001201, '操作日志删除', 2, NULL, NULL, 'system:operlog:remove', NULL, 2, 1),
 -- 登录日志按钮：system:logininfor:*
 (940000000000002131, 940000000000001202, '登录日志查询', 2, NULL, NULL, 'system:logininfor:list',   NULL, 1, 1),
 (940000000000002132, 940000000000001202, '登录日志删除', 2, NULL, NULL, 'system:logininfor:remove', NULL, 2, 1),
 (940000000000002133, 940000000000001202, '登录日志清空', 2, NULL, NULL, 'system:logininfor:clear',  NULL, 3, 1),
 -- 在线用户按钮：system:online:*
 (940000000000002141, 940000000000001203, '在线用户查询', 2, NULL, NULL, 'system:online:list',        NULL, 1, 1),
 (940000000000002142, 940000000000001203, '在线用户强退', 2, NULL, NULL, 'system:online:forceLogout', NULL, 2, 1),
 -- 服务/数据监控查询：system:monitor:*
 (940000000000002151, 940000000000001204, '服务监控查询', 2, NULL, NULL, 'system:monitor:list', NULL, 1, 1),
 (940000000000002152, 940000000000001205, '数据监控查询', 2, NULL, NULL, 'system:monitor:list', NULL, 1, 1),
 -- 定时任务按钮：system:job:*
 (940000000000002161, 940000000000001206, '任务查询', 2, NULL, NULL, 'system:job:list',   NULL, 1, 1),
 (940000000000002162, 940000000000001206, '任务新增', 2, NULL, NULL, 'system:job:add',    NULL, 2, 1),
 (940000000000002163, 940000000000001206, '任务修改', 2, NULL, NULL, 'system:job:edit',   NULL, 3, 1),
 (940000000000002164, 940000000000001206, '任务删除', 2, NULL, NULL, 'system:job:remove', NULL, 4, 1),
 (940000000000002165, 940000000000001206, '任务启停', 2, NULL, NULL, 'system:job:changeStatus', NULL, 5, 1),
 (940000000000002166, 940000000000001206, '任务执行', 2, NULL, NULL, 'system:job:run',    NULL, 6, 1),
 -- 代码生成按钮：system:gen:*
 (940000000000002171, 940000000000001301, '生成查询', 2, NULL, NULL, 'system:gen:list',     NULL, 1, 1),
 (940000000000002172, 940000000000001301, '生成预览', 2, NULL, NULL, 'system:gen:preview',  NULL, 2, 1),
 (940000000000002173, 940000000000001301, '生成下载', 2, NULL, NULL, 'system:gen:download', NULL, 3, 1);

