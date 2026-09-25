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

`classpath:db/migration` 下 **V1–V24** 脚本，全域共享（含 P0 稿酬/定时发布、P1 触达/会员、P2 分类/关注动态/行为风控等幂等迁移）。
H2 测试库由 `tools/gen-h2-schema.py` 依据此序列生成，保证测试与 MySQL 结构一致。详见 [docs/运维部署手册.md](docs/运维部署手册.md)。

> 检索域依赖运行中 Elasticsearch（8.13.4+I K），建有三套索引 **`moyue_book` / `moyue_chapter` / `moyue_qa`**；ES 不可用时检索降级不阻断主链路。

## 九、路线图状态（P0→P2）

> 状态以实际落地提交为准（详见 `docs/后续迭代更新计划.md` §4 落地追踪）。编号沿用该文档 P0/P1/P2 体系。

### P0 阶段（已交付）
| 项 | 内容 |
|---|---|
| P0-1 稿酬结算与打款闭环 | settlement_order 状态机 + 订阅/买断分成 + 打款 Stub（不回退） |
| P0-2 章节定时发布调度闭环 | ChapterPublishJobHandler + XXL-Job 接管 1→2 |
| P0-3 测试基线 | 4 个 @Disabled 流程测试 H2 化 + 全模块单测 |

### P1 阶段（已交付）
| 项 | 内容 |
|---|---|
| P1-1 AI 客服真实化 | LLM 引擎 + RAG 召回 + 多轮 + 转人工（缺 Key 降级关键字引擎） |
| P1-2 触达渠道补全 | 短信/推送真实外呼（默认降级）+ 邮件解析 + UserDTO 联系字段 |
| P1-3 推荐个性化 | 规则画像版 |
| P1-4 内容安全处置闭环 + 看板 | 机审(AC)/敏感词热刷/举报/处置双向通知/统计看板（已真实落地，非桩） |
| P1-5 检索质量调优 | 同义词/纠错 |

### P2 阶段（已交付）
| 项 | 内容 |
|---|---|
| P2-A 分类服务独立化 | category 表 + 后台 CRUD + BookClient(contextId) + book ES 含 categoryId；去硬编码 |
| P2-C 风控反作弊·行为层 | BehaviorRiskClient 非阻断埋点（LOGIN/SIGN_IN/REDEEM/REWARD/PUBLISH） |
| P2-D 书架实时同步 | 内容域事件 → 社区域 WebSocket 单播通道 |
| P2-G 可观测性增强 | TraceId 串联 + Prometheus 指标 + 单行 JSON 日志（common-observability 聚合模块） |
| P2-I 网关直连防护 | CidrFilter + InternalAuthInterceptor（X-Service-Token），上生产前置 |
| P2-L 智能朗读（TTS） | 听书进度 + 配置驱动降级（SERVICE_DEGRADED） |
| P2-B 会员/订阅（部分） | member 订阅状态机 + 支付 Stub（V23）；权益框架待深化 |
| P2-E 社区（部分） | follow/dynamic 表（V22）；关注流/动态聚合待深化 |

### P2 阶段（本批已交付）
| 项 | 内容 | 状态 |
|---|---|---|
| P2-H 跨域共享表解耦收尾 | author_income 收敛为单写方(operation) + AuthorIncomeService 收口跨域读取 | ✅ `e5bc7a8` |
| P2-J 文档与代码对齐 | 本文档路线图/Flyway/索引描述修正 | ✅ `1c9075f` |
| P2-K 测试覆盖补齐 | IM + 作者跨域稿酬降级单测（22 例），全量 418 例 0 失败 | ✅ `40df24c` |
| P2-F 前端页面与联调 | moyue-frontend 五页（登录/书城/详情+目录/阅读器/AI 客服）消费 /api/v1 契约，tsc + vite build 通过 | ✅ `1cb6c0f` |

### 单体化重构（已交付）
移除 Spring Cloud / OpenFeign / Nacos / Gateway，单模块 `moyue-app`；跨域调用改为进程内 Service 注入，`/api/v1` 路径与包名不变（前端契约不受影响）。
