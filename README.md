# 墨阅小说网（Moyue Novel）后端

基于 **Spring Boot 3.2.12** 的**单体**小说平台——单体化后由单个 `moyue-app` 模块承载全部业务域。

> 架构演进：早期为 22 模块微服务（RuoYi-Cloud 风格，Nacos + Gateway + OpenFeign），已重构为单体——
> 移除 Spring Cloud / OpenFeign / Nacos 注册发现 / Gateway，跨域调用改为进程内 Service 直接注入；
> HTTP 路径 `/api/v1/**` 与包名 `com.moyue.*` 保持不变（前端契约不受影响）。

## 一、模块总览（单体：1 个 Maven 模块）

```
moyue-parent/                      # git 仓库根
└── moyue-parent/                  # 工程根（本 README 所在目录）
    ├── moyue-app/                 # 单体应用（com.moyue.* 全部业务域）
    ├── moyue-frontend/            # 前端脚手架（Vite + TS，直连 /api/v1）
    ├── docs/                      # 设计 / 运维 / 迭代文档
    ├── scripts/                   # build.sh / start-all.sh / smoke-test.sh
    ├── tools/                     # gen-h2-schema.py 等
    ├── pom.xml                    # 单体父 POM（单模块 moyue-app）
    ├── .env.example
    └── README.md
```

业务域（包名 `com.moyue.<域>`，统一 `@ComponentScan("com.moyue")` + `@MapperScan("com.moyue")`）：

| 域 | 包 | 说明 |
|---|---|---|
| 认证 | `com.moyue.auth` | 登录 / 注册 / 刷新令牌 |
| 账号 | `com.moyue.user` / `com.moyue.account` | 用户资料 |
| 内容 | `com.moyue.book` / `com.moyue.chapter` / `com.moyue.read` / `com.moyue.category` | 书城 / 章节 / 阅读书架 / 封面文件 |
| 社交 | `com.moyue.blog` / `com.moyue.comment` / `com.moyue.im` / `com.moyue.follow` / `com.moyue.dynamic` | 博客 / 评论 / IM / 关注 / 动态 |
| 商业 | `com.moyue.points` / `com.moyue.merch` / `com.moyue.author` / `com.moyue.operation` | 积分 / 商城 / 作者稿酬 / 运营 |
| 检索 | `com.moyue.search` | 全文检索 / 推荐（只读 ES） |
| 触达 | `com.moyue.message` | 站内信 + 渠道 SPI（邮件真实现，短信/推送桩） |
| 内容安全 | `com.moyue.risk` / `com.moyue.audit` / `com.moyue.report` / `com.moyue.sensitive` | 审核 / 举报 / 敏感词 / 机审 |
| 会员 | `com.moyue.member` | 订阅 / 会员体系（P2-B） |
| 智能 | `com.moyue.ai` | AI 客服 |
| 平台 | `com.moyue.system` / `com.moyue.stat` | RBAC / 统计 |

## 二、环境要求

| 组件 | 版本 | 说明 |
|---|---|---|
| JDK | 21（target 17） | 编译与运行 |
| Maven | 3.9+ | 裸跑可编译（不依赖已删除的 `_bootstrap/settings.xml`） |
| MySQL | 8.x | Flyway 自动迁移 |
| Redis | 7.x | 缓存 / IM / refreshToken |
| Elasticsearch | 8.13.4 | 检索域（可选） |
| XXL-Job Admin | 2.4.0 | 调度中心（可选，8088）；执行器随单体进程启动（9099） |

> Nacos 已不再使用（单体无注册中心）。

## 三、环境变量（`.env.example`）

| 变量 | 用途 | 默认（dev） |
|---|---|---|
| `MYSQL_HOST/PORT/DB/USERNAME/PASSWORD` | 数据源 | localhost:3306/moyue，dev 口令 root |
| `REDIS_HOST/PORT` | Redis | localhost:6379 |
| `MOYUE_JWT_SECRET` | JWT 签名密钥（单体应用） | dev 有默认值，**test/prod 必须注入** |
| `ES_URIS` | Elasticsearch | http://localhost:9200 |
| `MOYUE_MAIL_HOST/PORT/USERNAME/PASSWORD/FROM` | SMTP（触达域） | MailHog localhost:1025 |
| `XXL_JOB_ADMIN_ADDRESSES` | 调度中心 | http://localhost:8088/xxl-job-admin |
| `SPRING_PROFILES_ACTIVE` | dev / test / prod | dev |

## 四、构建与启动

```bash
# 在本工程根（moyue-parent/moyue-parent）执行
mvn clean package -DskipTests
# 或一键脚本
./scripts/build.sh && ./scripts/start-all.sh
```

启动：单体 `moyue-app` 一个进程，直接监听 **:8080**。
基础设施：**原生安装**（MySQL / Redis / Elasticsearch / MailHog）。⚠️ 项目硬性约束：**禁用 Docker**，详见 [docs/不可忽视条件.md](docs/不可忽视条件.md)。

验证：`./scripts/smoke-test.sh`（单体直连 :8080 的全链路冒烟）。

## 五、路由（单体直连）

统一入口 `http://localhost:8080`，单体内部校验 Bearer token 后注入 `X-User-Id` / `X-User-Role`；白名单配置化（`moyue.auth.whitelist`）：

```yaml
moyue:
  auth:
    whitelist: /api/v1/auth/login,/api/v1/auth/register,/api/v1/auth/refresh,/api/v1/system/login
    whitelist-prefixes: /api/v1/files/
```

所有 `/api/v1/**` 由 `moyue-app` 在 8080 直接处理（不再有网关按服务名路由）。

## 六、鉴权体系（双层防线）

1. **路径级默认拒绝**：`AdminRoleInterceptor` 拦截 `/api/v1/admin/**`（仅 role=3）；
2. **注解级细粒度控制**（`moyue-common-security`）：`@RequiresRoles` / `@RequiresPermissions` + `PreAuthorizeAspect`。

## 七、跨域调用（进程内）

单体化后删除全部 `@FeignClient`，改为直接注入目标 `Service`（如 `UserClient` → `UserService`、
`BookClient` → `BookService`）。原 `moyue-api/*` 契约模块已删除；`/api/v1/internal/**` 仅供进程内 Service 互调。

## 八、Flyway 迁移

`classpath:db/migration` 下 V1–Vn 脚本，全域共享。详见 [docs/运维部署手册.md](docs/运维部署手册.md)。

## 九、路线图状态（P0→P2 已全部交付）

| 项 | 状态 |
|---|---|
| P2-B 会员/订阅 | ✅ 订阅状态机 + 支付 Stub + H2 集成测试 |
| P2-C 内容安全反作弊 | ✅ 配置化行为风控 + 复用触达 + H2 集成测试 |
| P2-13 搜索与推荐 | ✅ ES 分类筛选 / 排序 / 热门推荐 |
| P2-14 消息触达 | ✅ ChannelSender SPI + 站内信/邮件真实现 + 短信/推送桩 |
| P2-15 内容安全 | ✅ 人工审核 + 敏感词/机审/举报 |
| P2-16 缓存与性能 | ✅ Redis 缓存 + 限流熔断 |
| P2-17 配置治理 | ✅ dev/test/prod 隔离 + 密钥环境变量化 + 白名单配置化 |
| 单体化重构 | ✅ 移除 Spring Cloud / OpenFeign / Nacos / Gateway，单模块 moyue-app |
