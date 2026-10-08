# 墨阅小说网 · 后端微服务（moyue-cloud）

「墨阅小说网」后端工程的 **Spring Cloud 微服务形态**：Nacos 注册发现 + 网关路由 + Feign 跨模块调用。适合生产部署与水平扩展。

> 本仓库与 [`moyue-boot`](https://github.com/linxi072/moyue-boot) 为**双形态、共享源码**关系——所有模块源码逐字节一致，由工程根目录的 `sync.sh` 校验（一致文件 / 差异 0）。单体形态请见 `moyue-boot`。

## 技术栈

- Spring Boot 3.2.5 / Spring Cloud 2023.0.1 / Spring Security 6.2.4
- MyBatis-Plus 3.5.7 / Flyway（V12–V27）
- Nacos 注册发现 / Spring Cloud Gateway / OpenFeign
- MySQL 8 + Redis 7（容器化）
- JWT（HS256）

## 模块一览

| 模块 | 说明 |
| --- | --- |
| `moyue-gateway` | 网关（JWT 鉴权全局过滤器、路由转发） |
| `moyue-auth` | 认证服务 |
| `moyue-system` | 系统管理服务 |
| `moyue-content` | 内容服务（小说 + 热门榜） |
| `moyue-social` | 社区服务（帖子 + 运营加热） |
| `moyue-commerce` | 交易服务（支付单 + 订单概览） |
| `moyue-search` | 搜索服务（热词 + 热词榜） |
| `moyue-message` | 消息服务（站内信 + 未读计数） |
| `moyue-risk` | 风控服务（审核工单 + 通过/驳回） |
| `moyue-ai` | AI 服务（任务 + 任务概览） |
| `moyue-common/*` | 公共组件 |

## 与 moyue-boot 的差异

- 单体：单个 `BootApplication` 排除各模块启动类，聚合运行。
- 微服务：各模块独立 `XxxApplication` 启动类，经 Nacos 注册，网关统一入口。
- 共享 `moyue-modules/*/src` 与 `moyue-common/*/src` 两仓完全一致。

## 快速启动

```bash
# 1. 起中间件（MySQL 8 / Redis 7 / Nacos）
# 2. 启动各服务（先 common 依赖，再 gateway）
mvn -Plocal-jdk20 -DskipTests package
# 依次启动 auth / system / 各业务模块 / gateway
java -jar moyue-gateway/target/moyue-gateway.jar
```

## 默认账号

- 管理员：`admin / 123456`

## 说明

- Cloud 形态的端到端（Nacos/网关/Feign）需在真实环境跑通验证，是下一步最值得覆盖的风险面。
- 详情见项目资产文档（腾讯文档「墨阅小说网 · 项目资产」）。
