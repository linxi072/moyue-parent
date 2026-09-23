-- V24：P2-C 风控反作弊·行为层（行为事件 risk_behavior_event + 规则配置 risk_rule + 决策 risk_decision）
-- 行为事件与决策为不可变追加日志（与 audit_task 一致，不承载软删语义），故无 is_deleted；
-- 规则配置 risk_rule 为可变配置，含 is_deleted TINYINT(1) NOT NULL DEFAULT 0（逐实体显式软删）。
-- 幂等：CREATE TABLE IF NOT EXISTS；改完重跑 python tools/gen-h2-schema.py（H2 索引名加表前缀全局唯一）。

CREATE TABLE IF NOT EXISTS `risk_behavior_event` (
  `id`          BIGINT       NOT NULL                COMMENT '主键（雪花 ID）',
  `user_id`     BIGINT       NOT NULL                COMMENT '行为用户 ID → user.id',
  `device_id`   VARCHAR(64)  DEFAULT NULL            COMMENT '设备指纹 / 设备 ID',
  `event_type`  VARCHAR(32)  NOT NULL                COMMENT '事件类型：LOGIN / SIGN_IN / REDEEM / REWARD / PUBLISH',
  `biz_id`      BIGINT       DEFAULT NULL            COMMENT '关联业务主键',
  `ip`          VARCHAR(64)  DEFAULT NULL            COMMENT '来源 IP',
  `risk_score`  INT          NOT NULL DEFAULT 0      COMMENT '风险分（预留）',
  `create_time` DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '采集时间',
  PRIMARY KEY (`id`),
  KEY `idx_behavior_event_user` (`user_id`),
  KEY `idx_behavior_event_device` (`device_id`),
  KEY `idx_behavior_event_type` (`event_type`),
  KEY `idx_behavior_event_create` (`create_time`)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COMMENT = '用户行为事件表（风控采集源）';

CREATE TABLE IF NOT EXISTS `risk_rule` (
  `id`          BIGINT       NOT NULL                COMMENT '主键（雪花 ID）',
  `rule_code`   VARCHAR(32)  NOT NULL                COMMENT '规则编码（全局唯一）',
  `rule_name`   VARCHAR(64)  NOT NULL                COMMENT '规则名称',
  `rule_type`   VARCHAR(32)  NOT NULL                COMMENT 'FREQ / DEVICE_MULTI_ACCOUNT / POINTS_ANOMALY / ACCOUNT_THEFT',
  `enabled`     TINYINT(1)   NOT NULL DEFAULT 1      COMMENT '是否启用：0 关 / 1 开',
  `action`      VARCHAR(16)  NOT NULL                COMMENT 'LIMIT / MARK / REVIEW / BLOCK',
  `config_json` VARCHAR(512) DEFAULT NULL            COMMENT '规则参数 JSON（windowMinutes / threshold）',
  `is_deleted`  TINYINT(1)   NOT NULL DEFAULT 0      COMMENT '逻辑删除：0 否 / 1 是',
  `create_time` DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  `update_time` DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_risk_rule_code` (`rule_code`)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COMMENT = '风控规则配置表';

CREATE TABLE IF NOT EXISTS `risk_decision` (
  `id`          BIGINT       NOT NULL                COMMENT '主键（雪花 ID）',
  `user_id`     BIGINT       DEFAULT NULL            COMMENT '命中用户 ID',
  `device_id`   VARCHAR(64)  DEFAULT NULL            COMMENT '命中设备 ID',
  `rule_code`   VARCHAR(32)  DEFAULT NULL            COMMENT '命中规则编码',
  `event_type`  VARCHAR(32)  DEFAULT NULL            COMMENT '触发事件类型',
  `decision`    VARCHAR(16)  NOT NULL                COMMENT 'PASS / REVIEW / BLOCK',
  `risk_level`  TINYINT      NOT NULL DEFAULT 0      COMMENT '风险等级：0 无 / 1 低 / 2 中 / 3 高',
  `message`     VARCHAR(255) DEFAULT NULL            COMMENT '说明',
  `create_time` DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '决策时间',
  PRIMARY KEY (`id`),
  KEY `idx_decision_user` (`user_id`),
  KEY `idx_decision_create` (`create_time`),
  KEY `idx_decision_rule` (`rule_code`)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COMMENT = '风控决策 / 处置记录表';
