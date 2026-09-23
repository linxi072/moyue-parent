-- V20：分类服务独立化（P2-A）—— 新建 category 表
-- 说明：book.category_id 已于 V1__init.sql 落地（BookEntity#categoryId 第31行、V2 种子书已使用），
--       故本迁移不再重复 ALTER book，避免 H2（MySQL 兼容模式）直发 ALTER 时 'Column already exists' 失败；
--       book 含 category_id 的验收项已由 V1 满足。本迁移仅新增独立 category 表，幂等种子见 V21。
-- 幂等：CREATE TABLE IF NOT EXISTS（H2 兼容，生成器直发）。
-- 注：H2 测试库不直喂本脚本，由 tools/gen-h2-schema.py 生成等价 schema，此处语法以 MySQL 为准。
-- 关联需求：运营后台可对分类增删改排序；分类逻辑删除 @TableLogic(value="0", delval="1")（见 CategoryEntity）。

CREATE TABLE IF NOT EXISTS `category` (
  `id`          BIGINT       NOT NULL                COMMENT '分类主键（雪花 ID）',
  `name`        VARCHAR(50)  NOT NULL                COMMENT '分类名称',
  `icon`        VARCHAR(64)  DEFAULT NULL            COMMENT '分类图标（前端展示用）',
  `sort`        INT          NOT NULL DEFAULT 0      COMMENT '显示顺序（升序：书城筛选项排序）',
  `status`      TINYINT      DEFAULT 1               COMMENT '状态：0 禁用 / 1 正常',
  `is_deleted`  TINYINT(1)   NOT NULL DEFAULT 0      COMMENT '逻辑删除：0 否 / 1 是（@TableLogic）',
  `create_time` DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  `update_time` DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
  PRIMARY KEY (`id`)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COMMENT = '小说分类表（运营后台可增删改排序）';
