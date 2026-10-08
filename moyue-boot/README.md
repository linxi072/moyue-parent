# 墨阅小说网 · 后端单体（moyue-boot）

「墨阅小说网」后端工程的 **Spring Boot 单体形态**：一个 jar 聚合全部业务模块，开箱即跑，适合本地开发、联调与演示。

> 本仓库与 [`moyue-cloud`](https://github.com/linxi072/moyue-cloud) 为**双形态、共享源码**关系——所有模块源码逐字节一致，由工程根目录的 `sync.sh` 校验（一致文件 / 差异 0）。微服务形态请见 `moyue-cloud`。

## 技术栈

- Spring Boot 3.2.5 / Spring Cloud 2023.0.1 / Spring Security 6.2.4
- MyBatis-Plus 3.5.7 / Flyway（V12–V27）
- MySQL 8 + Redis 7（容器化）
- JWT（HS256）

## 模块一览

| 模块 | 说明 |
| --- | --- |
| `moyue-auth` | 认证（登录 / JWT 签发 / 登录日志） |
| `moyue-system` | 系统管理（用户 / 角色 / 菜单 / 部门 / 字典 / 配置 / 操作日志 / 在线用户） |
| `moyue-content` | 内容（小说 `moyue_book`：CRUD + 热门榜） |
| `moyue-social` | 社区（帖子 `moyue_community_post`：CRUD + 运营加热点赞） |
| `moyue-commerce` | 交易（支付单 `moyue_pay_order`：CRUD + 订单概览） |
| `moyue-search` | 搜索（热词 `moyue_search_hot_word`：CRUD + 热词榜） |
| `moyue-message` | 消息（站内信 `moyue_message`：CRUD + 未读计数） |
| `moyue-risk` | 风控（审核工单 `moyue_audit_record`：CRUD + 通过/驳回） |
| `moyue-ai` | AI（任务 `moyue_ai_task`：CRUD + 任务概览） |
| `moyue-common/*` | 公共组件（core / security / mybatis / redis / log / migration 等） |

## 快速启动

```bash
# 1. 起中间件（MySQL 8 / Redis 7）
docker run -d --name moyue-mysql -p 3306:3306 -e MYSQL_ROOT_PASSWORD=root mysql:8
docker run -d --name moyue-redis -p 6379:6379 redis:7-alpine

# 2. 打包并启动单体（首次启动 Flyway 自动建库 V12–V27）
mvn -Plocal-jdk20 -DskipTests package
java -jar moyue-boot/target/moyue-boot.jar

# 3. 登录（默认账号）
curl -X POST http://127.0.0.1:8080/api/v1/system/login \
  -H 'Content-Type: application/json' \
  -d '{"username":"admin","password":"123456"}'
```

## 默认账号

- 管理员：`admin / 123456`（超管，角色 `ROLE_ADMIN`）

## 说明

- 启动期暴露并修复的真实缺陷（打包 repackage、Nacos import-check、超管标识统一、Bean 冲突、MapperScan、字符集、Redisson 空密码、XXL-Job、Security 链放行、JWT superAdmin claim、SQL 口径、`-parameters`、迁移缺列）见开发计划与《架构缺口补齐说明》。
- 详情见项目资产文档（腾讯文档「墨阅小说网 · 项目资产」）。
