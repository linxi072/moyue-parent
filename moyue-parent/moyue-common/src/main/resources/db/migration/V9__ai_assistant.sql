-- =============================================================
-- V9  AI 智能客服（moyue-ai）：会话 + 消息
-- 回复由 AiReplyEngine 生成（内置关键词规则引擎，后续可替换为
-- 真实大模型适配器），会话/消息仅做记录留存。
-- =============================================================

-- -------------------------------------------------------------
-- 28  ai_session  客服会话表
-- -------------------------------------------------------------
DROP TABLE IF EXISTS `ai_session`;
CREATE TABLE `ai_session` (
  `id`          BIGINT       NOT NULL              COMMENT '会话主键',
  `user_id`     BIGINT       NOT NULL              COMMENT '用户 ID → user.id',
  `title`       VARCHAR(128) DEFAULT NULL           COMMENT '会话标题（默认取首条提问截断）',
  `is_deleted`  TINYINT(1)   NOT NULL DEFAULT 0    COMMENT '逻辑删除',
  `create_time` DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  `update_time` DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
  PRIMARY KEY (`id`),
  KEY `idx_user` (`user_id`)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COMMENT = 'AI 客服会话表';

-- -------------------------------------------------------------
-- 29  ai_message  客服消息表
-- role：1 用户提问 / 2 助手回复
-- -------------------------------------------------------------
DROP TABLE IF EXISTS `ai_message`;
CREATE TABLE `ai_message` (
  `id`          BIGINT        NOT NULL              COMMENT '消息主键',
  `session_id`  BIGINT        NOT NULL              COMMENT '会话 ID → ai_session.id',
  `role`        TINYINT       NOT NULL DEFAULT 1    COMMENT '角色：1 用户 / 2 助手',
  `content`     VARCHAR(2000) NOT NULL              COMMENT '消息内容',
  `is_deleted`  TINYINT(1)    NOT NULL DEFAULT 0    COMMENT '逻辑删除',
  `create_time` DATETIME      NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '发送时间',
  `update_time` DATETIME      NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
  PRIMARY KEY (`id`),
  KEY `idx_session` (`session_id`)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COMMENT = 'AI 客服消息表';
