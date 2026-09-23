-- V15：内容安全收尾（P2-15）
-- 1. sensitive_word 补命中统计列：V13 建表时无 hit_count，机审命中次数在此累计（旁路指标，仅统计用）。
-- 2. 后台菜单 + 按钮权限码种子：内容安全（敏感词管理 / 举报管理），对齐 V14 sys_menu 种子风格。

ALTER TABLE `sensitive_word`
    ADD COLUMN `hit_count` INT NOT NULL DEFAULT 0 COMMENT '命中次数统计（机审命中累计）' AFTER `status`;

-- -------------------------------------------------------------
-- 种子：后台菜单 + 按钮权限码（对齐 V14 sys_menu 结构）
-- 结构：内容安全顶级目录（parent_id = 0）
-- -------------------------------------------------------------
INSERT IGNORE INTO `sys_menu` (`id`, `parent_id`, `menu_name`, `menu_type`, `path`, `component`, `perms`, `icon`, `order_num`, `status`) VALUES
 (940000000000001004, 0, '内容安全', 0, 'safety', NULL, NULL, 'safe', 4, 1),
 (940000000000001401, 940000000000001004, '敏感词管理', 1, 'sensitiveword', 'risk/word/index',   NULL, 'edit', 1, 1),
 (940000000000001402, 940000000000001004, '举报管理',   1, 'report',       'risk/report/index', NULL, 'form', 2, 1);

INSERT IGNORE INTO `sys_menu` (`id`, `parent_id`, `menu_name`, `menu_type`, `path`, `component`, `perms`, `icon`, `order_num`, `status`) VALUES
 -- 敏感词按钮：system:risk:word:*
 (940000000000002181, 940000000000001401, '敏感词查询',     2, NULL, NULL, 'system:risk:word:list',    NULL, 1, 1),
 (940000000000002182, 940000000000001401, '敏感词新增',     2, NULL, NULL, 'system:risk:word:add',     NULL, 2, 1),
 (940000000000002183, 940000000000001401, '敏感词修改',     2, NULL, NULL, 'system:risk:word:edit',    NULL, 3, 1),
 (940000000000002184, 940000000000001401, '敏感词删除',     2, NULL, NULL, 'system:risk:word:remove',  NULL, 4, 1),
 (940000000000002185, 940000000000001401, '敏感词导入',     2, NULL, NULL, 'system:risk:word:import',  NULL, 5, 1),
 (940000000000002186, 940000000000001401, '敏感词导出',     2, NULL, NULL, 'system:risk:word:export',  NULL, 6, 1),
 (940000000000002187, 940000000000001401, '敏感词词库刷新', 2, NULL, NULL, 'system:risk:word:refresh', NULL, 7, 1),
 -- 举报按钮：system:report:*
 (940000000000002191, 940000000000001402, '举报查询',       2, NULL, NULL, 'system:report:list',   NULL, 1, 1),
 (940000000000002192, 940000000000001402, '举报处理',       2, NULL, NULL, 'system:report:handle', NULL, 2, 1);
