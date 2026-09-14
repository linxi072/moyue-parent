# 墨阅小说网（Moyue Novel）后端

基于 **Spring Boot 3.2.12 + Spring Cloud 2023.0.5 + Spring Cloud Alibaba** 的微服务小说平台。
工程组织对齐 [RuoYi-Cloud](https://github.com/yangzongzhuan/RuoYi-Cloud) 目录风格：common 细粒度拆分、api 契约模块化、独立认证服务、业务服务聚合于 `moyue-modules/`。

## 一、模块总览（聚合器 + 22 个 Maven 模块）

```
moyue-parent
├── moyue-common/                 # 通用模块聚合器（对齐 RuoYi ruoyi-common）
│   ├── moyue-common-core         #   R<T> / ResultCode / BizException / Constants / PageResult / 全局异常
│   ├── moyue-common-security     #   JWT 签发解析 / AdminRoleInterceptor / @RequiresRoles·@RequiresPermissions 切面 / HeaderInterceptor
│   ├── moyue-common-redis        #   MoyueCacheAutoConfiguration（RedisCacheManager，前缀 moyue:）/ CacheNames
│   ├── moyue-common-mybatis      #   MyBatis-Plus 分页插件自动装配
│   └── moyue-common-migration    #   Flyway V1–V13（38 张表，全服务共享）
├── moyue-api/                    # Feign 契约聚合器（对齐 RuoYi ruoyi-api，按目标服务拆分，全量 FallbackFactory）
│   ├── moyue-api-account         #   UserClient → moyue-account
│   ├── moyue-api-content         #   BookClient / ChapterClient → moyue-content
│   ├── moyue-api-social          #   BlogClient / CommentClient / ImClient → moyue-social
│   ├── moyue-api-commerce        #   PointsClient → moyue-commerce
│   ├── moyue-api-search          #   SearchIndexClient 索引同步 → moyue-search
│   ├── moyue-api-message         #   MessageDispatchClient 消息分发 → moyue-message
│   └── moyue-api-risk            #   RiskClient 机审 → moyue-risk
├── moyue-gateway/                # 网关 8080：JWT 鉴权 / 白名单配置化 / Sentinel 路由限流熔断
├── moyue-auth/                   # 独立认证 8090：登录 / 注册 / 刷新令牌（对齐 RuoYi ruoyi-auth）
└── moyue-modules/                # 业务服务聚合目录（对齐 RuoYi ruoyi-modules）
    ├── moyue-content/            #   8082 内容域：书城 book / 章节 chapter / 阅读书架 read / 封面文件 files
    ├── moyue-social/             #   8083 社区域：评论 comment / 博客 blog / 即时通讯 im（WebSocket /ws/im）
    ├── moyue-commerce/           #   8084 商业域：积分 points / 商城 merch / 作者稿酬 author
    ├── moyue-search/             #   8085 检索域：全文检索 / 热门推荐（只读 ES，索引 moyue_book）
    ├── moyue-system/             #   8086 平台运营域：系统管理 RBAC system / 运营公告对账 operation / 统计 stat / XXL-Job job / 打赏 reward
    ├── moyue-message/            #   8087 触达域：站内信 + 渠道 SPI（邮件真实现，短信/推送桩）+ 触达记录
    ├── moyue-risk/               #   8089 内容安全域：人工审核 audit（敏感词/机审/举报 见 P2-15 规划）
    ├── moyue-account/            #   8081 账号域：用户资料 user（认证已拆出）
    └── moyue-ai/                 #   8097 智能域：AI 客服
```

> **约定：模块 ≠ 包名。** 各模块内 Java 包保持 `com.moyue.<域>`，启动类统一
> `@ComponentScan("com.moyue")` + `@MapperScan("com.moyue")` + `@EnableDiscoveryClient`；
> 包名与 HTTP 路径在历次重构中零变更，前端契约不受影响。

## 二、环境要求

| 组件 | 版本 | 说明 |
|---|---|---|
| JDK | 21（父 POM target 17） | 编译与运行 |
| Maven | 3.9+ | 配合 `_bootstrap/settings.xml`（阿里云镜像） |
| MySQL | 8.x | Flyway 自动迁移 V1–V13 |
| Redis | 7.x | 缓存 / IM / refreshToken |
| Nacos | 2.x | 注册中心（8848） |
| Elasticsearch | 8.13.4 | 检索域（可选，docker-compose 提供） |
| XXL-Job Admin | 2.4.0 | 调度中心（可选，8088） |

## 三、环境变量（`.env.example`）

| 变量 | 用途 | 默认（dev） |
|---|---|---|
| `MYSQL_HOST/PORT/DB/USERNAME/PASSWORD` | 数据源 | localhost:3306/moyue，dev 口令 root |
| `REDIS_HOST/PORT` | Redis | localhost:6379 |
| `NACOS_SERVER_ADDR` | 注册中心 | localhost:8848 |
| `MOYUE_JWT_SECRET` | JWT 密钥（auth 与 gateway 必须一致） | dev 有默认值，**test/prod 必须注入** |
| `ES_URIS` | Elasticsearch | http://localhost:9200 |
| `MOYUE_MAIL_HOST/PORT/USERNAME/PASSWORD/FROM` | SMTP（触达域） | MailHog localhost:1025 |
| `XXL_JOB_ADMIN_ADDRESSES` | 调度中心 | http://localhost:8088/xxl-job-admin |
| `SPRING_PROFILES_ACTIVE` | dev / test / prod | dev |

## 四、构建与启动

```bash
mvn -s ../_bootstrap/settings.xml -DskipTests clean install   # 22 模块全量构建
./scripts/build.sh && ./scripts/start-all.sh                  # 或脚本一键
```

启动顺序：**auth（播种演示账号）→ gateway → 其余 9 个业务服务**（`start-all.sh` 已编排）。
基础设施：**原生安装**（MySQL / Redis / Nacos / Elasticsearch / MailHog）。⚠️ 项目硬性约束：**禁用 Docker**，详见 [docs/不可忽视条件.md](docs/不可忽视条件.md)。

验证：`./scripts/smoke-test.sh`；Nacos 控制台 http://localhost:8848/nacos 确认 11 个服务注册。

## 五、网关路由（moyue-gateway）

统一入口 `http://localhost:8080`，`JwtAuthGlobalFilter` 校验 Bearer token 后注入 `X-User-Id` / `X-User-Role`；白名单已配置化：

```yaml
moyue:
  auth:
    whitelist: /api/v1/auth/login,/api/v1/auth/register,/api/v1/auth/refresh,/api/v1/system/login
    whitelist-prefixes: /api/v1/files/
```

| 路径前缀 | 服务 |
|---|---|
| `/api/v1/auth/**` | moyue-auth（8090） |
| `/api/v1/users/**` | moyue-account（8081） |
| `/api/v1/books/**` `/api/v1/chapters/**` `/api/v1/read/**` `/api/v1/files/**` | moyue-content（8082） |
| `/api/v1/comments/**` `/api/v1/blog/**` `/api/v1/im/**` | moyue-social（8083） |
| `/api/v1/points/**` `/api/v1/merch/**` `/api/v1/author/**` | moyue-commerce（8084） |
| `/api/v1/search/**` | moyue-search（8085） |
| `/api/v1/admin/announcements/**` `/api/v1/admin/orders/**` `/api/v1/admin/stats/**` `/api/v1/rewards/**` `/api/v1/admin/system/**` `/api/v1/system/login` | moyue-system（8086） |
| `/api/v1/messages/**` | moyue-message（8087） |
| `/api/v1/admin/audit/**` `/api/v1/reports/**` `/api/v1/admin/risk/**` | moyue-risk（8089） |
| `/api/v1/ai/**` | moyue-ai（8097） |

Sentinel：网关路由级 QPS 限流（超限业务码 `30001`）+ 熔断降级（`40002`），规则编程式定义于 `SentinelGatewayConfig`。

## 六、鉴权体系（双层防线，对齐 RuoYi）

1. **路径级默认拒绝**：`AdminRoleInterceptor` 拦截 `/api/v1/admin/**`（仅 role=3）；
2. **注解级细粒度控制**（moyue-common-security）：
   - `@RequiresRoles("3")` —— 角色校验（1=读者 2=作者 3=管理员）；
   - `@RequiresPermissions("system:user:list")` —— 权限码校验，`*:*:*` 通配（RuoYi 惯例）；
   - `HeaderInterceptor` 装配 `SecurityContextHolder`（ThreadLocal），切面 `PreAuthorizeAspect` 执行校验，失败抛 FORBIDDEN(10003)；
   - 默认 `DefaultPermissionProvider`：role=3 授予 `*:*:*`；接入 RBAC 后替换为按用户查菜单权限的 Provider 即可。

## 七、跨服务调用（moyue-api）

全部 Feign 客户端带 `fallbackFactory`（`feign.sentinel.enabled: true`，Sentinel 托管），
目标服务不可用时返回 `R.fail(40002)` 而非抛异常：

```java
@FeignClient(name = "moyue-search", fallbackFactory = SearchIndexClientFallbackFactory.class)
public interface SearchIndexClient { ... }
```

通用分页载体 `PageResult` 位于 `com.moyue.common.core.domain`（moyue-common-core）。

## 八、Flyway 迁移（moyue-common-migration）

V1–V13 共 13 个脚本 / 38 张表，全服务共享（`classpath:db/migration`）。
关键：V3 message / V7 points / V8 merch / V10 audit / V12 system(RBAC) / V13 sensitive_word+report+message_template+message_channel_record。

## 九、路线图状态（P0→P2 已全部交付）

| 项 | 状态 |
|---|---|
| P2-13 搜索与推荐 | ✅ ES 分类筛选 / relevance·hot·latest 排序 / 热门推荐位（moyue-search） |
| P2-14 消息触达 | ✅ ChannelSender SPI + 站内信/邮件真实现 + 短信/推送桩 + XXL-Job 批量触达（moyue-message） |
| P2-15 内容安全 | 🟡 人工审核已交付（moyue-risk）；敏感词/机审/举报的 V13 表与 RiskClient 契约就绪，业务代码待实现 |
| P2-16 缓存与性能 | ✅ Redis 缓存基建 + Sentinel 网关限流熔断（BOOK_LIST/BOOK_DETAIL 缓存名待挂载读路径） |
| P2-17 配置治理 | ✅ dev/test/prod 隔离 + JWT/DB 口令环境变量化 + 网关白名单配置化 |
| RuoYi 化重构 | ✅ common 五拆 / api 七拆 + FallbackFactory / 独立 auth / 权限注解体系 / modules 聚合目录 |
