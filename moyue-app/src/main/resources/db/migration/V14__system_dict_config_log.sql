-- =============================================================
--  V14: 系统管理二期：字典 / 参数 / 操作日志 / 登录日志 + 菜单种子
--  说明：
--   1. sys_notice 不新建（复用 announcement 表）；
--   2. sys_job / sys_job_log 不新建（XXL-Job Admin 库为真源，moyue-system 代理访问）；
--   3. 删除策略：仅 sys_dict_type 走逻辑删除（is_deleted）；sys_dict_data 随类型物理删除；
--      sys_config / sys_oper_log / sys_logininfor 均无逻辑删除标记、物理删除；
--   4. 菜单种子 ID 沿用 V13 段位习惯（93xx 固定雪花段），挂 /api/v1/admin/system 下。
-- =============================================================

CREATE TABLE IF NOT EXISTS `sys_dict_type` (
  `id`          BIGINT       NOT NULL                COMMENT '字典类型主键（雪花 ID）',
  `dict_name`   VARCHAR(50)  NOT NULL                COMMENT '字典名称，如 小说状态',
  `dict_type`   VARCHAR(100) NOT NULL                COMMENT '字典类型编码，如 novel_status',
  `status`      TINYINT      NOT NULL DEFAULT 1      COMMENT '状态：0 停用 / 1 启用',
  `remark`      VARCHAR(255) DEFAULT NULL            COMMENT '备注',
  `is_deleted`  TINYINT(1)   NOT NULL DEFAULT 0      COMMENT '逻辑删除：0 否 / 1 是',
  `create_time` DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  `update_time` DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_dict_type` (`dict_type`)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COMMENT = '字典类型表';

CREATE TABLE IF NOT EXISTS `sys_dict_data` (
  `id`          BIGINT       NOT NULL                COMMENT '字典数据主键（雪花 ID）',
  `dict_type`   VARCHAR(100) NOT NULL                COMMENT '所属字典类型编码 → sys_dict_type.dict_type',
  `dict_label`  VARCHAR(100) NOT NULL                COMMENT '字典标签（展示用）',
  `dict_value`  VARCHAR(100) NOT NULL                COMMENT '字典键值（存储用）',
  `dict_sort`   INT          NOT NULL DEFAULT 0      COMMENT '显示顺序',
  `is_default`  TINYINT      NOT NULL DEFAULT 0      COMMENT '是否默认：0 否 / 1 是',
  `status`      TINYINT      NOT NULL DEFAULT 1      COMMENT '状态：0 停用 / 1 启用',
  `remark`      VARCHAR(255) DEFAULT NULL            COMMENT '备注',
  `create_time` DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  `update_time` DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
  PRIMARY KEY (`id`),
  KEY `idx_dict_type` (`dict_type`)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COMMENT = '字典数据表';

CREATE TABLE IF NOT EXISTS `sys_config` (
  `id`           BIGINT       NOT NULL               COMMENT '参数主键（雪花 ID）',
  `config_name`  VARCHAR(100) NOT NULL               COMMENT '参数名称，如 默认章节字数',
  `config_key`   VARCHAR(100) NOT NULL               COMMENT '参数键名，如 system.chapter.word-count',
  `config_value` VARCHAR(500) DEFAULT NULL           COMMENT '参数键值',
  `is_system`    TINYINT      NOT NULL DEFAULT 0     COMMENT '是否内置参数：0 否 / 1 是（内置不可删除）',
  `remark`       VARCHAR(255) DEFAULT NULL           COMMENT '备注',
  `create_time`  DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  `update_time`  DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_config_key` (`config_key`)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COMMENT = '系统参数配置表';

CREATE TABLE IF NOT EXISTS `sys_oper_log` (
  `id`            BIGINT      NOT NULL               COMMENT '日志主键（雪花 ID）',
  `module`        VARCHAR(50) DEFAULT NULL           COMMENT '业务模块，如 字典管理',
  `business_type` TINYINT     NOT NULL DEFAULT 0     COMMENT '业务类型：0 其它 / 1 新增 / 2 修改 / 3 删除 / 4 导出 / 5 强退 / 6 生成代码',
  `request_method` VARCHAR(10) DEFAULT NULL          COMMENT 'HTTP 请求方式：GET/POST/PUT/DELETE',
  `url`           VARCHAR(255) DEFAULT NULL          COMMENT '请求 URL',
  `operator_id`   BIGINT      DEFAULT NULL           COMMENT '操作人员 ID → sys_user.id',
  `operator_name` VARCHAR(50) DEFAULT NULL           COMMENT '操作人员名称',
  `ip`            VARCHAR(64) DEFAULT NULL           COMMENT '操作 IP',
  `param`         TEXT        DEFAULT NULL           COMMENT '请求参数（截断 2000 字符）',
  `result`        TEXT        DEFAULT NULL           COMMENT '返回结果（截断 2000 字符）',
  `status`        TINYINT     NOT NULL DEFAULT 0     COMMENT '操作状态：0 成功 / 1 失败',
  `error_msg`     TEXT        DEFAULT NULL           COMMENT '错误信息',
  `cost_ms`       INT         DEFAULT NULL           COMMENT '耗时（毫秒）',
  `oper_time`     DATETIME    DEFAULT NULL           COMMENT '操作时间',
  PRIMARY KEY (`id`),
  KEY `idx_operator` (`operator_id`),
  KEY `idx_module` (`module`),
  KEY `idx_oper_time` (`oper_time`)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COMMENT = '操作日志表';

CREATE TABLE IF NOT EXISTS `sys_logininfor` (
  `id`          BIGINT      NOT NULL                COMMENT '日志主键（雪花 ID）',
  `username`    VARCHAR(50) DEFAULT NULL           COMMENT '登录账号',
  `ip`          VARCHAR(64) DEFAULT NULL           COMMENT '登录 IP',
  `user_agent`  VARCHAR(255) DEFAULT NULL          COMMENT '浏览器 UA',
  `status`      TINYINT     NOT NULL DEFAULT 0     COMMENT '登录状态：0 成功 / 1 失败',
  `msg`         VARCHAR(255) DEFAULT NULL          COMMENT '提示信息，如 登录成功 / 账号或密码错误',
  `login_time`  DATETIME    DEFAULT NULL           COMMENT '登录时间',
  PRIMARY KEY (`id`),
  KEY `idx_username` (`username`),
  KEY `idx_login_time` (`login_time`)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COMMENT = '系统登录日志表';

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
