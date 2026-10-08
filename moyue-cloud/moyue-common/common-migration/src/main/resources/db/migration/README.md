# 数据库迁移脚本（Flyway）

目录：`moyue-common/common-migration/src/main/resources/db/migration`
所有服务与两个仓库（`moyue-cloud` / `moyue-boot`）**共享同一套迁移脚本**，避免多服务各自建表产生漂移（架构说明书 ADR-5）。

## 本轮已落地的版本

| 版本 | 文件 | 内容 | 新增表 |
| --- | --- | --- | --- |
| V12 | `V12__system_rbac.sql` | 系统管理（组织权限）基础表 | `sys_dept` `sys_user` `sys_role` `sys_menu` `sys_user_role` `sys_role_menu` `sys_role_dept`（7） |
| V14 | `V14__dict_and_config.sql` | 字典与参数管理 | `sys_dict_type` `sys_dict_data` `sys_config`（3） |
| V15 | `V15__user_domain_merge.sql` | 用户域合并（`user` → `sys_user`） | 0（DDL + 数据迁移） |
| V16 | `V16__audit_log.sql` | 审计日志三域 | `sys_oper_log` `sys_login_log` `sys_user_online`（3） |
| V17 | `V17__lowcode.sql` | 低代码两域 | `gen_table` `gen_table_column` `sys_form` `sys_form_item`（4） |

本轮合计 **17 张表**（47 表总盘中的 V5.0 增量部分 + V4.0 的系统管理/字典参数部分）。

## 尚未纳入本轮的版本

V1–V11、V13 属于既有历史迁移（作品 / 章节 / 评论 / 打赏 / 书架 / 积分 / 商城 / AI 客服 / 审核 / 内容安全 / 触达等），
见架构说明书 6.4。这些脚本由既有工程维护，本脚手架未重建，**从零建库时 V12 及之后的脚本可独立执行**
（表之间无跨版本外键依赖到缺失表；V15 对 `user` 表的迁移为条件执行，表不存在时自动跳过）。

接手历史库时，请先用 Flyway `baseline` 打基线，再应用 V12+。

## 约定

- 主键统一 `bigint` 雪花 ID，字段名一律 `id`，与 `common-mybatis` 的 `BaseEntity` 对齐
- 逻辑删除列 `is_deleted tinyint(1) DEFAULT 0`，**所有表必备**，与 MyBatis-Plus `@TableLogic` 对齐
- 审计列 `create_by` / `create_time` / `update_by` / `update_time`，由 `MetaObjectFillHandler` 自动填充
- 每个字段必须带 `COMMENT`，枚举值含义直接写进注释（架构说明书 6.5）
- `sys_user.username` 唯一索引：C 端用户请置 `NULL`，**不要写空串**（空串会触发唯一键冲突）

## 数据权限枚举（缺口 G-2 裁定）

`sys_role.data_scope`：

| 值 | 含义 | 作用域来源 |
| --- | --- | --- |
| 1 | 全部数据 | — |
| 2 | 自定义数据 | `sys_role_dept` 关联表 |
| 3 | 本部门 | `sys_user.dept_id` |
| 4 | 本部门及以下 | `sys_dept.ancestors` 前缀匹配 |
| 5 | 仅本人 | `create_by = 当前用户 ID` |
