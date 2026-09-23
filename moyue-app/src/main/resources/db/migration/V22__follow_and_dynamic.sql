-- V22：P2-E 社区互动深化 —— 关注流 / 动态（关注关系 + 用户动态）
-- 物理表名遵循主理人裁决：follow_relation（关注关系）+ user_dynamic（动态）。
-- 逻辑删除逐实体显式 @TableLogic(value="0", delval="1")（全局未启用，不写会静默物理删）；
-- 两表均含 is_deleted TINYINT(1) NOT NULL DEFAULT 0。
-- 幂等：CREATE TABLE IF NOT EXISTS；改完重跑 python tools/gen-h2-schema.py（H2 索引名加表前缀全局唯一）。
-- feed 物化表本期不建（延后 V23，fan-out-on-read 走 DB 时间线聚合）。

CREATE TABLE IF NOT EXISTS `follow_relation` (
  `id`          BIGINT       NOT NULL                COMMENT '主键（雪花 ID）',
  `fan_id`      BIGINT       NOT NULL                COMMENT '粉丝（关注发起方）ID → user.id',
  `author_id`   BIGINT       NOT NULL                COMMENT '被关注作者 ID → user.id',
  `is_deleted`  TINYINT(1)   NOT NULL DEFAULT 0      COMMENT '逻辑删除：0 否 / 1 是',
  `create_time` DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  `update_time` DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_follow_pair` (`fan_id`, `author_id`),
  KEY `idx_follow_author` (`author_id`),
  KEY `idx_follow_fan` (`fan_id`),
  KEY `idx_follow_create` (`create_time`)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COMMENT = '关注关系表';

CREATE TABLE IF NOT EXISTS `user_dynamic` (
  `id`           BIGINT       NOT NULL                COMMENT '主键（雪花 ID）',
  `actor_user_id` BIGINT     DEFAULT NULL            COMMENT '动作发起者（打赏者 / 作者本人）ID → user.id',
  `actor_name`   VARCHAR(64)  DEFAULT NULL            COMMENT '反规范化快照：动作发起者昵称',
  `author_id`    BIGINT       NOT NULL                COMMENT '动态归属作者（进粉丝流的人）→ user.id',
  `author_name`  VARCHAR(64)  DEFAULT NULL            COMMENT '反规范化快照：作者昵称',
  `book_id`      BIGINT       DEFAULT NULL            COMMENT '关联作品 ID → book.id',
  `book_title`   VARCHAR(128) DEFAULT NULL            COMMENT '反规范化快照：作品标题',
  `dynamic_type` TINYINT      NOT NULL                COMMENT '动态类型：1 发布新作 / 2 作品完结 / 3 打赏 / 4 关注(预留)',
  `summary`      VARCHAR(255) DEFAULT NULL            COMMENT '可选补充文案（后端不拼全文，前端按 type+字段渲染）',
  `ref_id`       BIGINT       DEFAULT NULL            COMMENT '来源业务主键（bookId / rewardOrderId）',
  `ref_type`     TINYINT      NOT NULL                COMMENT '来源类型：1=book发布 / 2=reward订单',
  `is_deleted`   TINYINT(1)   NOT NULL DEFAULT 0      COMMENT '逻辑删除：0 否 / 1 是',
  `create_time`  DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '排序键',
  `update_time`  DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_dynamic_ref` (`ref_type`, `ref_id`),
  KEY `idx_dynamic_actor` (`actor_user_id`),
  KEY `idx_dynamic_author` (`author_id`),
  KEY `idx_dynamic_create` (`create_time`)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COMMENT = '用户动态表（关注流时间线 / 作者主页动态）';
