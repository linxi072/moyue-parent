-- V12: 系统管理（RBAC 运营后台）：部门 / 用户 / 角色 / 菜单 + 关联表
-- 与 C 端 user 表解耦：sys_user 为运营后台操作员账号，登录签发 role=3 令牌，
-- 由 AdminRoleInterceptor 对 /api/v1/admin/** 做角色断言（避免越权调用后台接口）。
-- 数据权限（角色可见部门范围）用 sys_role.data_scope + dept_ids（逗号分隔）表达，省一张关联表。

CREATE TABLE IF NOT EXISTS `sys_dept` (
  `id`          BIGINT       NOT NULL                COMMENT '部门主键（雪花 ID）',
  `parent_id`   BIGINT       NOT NULL DEFAULT 0      COMMENT '父部门 ID，0 表示顶级',
  `dept_name`   VARCHAR(50)  NOT NULL                COMMENT '部门名称',
  `order_num`   INT          NOT NULL DEFAULT 0      COMMENT '显示顺序',
  `leader`      VARCHAR(20)  DEFAULT NULL            COMMENT '负责人',
  `phone`       VARCHAR(11)  DEFAULT NULL            COMMENT '联系电话',
  `email`       VARCHAR(64)  DEFAULT NULL            COMMENT '邮箱',
  `status`      TINYINT      NOT NULL DEFAULT 1      COMMENT '状态：0 禁用 / 1 正常',
  `is_deleted`  TINYINT(1)   NOT NULL DEFAULT 0      COMMENT '逻辑删除：0 否 / 1 是',
  `create_time` DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  `update_time` DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
  PRIMARY KEY (`id`),
  KEY `idx_parent` (`parent_id`)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COMMENT = '系统部门表';

CREATE TABLE IF NOT EXISTS `sys_user` (
  `id`          BIGINT       NOT NULL                COMMENT '用户主键（雪花 ID）',
  `dept_id`     BIGINT       NOT NULL DEFAULT 0      COMMENT '部门 ID → sys_dept.id，0 未分配',
  `username`    VARCHAR(30)  NOT NULL                COMMENT '登录账号',
  `nickname`    VARCHAR(30)  NOT NULL                COMMENT '昵称',
  `password`    VARCHAR(72)  NOT NULL                COMMENT 'BCrypt 密码哈希',
  `email`       VARCHAR(64)  DEFAULT NULL            COMMENT '邮箱',
  `phone`       VARCHAR(11)  DEFAULT NULL            COMMENT '手机号',
  `status`      TINYINT      NOT NULL DEFAULT 1      COMMENT '状态：0 禁用 / 1 正常',
  `is_deleted`  TINYINT(1)   NOT NULL DEFAULT 0      COMMENT '逻辑删除：0 否 / 1 是',
  `create_time` DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  `update_time` DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_username` (`username`),
  KEY `idx_dept` (`dept_id`),
  KEY `idx_status` (`status`)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COMMENT = '系统用户表（运营后台操作员）';

CREATE TABLE IF NOT EXISTS `sys_role` (
  `id`          BIGINT       NOT NULL                COMMENT '角色主键（雪花 ID）',
  `role_name`   VARCHAR(30)  NOT NULL                COMMENT '角色名称',
  `role_key`    VARCHAR(50)  NOT NULL                COMMENT '角色标识（权限校验用，如 admin）',
  `data_scope`  TINYINT      NOT NULL DEFAULT 1      COMMENT '数据权限：1 全部 / 2 自定义部门 / 3 仅本人',
  `dept_ids`    VARCHAR(255) DEFAULT NULL            COMMENT '自定义数据权限部门 ID，逗号分隔（data_scope=2）',
  `status`      TINYINT      NOT NULL DEFAULT 1      COMMENT '状态：0 禁用 / 1 正常',
  `is_deleted`  TINYINT(1)   NOT NULL DEFAULT 0      COMMENT '逻辑删除：0 否 / 1 是',
  `create_time` DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  `update_time` DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_role_key` (`role_key`),
  KEY `idx_status` (`status`)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COMMENT = '系统角色表';

CREATE TABLE IF NOT EXISTS `sys_menu` (
  `id`          BIGINT       NOT NULL                COMMENT '菜单主键（雪花 ID）',
  `parent_id`   BIGINT       NOT NULL DEFAULT 0      COMMENT '父菜单 ID，0 表示顶级',
  `menu_name`   VARCHAR(50)  NOT NULL                COMMENT '菜单名称',
  `menu_type`   TINYINT      NOT NULL DEFAULT 1      COMMENT '类型：0 目录 / 1 菜单 / 2 按钮',
  `path`        VARCHAR(128) DEFAULT NULL            COMMENT '路由地址',
  `component`   VARCHAR(128) DEFAULT NULL            COMMENT '前端组件路径',
  `perms`       VARCHAR(128) DEFAULT NULL            COMMENT '权限标识（按钮级，如 system:user:add）',
  `icon`        VARCHAR(64)  DEFAULT NULL            COMMENT '图标',
  `order_num`   INT          NOT NULL DEFAULT 0      COMMENT '显示顺序',
  `status`      TINYINT      NOT NULL DEFAULT 1      COMMENT '状态：0 禁用 / 1 正常',
  `is_deleted`  TINYINT(1)   NOT NULL DEFAULT 0      COMMENT '逻辑删除：0 否 / 1 是',
  `create_time` DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  `update_time` DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
  PRIMARY KEY (`id`),
  KEY `idx_parent` (`parent_id`)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COMMENT = '系统菜单表（目录/菜单/按钮）';

CREATE TABLE IF NOT EXISTS `sys_user_role` (
  `user_id`     BIGINT       NOT NULL                COMMENT '用户 ID → sys_user.id',
  `role_id`     BIGINT       NOT NULL                COMMENT '角色 ID → sys_role.id',
  PRIMARY KEY (`user_id`, `role_id`),
  KEY `idx_role` (`role_id`)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COMMENT = '用户-角色关联表';

CREATE TABLE IF NOT EXISTS `sys_role_menu` (
  `role_id`     BIGINT       NOT NULL                COMMENT '角色 ID → sys_role.id',
  `menu_id`     BIGINT       NOT NULL                COMMENT '菜单 ID → sys_menu.id',
  PRIMARY KEY (`role_id`, `menu_id`),
  KEY `idx_menu` (`menu_id`)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COMMENT = '角色-菜单关联表';
