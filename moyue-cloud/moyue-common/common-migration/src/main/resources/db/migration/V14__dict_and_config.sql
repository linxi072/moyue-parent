-- =============================================================================
-- V14  字典与参数管理（架构说明书 6.3 / 6.4 V4.0 新增）
-- -----------------------------------------------------------------------------
-- 三张表：sys_dict_type / sys_dict_data / sys_config
-- 字段定义直接取自说明书 6.3；审计字段与 is_deleted 按 6.5 规范补齐。
-- 缓存键见 common-redis 的 CacheNames：moyue:dict:data / moyue:config
-- =============================================================================

-- ---------------------------------------------------------------- 字典类型
CREATE TABLE IF NOT EXISTS `sys_dict_type` (
    `id`         bigint       NOT NULL COMMENT '字典主键（雪花 ID）',
    `dict_name`  varchar(128) NOT NULL DEFAULT '' COMMENT '字典名称',
    `dict_type`  varchar(128) NOT NULL DEFAULT '' COMMENT '字典类型标识，如 book_category，唯一索引',
    `status`     tinyint      NOT NULL DEFAULT 1 COMMENT '状态：0 停用 / 1 正常',
    `create_by`  varchar(64)  DEFAULT NULL COMMENT '创建人',
    `create_time` datetime    DEFAULT NULL COMMENT '创建时间',
    `update_by`  varchar(64)  DEFAULT NULL COMMENT '更新人',
    `update_time` datetime    DEFAULT NULL COMMENT '更新时间',
    `remark`     varchar(512) DEFAULT NULL COMMENT '备注',
    `is_deleted` tinyint(1)   NOT NULL DEFAULT 0 COMMENT '逻辑删除：0 未删除 / 1 已删除',
    PRIMARY KEY (`id`),
    UNIQUE KEY `uk_dict_type` (`dict_type`),
    KEY `idx_dict_status` (`status`, `is_deleted`)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COLLATE = utf8mb4_general_ci COMMENT = '字典类型表';

-- ---------------------------------------------------------------- 字典数据
CREATE TABLE IF NOT EXISTS `sys_dict_data` (
    `id`         bigint       NOT NULL COMMENT '字典编码（雪花 ID）',
    `dict_type`  varchar(128) NOT NULL DEFAULT '' COMMENT '字典类型 → sys_dict_type.dict_type',
    `dict_label` varchar(128) NOT NULL DEFAULT '' COMMENT '字典标签（展示值）',
    `dict_value` varchar(128) NOT NULL DEFAULT '' COMMENT '字典键值（存储值）',
    `dict_sort`  int          NOT NULL DEFAULT 0 COMMENT '显示排序，升序',
    `is_default` tinyint      NOT NULL DEFAULT 0 COMMENT '是否默认：0 否 / 1 是',
    `status`     tinyint      NOT NULL DEFAULT 1 COMMENT '状态：0 停用 / 1 正常',
    `create_by`  varchar(64)  DEFAULT NULL COMMENT '创建人',
    `create_time` datetime    DEFAULT NULL COMMENT '创建时间',
    `update_by`  varchar(64)  DEFAULT NULL COMMENT '更新人',
    `update_time` datetime    DEFAULT NULL COMMENT '更新时间',
    `remark`     varchar(512) DEFAULT NULL COMMENT '备注',
    `is_deleted` tinyint(1)   NOT NULL DEFAULT 0 COMMENT '逻辑删除：0 未删除 / 1 已删除',
    PRIMARY KEY (`id`),
    KEY `idx_dd_type` (`dict_type`),
    KEY `idx_dd_type_status` (`dict_type`, `status`, `is_deleted`)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COLLATE = utf8mb4_general_ci COMMENT = '字典数据表';

-- ---------------------------------------------------------------- 参数配置
CREATE TABLE IF NOT EXISTS `sys_config` (
    `id`          bigint       NOT NULL COMMENT '参数主键（雪花 ID）',
    `config_name` varchar(128) NOT NULL DEFAULT '' COMMENT '参数名称',
    `config_key`  varchar(128) NOT NULL DEFAULT '' COMMENT '参数键名，如 points.sign.daily，唯一索引',
    `config_value` varchar(512) NOT NULL DEFAULT '' COMMENT '参数键值',
    `config_type` tinyint      NOT NULL DEFAULT 0 COMMENT '类型：0 自定义 / 1 系统内置（不可删除）',
    `create_by`   varchar(64)  DEFAULT NULL COMMENT '创建人',
    `create_time` datetime     DEFAULT NULL COMMENT '创建时间',
    `update_by`   varchar(64)  DEFAULT NULL COMMENT '更新人',
    `update_time` datetime     DEFAULT NULL COMMENT '更新时间',
    `remark`      varchar(512) DEFAULT NULL COMMENT '备注',
    `is_deleted`  tinyint(1)   NOT NULL DEFAULT 0 COMMENT '逻辑删除：0 未删除 / 1 已删除',
    PRIMARY KEY (`id`),
    UNIQUE KEY `uk_config_key` (`config_key`)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COLLATE = utf8mb4_general_ci COMMENT = '参数配置表';

-- ---------------------------------------------------------------- 初始化数据
-- 系统内置参数（config_type = 1，前端不可删除）
INSERT INTO `sys_config` (`id`, `config_name`, `config_key`, `config_value`, `config_type`,
                          `create_by`, `create_time`, `remark`, `is_deleted`)
VALUES
    (1, '用户初始密码',      'sys.user.initPassword',   'User@123456',      1, 'system', NOW(), '新增用户时的默认密码', 0),
    (2, '验证码开关',        'sys.account.captchaEnabled', 'true',          1, 'system', NOW(), '后台登录是否校验验证码', 0),
    (3, '后台登录失败锁定次数', 'sys.account.maxRetryCount', '5',            1, 'system', NOW(), '超过次数锁定账号，见 CacheNames.LOGIN_FAIL', 0),
    (4, '书架容量上限',      'reader.bookshelf.maxSize', '500',             1, 'system', NOW(), '单个读者书架可容纳作品数', 0),
    (5, '每日签到积分',      'points.sign.daily',        '10',              0, 'system', NOW(), '每日签到发放积分', 0),
    (6, '章节审核超时小时数', 'audit.chapter.timeoutHours', '24',           0, 'system', NOW(), '超时未审自动流转至上级审核', 0);

-- 常用字典
INSERT INTO `sys_dict_type` (`id`, `dict_name`, `dict_type`, `status`, `create_by`, `create_time`, `remark`, `is_deleted`)
VALUES
    (1, '用户状态',   'sys_user_status',   1, 'system', NOW(), '读者/作者/运营账号状态', 0),
    (2, '主体类型',   'sys_user_type',     1, 'system', NOW(), '1 读者 / 2 作者 / 3 运营', 0),
    (3, '菜单状态',   'sys_menu_status',   1, 'system', NOW(), '菜单显示与停用', 0),
    (4, '任务状态',   'sys_job_status',    1, 'system', NOW(), 'XXL-Job 任务启停', 0),
    (5, '系统开关',   'sys_yes_no',        1, 'system', NOW(), '通用是否开关', 0);

INSERT INTO `sys_dict_data` (`id`, `dict_type`, `dict_label`, `dict_value`, `dict_sort`, `is_default`, `status`,
                             `create_by`, `create_time`, `is_deleted`)
VALUES
    (1, 'sys_user_status', '正常', '1', 1, 1, 1, 'system', NOW(), 0),
    (2, 'sys_user_status', '停用', '0', 2, 0, 1, 'system', NOW(), 0),
    (3, 'sys_user_type',   '读者', '1', 1, 0, 1, 'system', NOW(), 0),
    (4, 'sys_user_type',   '作者', '2', 2, 0, 1, 'system', NOW(), 0),
    (5, 'sys_user_type',   '运营', '3', 3, 0, 1, 'system', NOW(), 0),
    (6, 'sys_yes_no',      '是',   '1', 1, 0, 1, 'system', NOW(), 0),
    (7, 'sys_yes_no',      '否',   '0', 2, 1, 1, 'system', NOW(), 0),
    (8, 'sys_job_status',  '正常', '1', 1, 1, 1, 'system', NOW(), 0),
    (9, 'sys_job_status',  '暂停', '0', 2, 0, 1, 'system', NOW(), 0),
    (10, 'sys_menu_status', '显示', '1', 1, 1, 1, 'system', NOW(), 0),
    (11, 'sys_menu_status', '隐藏', '0', 2, 0, 1, 'system', NOW(), 0);
