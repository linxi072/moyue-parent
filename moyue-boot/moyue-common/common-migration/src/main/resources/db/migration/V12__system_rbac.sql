-- =============================================================================
-- V12  系统管理（组织权限）基础表
-- -----------------------------------------------------------------------------
-- 架构说明书 6.4：V12 系统管理六张 sys_* 表。
--
-- 【架构缺口 G-1 裁定】说明书仅给出 sys_user 的字段定义，sys_role / sys_menu /
--   sys_dept / sys_user_role / sys_role_menu 五张表未定义字段，本脚本按 RuoYi 惯例 +
--   说明书 6.5 命名规范补齐，字段含义直接写入 COMMENT。
-- 【架构缺口 G-2 裁定】data_scope 枚举说明书未定义，本脚本裁定为：
--     1 全部数据 / 2 自定义数据 / 3 本部门 / 4 本部门及以下 / 5 仅本人
--   其中「2 自定义数据」的作用域由 sys_role_dept 关联表承载。
-- 【口径差异 C-7】说明书称「六张 sys_* 表」，但数据权限必需的 sys_role_dept 未计入，
--   本脚本实际建 7 张表，已在《架构缺口补齐说明.md》登记。
--
-- 主键统一 bigint 雪花 ID；所有表必备 is_deleted 逻辑删除位。
-- =============================================================================

-- ---------------------------------------------------------------- 部门表
CREATE TABLE IF NOT EXISTS `sys_dept` (
    `id`           bigint       NOT NULL COMMENT '部门主键（雪花 ID）',
    `parent_id`    bigint       NOT NULL DEFAULT 0 COMMENT '父部门 ID，顶级为 0',
    `ancestors`    varchar(256) NOT NULL DEFAULT '' COMMENT '祖级路径，如 0,100,101，用于「本部门及以下」递归查询',
    `dept_name`    varchar(64)  NOT NULL DEFAULT '' COMMENT '部门名称',
    `order_num`    int          NOT NULL DEFAULT 0 COMMENT '显示顺序，升序',
    `leader`       varchar(32)  DEFAULT NULL COMMENT '负责人',
    `phone`        varchar(11)  DEFAULT NULL COMMENT '联系电话',
    `email`        varchar(128) DEFAULT NULL COMMENT '邮箱',
    `status`       tinyint      NOT NULL DEFAULT 1 COMMENT '状态：0 停用 / 1 正常',
    `create_by`    varchar(64)  DEFAULT NULL COMMENT '创建人',
    `create_time`  datetime     DEFAULT NULL COMMENT '创建时间',
    `update_by`    varchar(64)  DEFAULT NULL COMMENT '更新人',
    `update_time`  datetime     DEFAULT NULL COMMENT '更新时间',
    `remark`       varchar(512) DEFAULT NULL COMMENT '备注',
    `is_deleted`   tinyint(1)   NOT NULL DEFAULT 0 COMMENT '逻辑删除：0 未删除 / 1 已删除',
    PRIMARY KEY (`id`),
    KEY `idx_dept_parent` (`parent_id`),
    KEY `idx_dept_status` (`status`, `is_deleted`)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COLLATE = utf8mb4_general_ci COMMENT = '部门表';

-- ---------------------------------------------------------------- 用户表
-- V12 只建运营侧基础字段；user_type / phone / email / avatar 由 V15 用户域合并补充。
CREATE TABLE IF NOT EXISTS `sys_user` (
    `id`           bigint       NOT NULL COMMENT '用户主键（雪花 ID；迁移时沿用原表 id）',
    `username`     varchar(64)  DEFAULT NULL COMMENT '登录账号：运营人员必填且唯一；C 端留 NULL（勿用空串，否则唯一索引冲突）',
    `nickname`     varchar(32)  DEFAULT NULL COMMENT '用户昵称',
    `password`     varchar(128) DEFAULT NULL COMMENT 'BCrypt 密码；C 端手机号登录用户可为空',
    `dept_id`      bigint       DEFAULT NULL COMMENT '所属部门 → sys_dept.id，C 端用户为空',
    `status`       tinyint      NOT NULL DEFAULT 1 COMMENT '状态：0 禁用 / 1 正常',
    `login_ip`     varchar(50)  DEFAULT NULL COMMENT '最近登录 IP',
    `login_date`   datetime     DEFAULT NULL COMMENT '最近登录时间',
    `create_by`    varchar(64)  DEFAULT NULL COMMENT '创建人',
    `create_time`  datetime     DEFAULT NULL COMMENT '创建时间',
    `update_by`    varchar(64)  DEFAULT NULL COMMENT '更新人',
    `update_time`  datetime     DEFAULT NULL COMMENT '更新时间',
    `remark`       varchar(512) DEFAULT NULL COMMENT '备注',
    `is_deleted`   tinyint(1)   NOT NULL DEFAULT 0 COMMENT '逻辑删除：0 未删除 / 1 已删除',
    PRIMARY KEY (`id`),
    UNIQUE KEY `uk_user_username` (`username`),
    KEY `idx_user_dept` (`dept_id`),
    KEY `idx_user_status` (`status`, `is_deleted`)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COLLATE = utf8mb4_general_ci COMMENT = '统一用户表（读者 / 作者 / 运营）';

-- ---------------------------------------------------------------- 角色表
CREATE TABLE IF NOT EXISTS `sys_role` (
    `id`          bigint       NOT NULL COMMENT '角色主键（雪花 ID）',
    `role_name`   varchar(64)  NOT NULL COMMENT '角色名称，如「内容审核员」',
    `role_key`    varchar(128) NOT NULL COMMENT '角色权限标识，如 content:auditor，唯一索引',
    `role_sort`   int          NOT NULL DEFAULT 0 COMMENT '显示顺序，升序',
    `data_scope`  tinyint      NOT NULL DEFAULT 1 COMMENT '数据范围：1 全部 / 2 自定义 / 3 本部门 / 4 本部门及以下 / 5 仅本人',
    `status`      tinyint      NOT NULL DEFAULT 1 COMMENT '状态：0 停用 / 1 正常',
    `create_by`   varchar(64)  DEFAULT NULL COMMENT '创建人',
    `create_time` datetime     DEFAULT NULL COMMENT '创建时间',
    `update_by`   varchar(64)  DEFAULT NULL COMMENT '更新人',
    `update_time` datetime     DEFAULT NULL COMMENT '更新时间',
    `remark`      varchar(512) DEFAULT NULL COMMENT '备注',
    `is_deleted`  tinyint(1)   NOT NULL DEFAULT 0 COMMENT '逻辑删除：0 未删除 / 1 已删除',
    PRIMARY KEY (`id`),
    UNIQUE KEY `uk_role_key` (`role_key`),
    KEY `idx_role_status` (`status`, `is_deleted`)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COLLATE = utf8mb4_general_ci COMMENT = '角色表';

-- ---------------------------------------------------------------- 菜单（权限）表
CREATE TABLE IF NOT EXISTS `sys_menu` (
    `id`         bigint       NOT NULL COMMENT '菜单主键（雪花 ID）',
    `menu_name`  varchar(64)  NOT NULL COMMENT '菜单名称',
    `parent_id`  bigint       NOT NULL DEFAULT 0 COMMENT '父菜单 ID，顶级为 0',
    `order_num`  int          NOT NULL DEFAULT 0 COMMENT '显示顺序，升序',
    `path`       varchar(256) DEFAULT '' COMMENT '前端路由地址',
    `component`  varchar(256) DEFAULT NULL COMMENT '前端组件路径，如 system/user/index',
    `query`      varchar(256) DEFAULT NULL COMMENT '路由参数，如 {"id": 1}',
    `is_frame`   tinyint      NOT NULL DEFAULT 1 COMMENT '是否外链：0 是 / 1 否',
    `is_cache`   tinyint      NOT NULL DEFAULT 0 COMMENT '是否缓存：0 缓存 / 1 不缓存',
    `menu_type`  char(1)      NOT NULL DEFAULT '' COMMENT '菜单类型：M 目录 / C 菜单 / F 按钮',
    `visible`    tinyint      NOT NULL DEFAULT 1 COMMENT '显示状态：0 隐藏 / 1 显示',
    `status`     tinyint      NOT NULL DEFAULT 1 COMMENT '状态：0 停用 / 1 正常',
    `perms`      varchar(128) DEFAULT NULL COMMENT '权限标识，如 system:user:list',
    `icon`       varchar(128) DEFAULT '#' COMMENT '菜单图标',
    `create_by`  varchar(64)  DEFAULT NULL COMMENT '创建人',
    `create_time` datetime    DEFAULT NULL COMMENT '创建时间',
    `update_by`  varchar(64)  DEFAULT NULL COMMENT '更新人',
    `update_time` datetime    DEFAULT NULL COMMENT '更新时间',
    `remark`     varchar(512) DEFAULT NULL COMMENT '备注',
    `is_deleted` tinyint(1)   NOT NULL DEFAULT 0 COMMENT '逻辑删除：0 未删除 / 1 已删除',
    PRIMARY KEY (`id`),
    KEY `idx_menu_parent` (`parent_id`),
    KEY `idx_menu_perms` (`perms`)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COLLATE = utf8mb4_general_ci COMMENT = '菜单权限表';

-- ---------------------------------------------------------------- 用户-角色关联
CREATE TABLE IF NOT EXISTS `sys_user_role` (
    `id`      bigint NOT NULL COMMENT '关联主键（雪花 ID）',
    `user_id` bigint NOT NULL COMMENT '用户 ID → sys_user.id',
    `role_id` bigint NOT NULL COMMENT '角色 ID → sys_role.id',
    PRIMARY KEY (`id`),
    UNIQUE KEY `uk_user_role` (`user_id`, `role_id`),
    KEY `idx_ur_role` (`role_id`)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COLLATE = utf8mb4_general_ci COMMENT = '用户与角色关联表';

-- ---------------------------------------------------------------- 角色-菜单关联
CREATE TABLE IF NOT EXISTS `sys_role_menu` (
    `id`      bigint NOT NULL COMMENT '关联主键（雪花 ID）',
    `role_id` bigint NOT NULL COMMENT '角色 ID → sys_role.id',
    `menu_id` bigint NOT NULL COMMENT '菜单 ID → sys_menu.id',
    PRIMARY KEY (`id`),
    UNIQUE KEY `uk_role_menu` (`role_id`, `menu_id`)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COLLATE = utf8mb4_general_ci COMMENT = '角色与菜单关联表';

-- ---------------------------------------------------------------- 角色-部门（数据权限）
-- 【G-2】sys_role.data_scope = 2（自定义数据）时的作用域明细。
CREATE TABLE IF NOT EXISTS `sys_role_dept` (
    `id`      bigint NOT NULL COMMENT '关联主键（雪花 ID）',
    `role_id` bigint NOT NULL COMMENT '角色 ID → sys_role.id',
    `dept_id` bigint NOT NULL COMMENT '部门 ID → sys_dept.id',
    PRIMARY KEY (`id`),
    UNIQUE KEY `uk_role_dept` (`role_id`, `dept_id`)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COLLATE = utf8mb4_general_ci COMMENT = '角色与部门关联表（数据权限）';

-- ---------------------------------------------------------------- 初始化数据
-- 超管角色：拥有全部菜单与全部数据范围
INSERT INTO `sys_role` (`id`, `role_name`, `role_key`, `role_sort`, `data_scope`, `status`,
                        `create_by`, `create_time`, `remark`, `is_deleted`)
VALUES (1, '超级管理员', 'ROLE_ADMIN', 1, 1, 1, 'system', NOW(), '内置超管角色，不可删除', 0);

-- 顶级部门
INSERT INTO `sys_dept` (`id`, `parent_id`, `ancestors`, `dept_name`, `order_num`, `status`,
                        `create_by`, `create_time`, `is_deleted`)
VALUES (100, 0, '0', '墨阅科技', 1, 1, 'system', NOW(), 0);

-- 超管账号：初始密码 Admin@123（BCrypt，上线前必须修改）
INSERT INTO `sys_user` (`id`, `username`, `nickname`, `password`, `dept_id`, `status`,
                        `create_by`, `create_time`, `remark`, `is_deleted`)
VALUES (1, 'admin', '超级管理员',
        '$2a$10$N.zmdr9k7uOCQb376NoUnuTJ8iAt6Z5EHsM8lE9lBOsl7iKTVKIUi',
        100, 1, 'system', NOW(), '内置超管账号，上线前请修改密码', 0);

INSERT INTO `sys_user_role` (`id`, `user_id`, `role_id`) VALUES (1, 1, 1);
