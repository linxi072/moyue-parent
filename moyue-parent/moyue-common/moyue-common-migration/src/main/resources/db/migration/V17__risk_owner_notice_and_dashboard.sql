-- V17：内容安全处置闭环收尾（P1-4）
-- 1. 新增 OWNER_NOTICE 消息模板：举报/审核后给被处理内容归属人（章节作者 / 评论者 / 书籍作者）发站内信。
--    站内信渠道（INBOX=1）真实落 notice 表，故该模板默认仅走站内信（channels=1），
--    保证处置闭环在站内信真实可达，不依赖 P1-2 邮件/短信服务商。
-- 2. 后台菜单按钮权限码种子：内容安全 → 风险看板（system:risk:dashboard），
--    对齐 V15 sys_menu 种子风格，供 RiskDashboardController 的 @RequiresPermissions 校验。

-- 种子：被处理方通知模板（仅站内信渠道）
INSERT IGNORE INTO `message_template` (`id`,`code`,`name`,`title_tpl`,`content_tpl`,`channels`) VALUES
 (920000000000000004,'OWNER_NOTICE','被处理方通知','{targetDesc}处理通知','您发布的{targetDesc}因被举报，平台处理结果：{result}。处理意见：{reason}','1');

-- 种子：内容安全 → 风险看板按钮权限码
INSERT IGNORE INTO `sys_menu` (`id`, `parent_id`, `menu_name`, `menu_type`, `path`, `component`, `perms`, `icon`, `order_num`, `status`) VALUES
 (940000000000002193, 940000000000001004, '风险看板', 2, NULL, NULL, 'system:risk:dashboard', 'form', 3, 1);
