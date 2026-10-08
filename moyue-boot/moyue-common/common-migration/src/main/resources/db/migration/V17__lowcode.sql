-- =============================================================================
-- V17  低代码两域四表（架构说明书 6.4 V5.0 新增）
-- -----------------------------------------------------------------------------
-- gen_table / gen_table_column  代码生成域：表结构元数据 + 字段生成配置
-- sys_form / sys_form_item      在线构建器域：动态表单定义与字段项
-- sys_form_data                 在线构建器域：表单提交数据（见下方【缺口 G-7】）
--
-- 说明：Velocity 模板渲染所需的信息全部落在这五张表，生成动作不落库（Zip 流式下载）。
-- =============================================================================

-- ---------------------------------------------------------------- 代码生成 - 表
CREATE TABLE IF NOT EXISTS `gen_table` (
    `id`               bigint       NOT NULL COMMENT '配置主键（雪花 ID）',
    `table_name`       varchar(200) DEFAULT '' COMMENT '表名称，如 sys_user',
    `table_comment`    varchar(512) DEFAULT '' COMMENT '表描述，取自 information_schema',
    `sub_table_name`   varchar(64)  DEFAULT NULL COMMENT '子表名称（主子表模板）',
    `sub_table_fk_name` varchar(64) DEFAULT NULL COMMENT '子表关联的外键名',
    `class_name`       varchar(128) DEFAULT '' COMMENT '实体类名称，如 SysUser',
    `tpl_category`     varchar(64)  DEFAULT 'crud' COMMENT '模板类型：crud 单表 / tree 树表 / sub 主子表',
    `package_name`     varchar(128) DEFAULT '' COMMENT '生成包路径，如 com.moyue.system',
    `module_name`      varchar(64)  DEFAULT '' COMMENT '生成模块名，如 system',
    `business_name`    varchar(64)  DEFAULT '' COMMENT '生成业务名，如 user',
    `function_name`    varchar(128) DEFAULT '' COMMENT '生成功能名，用于菜单与页面标题',
    `function_author`  varchar(64)  DEFAULT '' COMMENT '生成功能作者',
    `gen_type`         tinyint      DEFAULT 0 COMMENT '生成方式：0 Zip 下载 / 1 写入自定义路径',
    `gen_path`         varchar(256) DEFAULT '/' COMMENT '自定义生成路径（gen_type = 1 时生效）',
    `options`          text COMMENT '其它生成选项（JSON，如树编码字段、父菜单 ID）',
    `create_by`        varchar(64)  DEFAULT NULL COMMENT '创建人',
    `create_time`      datetime     DEFAULT NULL COMMENT '创建时间',
    `update_by`        varchar(64)  DEFAULT NULL COMMENT '更新人',
    `update_time`      datetime     DEFAULT NULL COMMENT '更新时间',
    `remark`           varchar(512) DEFAULT NULL COMMENT '备注',
    `is_deleted`       tinyint(1)   NOT NULL DEFAULT 0 COMMENT '逻辑删除：0 未删除 / 1 已删除',
    PRIMARY KEY (`id`),
    UNIQUE KEY `uk_gen_table_name` (`table_name`),
    KEY `idx_gen_module` (`module_name`, `business_name`)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COLLATE = utf8mb4_general_ci COMMENT = '代码生成业务表';

-- ---------------------------------------------------------------- 代码生成 - 字段
CREATE TABLE IF NOT EXISTS `gen_table_column` (
    `id`            bigint       NOT NULL COMMENT '字段主键（雪花 ID）',
    `table_id`      bigint       DEFAULT NULL COMMENT '归属表配置 ID → gen_table.id',
    `column_name`   varchar(200) DEFAULT NULL COMMENT '列名称，如 user_name',
    `column_comment` varchar(512) DEFAULT NULL COMMENT '列描述',
    `column_type`   varchar(128) DEFAULT NULL COMMENT '列类型，如 varchar(64)',
    `java_type`     varchar(128) DEFAULT NULL COMMENT 'Java 类型，如 String',
    `java_field`    varchar(128) DEFAULT NULL COMMENT 'Java 字段名，如 userName',
    `is_pk`         tinyint      DEFAULT 0 COMMENT '是否主键：0 否 / 1 是',
    `is_increment`  tinyint      DEFAULT 0 COMMENT '是否自增：0 否 / 1 是',
    `is_required`   tinyint      DEFAULT 0 COMMENT '是否必填：0 否 / 1 是',
    `is_insert`     tinyint      DEFAULT 0 COMMENT '是否新增字段：0 否 / 1 是',
    `is_edit`       tinyint      DEFAULT 0 COMMENT '是否编辑字段：0 否 / 1 是',
    `is_list`       tinyint      DEFAULT 0 COMMENT '是否列表字段：0 否 / 1 是',
    `is_query`      tinyint      DEFAULT 0 COMMENT '是否查询字段：0 否 / 1 是',
    `query_type`    varchar(64)  DEFAULT 'EQ' COMMENT '查询方式：EQ / NE / GT / GE / LT / LE / LIKE / BETWEEN',
    `html_type`     varchar(64)  DEFAULT 'input' COMMENT '表单控件：input / textarea / select / radio / checkbox / datetime / upload / editor',
    `dict_type`     varchar(128) DEFAULT '' COMMENT '字典类型，用于 select / radio 回填选项',
    `sort`          int          DEFAULT 0 COMMENT '排序，升序',
    `create_by`     varchar(64)  DEFAULT NULL COMMENT '创建人',
    `create_time`   datetime     DEFAULT NULL COMMENT '创建时间',
    `update_by`     varchar(64)  DEFAULT NULL COMMENT '更新人',
    `update_time`   datetime     DEFAULT NULL COMMENT '更新时间',
    `remark`        varchar(512) DEFAULT NULL COMMENT '备注',
    `is_deleted`    tinyint(1)   NOT NULL DEFAULT 0 COMMENT '逻辑删除：0 未删除 / 1 已删除',
    PRIMARY KEY (`id`),
    KEY `idx_gc_table` (`table_id`),
    KEY `idx_gc_sort` (`table_id`, `sort`)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COLLATE = utf8mb4_general_ci COMMENT = '代码生成字段表';

-- ---------------------------------------------------------------- 在线构建器 - 表单
CREATE TABLE IF NOT EXISTS `sys_form` (
    `id`          bigint       NOT NULL COMMENT '表单主键（雪花 ID）',
    `form_name`   varchar(128) NOT NULL COMMENT '表单名称',
    `form_key`    varchar(128) NOT NULL COMMENT '表单标识，如 author_realname，唯一索引',
    `form_desc`   varchar(512) DEFAULT NULL COMMENT '表单描述',
    `config`      text COMMENT '表单整体配置（JSON：布局 / 校验规则 / 提交地址）',
    `status`      tinyint      DEFAULT 0 COMMENT '状态：0 草稿 / 1 已发布 / 2 已下线',
    `version`     int          DEFAULT 1 COMMENT '版本号，每次发布 +1，便于回滚',
    `create_by`   varchar(64)  DEFAULT NULL COMMENT '创建人',
    `create_time` datetime     DEFAULT NULL COMMENT '创建时间',
    `update_by`   varchar(64)  DEFAULT NULL COMMENT '更新人',
    `update_time` datetime     DEFAULT NULL COMMENT '更新时间',
    `remark`      varchar(512) DEFAULT NULL COMMENT '备注',
    `is_deleted`  tinyint(1)   NOT NULL DEFAULT 0 COMMENT '逻辑删除：0 未删除 / 1 已删除',
    PRIMARY KEY (`id`),
    UNIQUE KEY `uk_form_key` (`form_key`),
    KEY `idx_form_status` (`status`, `is_deleted`)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COLLATE = utf8mb4_general_ci COMMENT = '在线构建器表单表';

-- ---------------------------------------------------------------- 在线构建器 - 字段项
CREATE TABLE IF NOT EXISTS `sys_form_item` (
    `id`            bigint       NOT NULL COMMENT '字段项主键（雪花 ID）',
    `form_id`       bigint       NOT NULL COMMENT '归属表单 ID → sys_form.id',
    `item_name`     varchar(128) NOT NULL COMMENT '字段名称（展示用）',
    `item_key`      varchar(128) NOT NULL COMMENT '字段标识（提交时的 key）',
    `item_type`     varchar(64)  DEFAULT 'input' COMMENT '控件类型：input / textarea / number / select / radio / checkbox / date / upload',
    `default_value` varchar(512) DEFAULT NULL COMMENT '默认值',
    `placeholder`   varchar(256) DEFAULT NULL COMMENT '输入提示',
    `options`       text COMMENT '选项与校验规则（JSON：choices / required / maxLength / pattern）',
    `required`      tinyint      DEFAULT 0 COMMENT '是否必填：0 否 / 1 是',
    `sort`          int          DEFAULT 0 COMMENT '排序，升序',
    `create_by`     varchar(64)  DEFAULT NULL COMMENT '创建人',
    `create_time`   datetime     DEFAULT NULL COMMENT '创建时间',
    `update_by`     varchar(64)  DEFAULT NULL COMMENT '更新人',
    `update_time`   datetime     DEFAULT NULL COMMENT '更新时间',
    `remark`        varchar(512) DEFAULT NULL COMMENT '备注',
    `is_deleted`    tinyint(1)   NOT NULL DEFAULT 0 COMMENT '逻辑删除：0 未删除 / 1 已删除',
    PRIMARY KEY (`id`),
    UNIQUE KEY `uk_form_item` (`form_id`, `item_key`),
    KEY `idx_fi_sort` (`form_id`, `sort`)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COLLATE = utf8mb4_general_ci COMMENT = '在线构建器字段项表';

-- ---------------------------------------------------------------- 在线构建器 - 提交数据
-- 【架构缺口 G-7 裁定】说明书 7.6 域⑯ 包含「表单收集数据列表 / 详情 / 删除 / 导出」四个端点，
--   但 6.4 的 V17 只列出 sys_form / sys_form_item 两表，未定义提交数据的存储位置。
--   本脚本补充 sys_form_data：以 JSON 承载动态字段（Schema 可随时变更，不适合建物理列），
--   常用检索维度（form_id / 提交人 / 提交时间）单独建索引。已在《架构缺口补齐说明.md》登记。
CREATE TABLE IF NOT EXISTS `sys_form_data` (
    `id`          bigint       NOT NULL COMMENT '数据主键（雪花 ID）',
    `form_id`     bigint       NOT NULL COMMENT '归属表单 ID → sys_form.id',
    `form_key`    varchar(128) NOT NULL COMMENT '表单标识，冗余存储便于按 key 检索',
    `data_json`   text COMMENT '提交内容（JSON：item_key -> value）',
    `submit_by`   bigint       DEFAULT NULL COMMENT '提交人 ID → sys_user.id，匿名表单为空',
    `submit_name` varchar(64)  DEFAULT '' COMMENT '提交人账号（快照）',
    `submit_ip`   varchar(50)  DEFAULT '' COMMENT '提交 IP',
    `submit_time` datetime     DEFAULT NULL COMMENT '提交时间',
    `status`      tinyint      DEFAULT 1 COMMENT '状态：0 已删除待清理 / 1 正常',
    `create_by`   varchar(64)  DEFAULT NULL COMMENT '创建人',
    `create_time` datetime     DEFAULT NULL COMMENT '创建时间',
    `update_by`   varchar(64)  DEFAULT NULL COMMENT '更新人',
    `update_time` datetime     DEFAULT NULL COMMENT '更新时间',
    `remark`      varchar(512) DEFAULT NULL COMMENT '备注',
    `is_deleted`  tinyint(1)   NOT NULL DEFAULT 0 COMMENT '逻辑删除：0 未删除 / 1 已删除',
    PRIMARY KEY (`id`),
    KEY `idx_fd_form` (`form_id`, `submit_time`),
    KEY `idx_fd_key` (`form_key`),
    KEY `idx_fd_submitter` (`submit_by`, `submit_time`)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COLLATE = utf8mb4_general_ci COMMENT = '在线构建器提交数据表';
