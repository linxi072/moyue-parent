-- =============================================================
-- V7  积分获取渠道（P1-10）：签到记录 + 积分流水
-- 依赖：points_account（V4）
-- =============================================================

-- -------------------------------------------------------------
-- 23  points_check_in  每日签到记录表
-- -------------------------------------------------------------
DROP TABLE IF EXISTS `points_check_in`;
CREATE TABLE `points_check_in` (
  `id`             BIGINT     NOT NULL              COMMENT '主键',
  `user_id`        BIGINT     NOT NULL              COMMENT '用户 ID（主键之一）→ user.id',
  `check_in_date`  DATE       NOT NULL              COMMENT '签到日期 YYYY-MM-DD',
  `points`         INT        NOT NULL DEFAULT 0    COMMENT '本次签到发放积分',
  `is_deleted`     TINYINT(1) NOT NULL DEFAULT 0    COMMENT '逻辑删除',
  `create_time`    DATETIME   NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '签到时间',
  `update_time`    DATETIME   NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_user_date` (`user_id`, `check_in_date`)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COMMENT = '每日签到记录表';

-- -------------------------------------------------------------
-- 24  points_flow  积分流水表（只增不改的台账）
-- biz_type：1 签到 / 2 阅读时长 / 3 评论奖励 / 4 系统发放 / 5 兑换消费
-- points：正数为获得，负数为消费
-- -------------------------------------------------------------
DROP TABLE IF EXISTS `points_flow`;
CREATE TABLE `points_flow` (
  `id`          BIGINT       NOT NULL              COMMENT '主键',
  `user_id`     BIGINT       NOT NULL              COMMENT '用户 ID → user.id',
  `biz_type`    TINYINT      NOT NULL              COMMENT '业务类型：1 签到 / 2 阅读时长 / 3 评论奖励 / 4 系统发放 / 5 兑换消费',
  `points`      INT          NOT NULL              COMMENT '积分变动（正获得 / 负消费）',
  `remark`      VARCHAR(255) DEFAULT NULL          COMMENT '备注',
  `is_deleted`  TINYINT(1)   NOT NULL DEFAULT 0    COMMENT '逻辑删除',
  `create_time` DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '发生时间',
  `update_time` DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
  PRIMARY KEY (`id`),
  KEY `idx_user` (`user_id`)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COMMENT = '积分流水表（只增台账）';
