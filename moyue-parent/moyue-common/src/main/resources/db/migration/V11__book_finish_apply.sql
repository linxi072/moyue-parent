-- V11: 作品完结申请审核流（P1-6）
-- 作者不能自行把作品置为「已完结」，须提交完结申请（status=0 待审核），
-- 由管理员（role=3）裁决：通过 → book.status 置 2（已完结）；驳回 → 记录审核意见。
-- 同一作品同时只允许存在一条在途（status=0）申请，由服务层保证。
CREATE TABLE IF NOT EXISTS `book_finish_apply` (
  `id`            BIGINT       NOT NULL                COMMENT '申请主键（雪花 ID）',
  `book_id`       BIGINT       NOT NULL                COMMENT '作品 ID → book.id',
  `author_id`     BIGINT       NOT NULL                COMMENT '申请人（作者）ID → user.id',
  `reason`        VARCHAR(500) DEFAULT NULL            COMMENT '完结申请理由（可空）',
  `status`        TINYINT      NOT NULL DEFAULT 0      COMMENT '审核状态：0 待审核 / 1 通过 / 2 驳回',
  `auditor_id`    BIGINT       DEFAULT NULL            COMMENT '审核人（管理员）ID → user.id',
  `audit_remark`  VARCHAR(255) DEFAULT NULL            COMMENT '审核意见（通过 / 驳回理由，可空）',
  `is_deleted`    TINYINT(1)   NOT NULL DEFAULT 0      COMMENT '逻辑删除：0 否 / 1 是',
  `create_time`   DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  `update_time`   DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
  PRIMARY KEY (`id`),
  KEY `idx_book_status` (`book_id`, `status`),
  KEY `idx_status_create` (`status`, `create_time`)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COMMENT = '作品完结申请表';
