-- =============================================================
-- V6  公告表（新建） + reward_order 逻辑删除列补齐
--
-- 背景：
--   1) reward_order（V1 建表）缺 is_deleted 列，而实体 RewardOrderEntity 含
--      isDeleted 字段，且各服务全局配置 logic-delete-field: isDeleted 生效
--      → 任何对 reward_order 的 MyBatis-Plus 查询都会追加 is_deleted = 0
--      条件，命中 Unknown column 'is_deleted' 而报错（现有 /admin/announcements
--      占位接口即因此不可用）。此处补齐该列，存量数据默认未删除。
--   2) 运营公告此前无对应表、仅有占位实现，此处新建 announcement 表。
--
-- 约定：create_time · update_time 全表统一；is_deleted 逻辑删除
-- =============================================================

SET FOREIGN_KEY_CHECKS = 0;

-- -------------------------------------------------------------
-- 1) reward_order 补齐逻辑删除列
-- -------------------------------------------------------------
ALTER TABLE `reward_order`
  ADD COLUMN `is_deleted` TINYINT(1) NOT NULL DEFAULT 0 COMMENT '逻辑删除' AFTER `pay_time`;

-- -------------------------------------------------------------
-- 2) announcement  运营公告表
-- -------------------------------------------------------------
DROP TABLE IF EXISTS `announcement`;
CREATE TABLE `announcement` (
  `id`           BIGINT       NOT NULL                 COMMENT '公告主键',
  `title`        VARCHAR(128) NOT NULL                 COMMENT '标题',
  `content`      TEXT         NOT NULL                 COMMENT '正文',
  `type`         TINYINT      NOT NULL DEFAULT 1       COMMENT '类型：1 站内公告 / 2 活动 / 3 系统维护',
  `status`       TINYINT      NOT NULL DEFAULT 0       COMMENT '状态：0 草稿 / 1 已发布 / 2 已下线',
  `is_top`       TINYINT(1)   NOT NULL DEFAULT 0       COMMENT '是否置顶：0 否 / 1 是',
  `publish_time` DATETIME     DEFAULT NULL             COMMENT '发布时间',
  `is_deleted`   TINYINT(1)   NOT NULL DEFAULT 0       COMMENT '逻辑删除',
  `create_time`  DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  `update_time`  DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
  PRIMARY KEY (`id`),
  KEY `idx_status` (`status`),
  KEY `idx_top_time` (`is_top`, `publish_time`)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COMMENT = '运营公告表';

SET FOREIGN_KEY_CHECKS = 1;
