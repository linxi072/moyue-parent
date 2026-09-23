-- V21：分类服务独立化（P2-A）—— 幂等种子
-- 1. 默认分类：玄幻(id=1)/都市(id=2)/悬疑(id=3)，sort 对应 1/2/3，对齐原 BookService.CATEGORY_NAMES 硬编码。
-- 2. 后台分类管理菜单（目录/菜单/按钮），perms = system:category:list/add/edit/remove，
--    供 moyue-system CategoryAdminController 复用 RBAC（@RequiresPermissions）。
-- 幂等：H2/MySQL 均支持 INSERT ... SELECT ... WHERE NOT EXISTS，重复执行不报错。
-- 注：H2 测试库由 tools/gen-h2-schema.py 生成等价 schema，种子一并直发。

-- ----- 默认分类 -----
INSERT INTO category (id, name, icon, sort, status)
SELECT 1, '玄幻', 'fantasy', 1, 1
WHERE NOT EXISTS (SELECT 1 FROM category WHERE id = 1);

INSERT INTO category (id, name, icon, sort, status)
SELECT 2, '都市', 'city', 2, 1
WHERE NOT EXISTS (SELECT 1 FROM category WHERE id = 2);

INSERT INTO category (id, name, icon, sort, status)
SELECT 3, '悬疑', 'mystery', 3, 1
WHERE NOT EXISTS (SELECT 1 FROM category WHERE id = 3);

-- ----- 后台分类管理菜单（顶级菜单，parent_id=0，对齐 V15 sys_menu 种子风格）-----
INSERT INTO sys_menu (id, parent_id, menu_name, menu_type, path, component, perms, icon, order_num, status)
SELECT 940000000000003001, 0, '分类管理', 1, 'category', 'content/category/index', 'system:category:list', 'category', 5, 1
WHERE NOT EXISTS (SELECT 1 FROM sys_menu WHERE id = 940000000000003001);

-- ----- 按钮权限码（system:category:list/add/edit/remove）-----
INSERT INTO sys_menu (id, parent_id, menu_name, menu_type, path, component, perms, icon, order_num, status)
SELECT 940000000000003011, 940000000000003001, '分类查询', 2, NULL, NULL, 'system:category:list', NULL, 1, 1
WHERE NOT EXISTS (SELECT 1 FROM sys_menu WHERE id = 940000000000003011);

INSERT INTO sys_menu (id, parent_id, menu_name, menu_type, path, component, perms, icon, order_num, status)
SELECT 940000000000003012, 940000000000003001, '分类新增', 2, NULL, NULL, 'system:category:add', NULL, 2, 1
WHERE NOT EXISTS (SELECT 1 FROM sys_menu WHERE id = 940000000000003012);

INSERT INTO sys_menu (id, parent_id, menu_name, menu_type, path, component, perms, icon, order_num, status)
SELECT 940000000000003013, 940000000000003001, '分类修改', 2, NULL, NULL, 'system:category:edit', NULL, 3, 1
WHERE NOT EXISTS (SELECT 1 FROM sys_menu WHERE id = 940000000000003013);

INSERT INTO sys_menu (id, parent_id, menu_name, menu_type, path, component, perms, icon, order_num, status)
SELECT 940000000000003014, 940000000000003001, '分类删除', 2, NULL, NULL, 'system:category:remove', NULL, 4, 1
WHERE NOT EXISTS (SELECT 1 FROM sys_menu WHERE id = 940000000000003014);
