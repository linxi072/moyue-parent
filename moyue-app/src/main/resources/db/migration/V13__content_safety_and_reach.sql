-- V13：内容安全（敏感词 / 举报）+ 消息触达（模板 / 触达记录）（P2-13~17）
-- 说明：notice 表沿用 V3，不改结构；audit_task 沿用 V1/V10，不改结构
--       （biz_type 由 TINYINT 承载新语义：1 章节 / 2 评论 / 3 书籍 / 4 用户）。

CREATE TABLE IF NOT EXISTS `sensitive_word` (
  `id`          BIGINT       NOT NULL                COMMENT '主键（雪花 ID）',
  `word`        VARCHAR(64)  NOT NULL                COMMENT '敏感词',
  `level`       TINYINT      NOT NULL DEFAULT 1      COMMENT '等级：1 拦截 / 2 告警（转人工）',
  `category`    VARCHAR(32)  DEFAULT NULL            COMMENT '分类：政治/广告/谩骂/涉黄…',
  `status`      TINYINT      NOT NULL DEFAULT 1      COMMENT '状态：0 停用 / 1 启用',
  `is_deleted`  TINYINT(1)   NOT NULL DEFAULT 0      COMMENT '逻辑删除',
  `create_time` DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP,
  `update_time` DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_word` (`word`),
  KEY `idx_status_level` (`status`, `level`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='敏感词库';

CREATE TABLE IF NOT EXISTS `report` (
  `id`            BIGINT       NOT NULL              COMMENT '举报主键（雪花 ID）',
  `reporter_id`   BIGINT       NOT NULL              COMMENT '举报人 → user.id',
  `target_type`   TINYINT      NOT NULL              COMMENT '对象：1 书籍 / 2 章节 / 3 评论 / 4 用户',
  `target_id`     BIGINT       NOT NULL              COMMENT '对象主键',
  `reason_type`   TINYINT      NOT NULL DEFAULT 1    COMMENT '原因：1 违规内容 / 2 广告 / 3 侵权 / 4 其他',
  `reason`        VARCHAR(500) DEFAULT NULL          COMMENT '补充说明',
  `status`        TINYINT      NOT NULL DEFAULT 0    COMMENT '状态：0 待处理 / 1 属实 / 2 驳回',
  `handler_id`    BIGINT       DEFAULT NULL          COMMENT '处理人 → 管理员 ID',
  `handle_remark` VARCHAR(255) DEFAULT NULL          COMMENT '处理意见',
  `handle_time`   DATETIME     DEFAULT NULL          COMMENT '处理时间',
  `is_deleted`    TINYINT(1)   NOT NULL DEFAULT 0    COMMENT '逻辑删除',
  `create_time`   DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP,
  `update_time`   DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  PRIMARY KEY (`id`),
  KEY `idx_status` (`status`),
  KEY `idx_target` (`target_type`, `target_id`),
  KEY `idx_reporter` (`reporter_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='举报表';

CREATE TABLE IF NOT EXISTS `message_template` (
  `id`          BIGINT        NOT NULL,
  `code`        VARCHAR(64)   NOT NULL              COMMENT '模板编码，如 AUDIT_PASS / REPORT_RESULT',
  `name`        VARCHAR(64)   NOT NULL              COMMENT '模板名称',
  `title_tpl`   VARCHAR(255)  DEFAULT NULL          COMMENT '标题模板，占位符 {param}',
  `content_tpl` VARCHAR(1000) NOT NULL              COMMENT '内容模板',
  `channels`    VARCHAR(64)   NOT NULL DEFAULT '1'  COMMENT '默认渠道，逗号分隔：1 站内信/2 邮件/3 短信/4 推送',
  `status`      TINYINT       NOT NULL DEFAULT 1    COMMENT '状态：0 停用 / 1 启用',
  `is_deleted`  TINYINT(1)    NOT NULL DEFAULT 0,
  `create_time` DATETIME      NOT NULL DEFAULT CURRENT_TIMESTAMP,
  `update_time` DATETIME      NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_code` (`code`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='消息模板表';

CREATE TABLE IF NOT EXISTS `message_channel_record` (
  `id`            BIGINT        NOT NULL,
  `user_id`       BIGINT        NOT NULL            COMMENT '接收用户 → user.id',
  `channel`       TINYINT       NOT NULL            COMMENT '渠道：1 站内信/2 邮件/3 短信/4 推送',
  `template_code` VARCHAR(64)   DEFAULT NULL        COMMENT '模板编码',
  `target`        VARCHAR(128)  DEFAULT NULL        COMMENT '投递地址（邮箱/手机号）',
  `title`         VARCHAR(128)  DEFAULT NULL,
  `content`       VARCHAR(1000) DEFAULT NULL,
  `biz_type`      VARCHAR(64)   DEFAULT NULL        COMMENT '业务类型，如 AUDIT / REPORT',
  `biz_id`        BIGINT        DEFAULT NULL        COMMENT '业务主键',
  `status`        TINYINT       NOT NULL DEFAULT 0  COMMENT '0 待发 / 1 成功 / 2 失败',
  `error_msg`     VARCHAR(255)  DEFAULT NULL,
  `retry_count`   INT           NOT NULL DEFAULT 0,
  `send_time`     DATETIME      DEFAULT NULL,
  `is_deleted`    TINYINT(1)    NOT NULL DEFAULT 0,
  `create_time`   DATETIME      NOT NULL DEFAULT CURRENT_TIMESTAMP,
  `update_time`   DATETIME      NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  PRIMARY KEY (`id`),
  KEY `idx_user` (`user_id`),
  KEY `idx_status` (`status`),
  KEY `idx_template` (`template_code`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='消息触达记录表';

-- 种子：敏感词（分级）
INSERT IGNORE INTO `sensitive_word` (`id`,`word`,`level`,`category`) VALUES
 (910000000000000001,'示例敏感词A',1,'政治'),
 (910000000000000002,'示例敏感词B',1,'广告'),
 (910000000000000003,'示例灰词C',2,'谩骂');

-- 种子：消息模板
INSERT IGNORE INTO `message_template` (`id`,`code`,`name`,`title_tpl`,`content_tpl`,`channels`) VALUES
 (920000000000000001,'AUDIT_PASS','审核通过','{bizName}审核通过','您提交的{bizName}已通过审核。','1,2'),
 (920000000000000002,'AUDIT_REJECT','审核驳回','{bizName}审核驳回','您提交的{bizName}未通过审核：{reason}','1,2'),
 (920000000000000003,'REPORT_RESULT','举报处理结果','您的举报已处理','您对{targetDesc}的举报处理结果：{result}。','1,2');
