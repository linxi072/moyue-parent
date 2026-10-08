-- =============================================================================
-- V16  审计日志三表（架构说明书 6.4 V5.0 新增）
-- -----------------------------------------------------------------------------
-- sys_oper_log    操作日志（对应 common-log 的 OperLogDTO）
-- sys_login_log   登录日志（对应 common-log 的 LoginLogDTO）
-- sys_user_online 在线用户审计（对应缓存监控/在线用户域的会话快照）
-- =============================================================================

-- ---------------------------------------------------------------- 操作日志
CREATE TABLE IF NOT EXISTS `sys_oper_log` (
    `id`             bigint        NOT NULL COMMENT '日志主键（雪花 ID）',
    `title`          varchar(64)   DEFAULT '' COMMENT '模块标题，取自 @Log.title',
    `business_type`  tinyint       DEFAULT 0 COMMENT '业务类型：0 其它 / 1 新增 / 2 修改 / 3 删除 / 4 授权 / 5 导出 / 6 导入 / 7 强退 / 8 生成代码 / 9 清空 / 10 审核 / 11 上下线',
    `method`         varchar(256)  DEFAULT '' COMMENT '方法全限定名，如 com.moyue..UserController#save',
    `request_method` varchar(16)   DEFAULT '' COMMENT 'HTTP 方法',
    `operator_type`  tinyint       DEFAULT 0 COMMENT '操作人类别：0 其它 / 1 读者 / 2 作者 / 3 后台运营 / 4 系统内部',
    `oper_name`      varchar(64)   DEFAULT '' COMMENT '操作人账号',
    `oper_id`        bigint        DEFAULT NULL COMMENT '操作人 ID → sys_user.id',
    `oper_url`       varchar(512)  DEFAULT '' COMMENT '请求 URL',
    `oper_ip`        varchar(50)   DEFAULT '' COMMENT '客户端 IP',
    `oper_location`  varchar(255)  DEFAULT '' COMMENT 'IP 归属地',
    `oper_param`     text COMMENT '请求参数（截断 4000 字符）',
    `json_result`    text COMMENT '响应结果（仅 saveResponseData=true 时保存，截断 2000 字符）',
    `status`         tinyint       DEFAULT 0 COMMENT '操作状态：0 正常 / 1 异常',
    `error_msg`      varchar(2000) DEFAULT NULL COMMENT '错误消息',
    `cost_time`      bigint        DEFAULT 0 COMMENT '耗时（毫秒）',
    `oper_time`      datetime      DEFAULT NULL COMMENT '操作时间',
    `create_time`    datetime      DEFAULT NULL COMMENT '创建时间',
    `is_deleted`     tinyint(1)    NOT NULL DEFAULT 0 COMMENT '逻辑删除：0 未删除 / 1 已删除',
    PRIMARY KEY (`id`),
    KEY `idx_ol_time` (`oper_time`),
    KEY `idx_ol_oper` (`oper_id`, `oper_time`),
    KEY `idx_ol_business` (`business_type`, `status`)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COLLATE = utf8mb4_general_ci COMMENT = '操作日志表';

-- ---------------------------------------------------------------- 登录日志
CREATE TABLE IF NOT EXISTS `sys_login_log` (
    `id`         bigint       NOT NULL COMMENT '日志主键（雪花 ID）',
    `username`   varchar(64)  DEFAULT '' COMMENT '登录账号',
    `user_id`    bigint       DEFAULT NULL COMMENT '用户 ID，登录失败时为 NULL',
    `user_type`  tinyint      DEFAULT NULL COMMENT '主体类型：1 读者 / 2 作者 / 3 运营',
    `ip`         varchar(50)  DEFAULT '' COMMENT '登录 IP',
    `location`   varchar(255) DEFAULT '' COMMENT 'IP 归属地',
    `browser`    varchar(64)  DEFAULT '' COMMENT '浏览器',
    `os`         varchar(64)  DEFAULT '' COMMENT '操作系统',
    `status`     tinyint      DEFAULT 0 COMMENT '登录状态：0 成功 / 1 失败',
    `message`    varchar(255) DEFAULT '' COMMENT '提示消息，如「密码错误」「账号已停用」',
    `login_time` datetime     DEFAULT NULL COMMENT '登录时间',
    `create_time` datetime    DEFAULT NULL COMMENT '创建时间',
    `is_deleted` tinyint(1)   NOT NULL DEFAULT 0 COMMENT '逻辑删除：0 未删除 / 1 已删除',
    PRIMARY KEY (`id`),
    KEY `idx_ll_time` (`login_time`),
    KEY `idx_ll_user` (`user_id`, `login_time`),
    KEY `idx_ll_status` (`status`, `login_time`)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COLLATE = utf8mb4_general_ci COMMENT = '登录日志表';

-- ---------------------------------------------------------------- 在线用户审计
CREATE TABLE IF NOT EXISTS `sys_user_online` (
    `id`               bigint       NOT NULL COMMENT '会话主键（雪花 ID）',
    `token_id`         varchar(128) NOT NULL COMMENT '令牌 ID（JWT jti 或 Redis key 后缀），唯一索引',
    `user_id`          bigint       DEFAULT NULL COMMENT '用户 ID → sys_user.id',
    `username`         varchar(64)  DEFAULT '' COMMENT '登录账号',
    `nickname`         varchar(32)  DEFAULT '' COMMENT '用户昵称',
    `user_type`        tinyint      DEFAULT NULL COMMENT '主体类型：1 读者 / 2 作者 / 3 运营',
    `dept_id`          bigint       DEFAULT NULL COMMENT '部门 ID，C 端为空',
    `ip`               varchar(50)  DEFAULT '' COMMENT '登录 IP',
    `location`         varchar(255) DEFAULT '' COMMENT 'IP 归属地',
    `browser`          varchar(64)  DEFAULT '' COMMENT '浏览器',
    `os`               varchar(64)  DEFAULT '' COMMENT '操作系统',
    `device`           varchar(64)  DEFAULT '' COMMENT '设备类型：PC / H5 / APP / 小程序',
    `login_time`       datetime     DEFAULT NULL COMMENT '登录时间',
    `last_access_time` datetime     DEFAULT NULL COMMENT '最后访问时间',
    `expire_time`      datetime     DEFAULT NULL COMMENT '令牌过期时间',
    `status`           tinyint      DEFAULT 1 COMMENT '会话状态：0 已强退 / 1 在线',
    `is_deleted`       tinyint(1)   NOT NULL DEFAULT 0 COMMENT '逻辑删除：0 未删除 / 1 已删除',
    PRIMARY KEY (`id`),
    UNIQUE KEY `uk_online_token` (`token_id`),
    KEY `idx_online_user` (`user_id`),
    KEY `idx_online_status` (`status`, `last_access_time`)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COLLATE = utf8mb4_general_ci COMMENT = '在线用户审计表';
