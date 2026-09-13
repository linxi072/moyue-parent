-- Flyway V3：站内信 / 通知表（message 服务专属）
CREATE TABLE IF NOT EXISTS `notice` (
  `id`          BIGINT       NOT NULL                COMMENT '通知主键',
  `user_id`     BIGINT       NOT NULL                COMMENT '接收用户 → user.id',
  `title`       VARCHAR(128) NOT NULL                COMMENT '标题',
  `content`     VARCHAR(500) DEFAULT NULL            COMMENT '内容',
  `type`        TINYINT      NOT NULL DEFAULT 1      COMMENT '类型：1 系统 / 2 互动 / 3 审核',
  `is_read`     TINYINT(1)   NOT NULL DEFAULT 0      COMMENT '是否已读',
  `create_time` DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  PRIMARY KEY (`id`),
  KEY `idx_user` (`user_id`)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COMMENT = '站内信/通知表';
