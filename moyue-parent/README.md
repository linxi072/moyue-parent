# 墨阅小说网 · 后端工程（moyue-parent）

> Maven 多模块微服务：**基础层 3 模块 + 三端业务 5 服务**（共 **8 个 Maven 模块**）。
> 技术栈：Spring Boot 3.2.12、Spring Cloud 2023.0.5、Spring Cloud Alibaba 2023.0.1.0（Nacos + Sentinel）、MyBatis-Plus 3.5.7、XXL-Job 2.4.0、jjwt 0.12.6、Java 17。
> 依赖 MySQL 8 / Redis 7.2 / Nacos 2.3.2 / Elasticsearch 8.13 / MailHog（一键 `docker compose up -d`）。
> 统一响应体 `R<T>`、错误码、鉴权头、BasePath 严格对齐[架构设计稿](../系统架构设计说明.md)。

## 三端架构（2026-09-13 重构）

> 工程经两次模块重构：21 → 9（领域收敛）→ **8（三端聚合）**。
> 本次按**读者端 / 作者端 / 管理端**重新切分服务边界；合并只动 Maven 模块与网关路由目标，
> **Java 包名与全部 HTTP 路径零变更**——各能力域仍保留原 `com.moyue.<域>` 包，
> 由启动类 `@ComponentScan("com.moyue")` + `@MapperScan("com.moyue")` 统一扫描。

| 模块 | 端口 | 端 | 承载能力域（Java 包） | 主表 |
| --- | --- | --- | --- | --- |
| moyue-parent | - | - | 父工程：版本与依赖 BOM 统一管理 | - |
| moyue-common | - | - | 通用模块：`R<T>` / `ResultCode` / `BizException` / 全局异常 / `JwtProvider` / `AdminRoleInterceptor` / 缓存自动配置 + **Flyway 迁移 V1–V13** | - |
| moyue-api | - | - | 共享 DTO + 10 个 Feign 客户端（服务名已对齐三端） | - |
| moyue-gateway | 8080 | - | Spring Cloud Gateway：显式 `lb://` 路由 + JWT 统一鉴权 + 跨域 + **Sentinel 网关限流/熔断骨架** | - |
| moyue-account | 8081 | 通用 | 认证中心（登录 / 注册 / 刷新）+ 用户资料 | user + refreshToken（Redis） |
| **moyue-reader** | **8091** | **读者端** | 阅读书架 `read` · 评论 `comment` · 博客 `blog` · 即时通讯 `im`（WebSocket `/ws/im`）· 积分 `points` · 周边商城 `merch` · **搜索推荐 `search`（Elasticsearch 只读）** · **消息触达 `message`（渠道 SPI：站内信+邮件真实现，短信/推送桩）** · **打赏与稿酬 `operation`（Reward* + AuthorIncome*）** | bookshelf / comment / blog_* / chat_* / points_* / merch_* / notice / message_template / message_channel_record / reward_order / author_income |
| **moyue-author** | **8092** | **作者端** | 作品管理 `book`（CRUD + 我的作品 + 完结申请）· 章节创作 `chapter`（草稿 / 发布 / 排序）· 作者稿酬账户 `author` · 封面文件 `content.config`（本地上传 `/api/v1/files/**`） | book / chapter / author_income |
| **moyue-admin** | **8093** | **管理端** | 内容审核 `audit`（裁决经 Feign 回写）· 运营公告与对账 `operation`（Announcement* + 订单对账）· 全站统计 `stat` · 系统管理 RBAC `system`（部门/菜单/角色/用户）· **XXL-Job 执行器 `job`**（appname=moyue-job，RPC 9099） | audit_task / announcement / sys_* / 聚合只读 |
| moyue-ai | 8097 | 通用 | AI 智能客服（独立能力域，便于单独扩容） | - |

> 包名提示：`moyue-reader` 内含 `com.moyue.{read,comment,blog,im,points,merch,search,message,operation}`；
> `moyue-author` 内含 `com.moyue.{book,chapter,author,content.config}`；
> `moyue-admin` 内含 `com.moyue.{audit,operation,stat,system,job}`。
> `com.moyue.operation` 按打赏/公告**拆分归属**：`Reward*` 与 `AuthorIncome*` 在 reader（写侧），
> `Announcement*` 与订单对账在 admin；admin 侧另持一份 `RewardOrderEntity/Mapper` 只读对账
> （与既有 `author_income` 双份共存的先例一致：同 FQN、不同服务、同一张表）。

## 端口总览

| 服务 | 端口 | 说明 |
| --- | --- | --- |
| 网关 gateway | 8080 | 前端唯一入口 |
| 账号 account | 8081 | auth + user |
| **读者端 reader** | **8091** | 阅读/社交/商业/搜索/触达/打赏 |
| **作者端 author** | **8092** | 作品/章节/稿酬/文件 |
| **管理端 admin** | **8093** | 审核/运营/统计/RBAC/调度（XXL-Job 执行器 RPC 9099） |
| 智能 ai | 8097 | AI 客服 |
| 前端 web | 5173 | Vue 3 |
| MySQL / Redis / Nacos | 3306 / 6379 / 8848 | 基础设施 |
| **Elasticsearch / MailHog** | **9200 / 1025(8025)** | 检索 / 邮件联调（docker-compose 提供） |
| XXL-Job Admin（需单独启动） | 8088 | 调度中心 |

## 本地启动

### 1. 启动基础设施（MySQL / Redis / Nacos / Elasticsearch / MailHog）
```bash
cd ..            # 回到 moyue-parent 上级目录（docker-compose.yml 所在处）
docker compose up -d
# 健康检查：mysqladmin ping / redis-cli ping / curl http://localhost:8848/nacos/health/check
# ES：curl http://localhost:9200 ；MailHog 界面：http://localhost:8025
```

### 2. 环境变量（P2-17 配置治理）

> 敏感项一律 `${ENV_VAR:dev默认}` 注入；**test / prod profile 不提供明文默认值，必须由环境变量注入**。
> 样例见 `moyue-parent/.env.example`。

| 变量 | 说明 | dev 默认 |
| --- | --- | --- |
| `MOYUE_JWT_SECRET` | JWT 共享密钥（gateway / account / common `JwtProvider`） | 本地开发默认值 |
| `MYSQL_USERNAME` / `MYSQL_PASSWORD` | 数据库账号口令 | root / root（仅 dev yml） |
| `ES_URIS` | Elasticsearch 地址 | http://localhost:9200 |
| `MOYUE_MAIL_HOST/PORT/USERNAME/PASSWORD/FROM` | SMTP（dev 指向 MailHog，无认证） | localhost:1025 / 空 |
| `SPRING_PROFILES_ACTIVE` | dev / test / prod | dev |

### 3. 构建全部模块
```bash
cd moyue-parent
mvn clean package -DskipTests
```

### 4. 启动各服务（顺序不限，均向 Nacos 注册）
```bash
# 方式 A：直接跑 jar
java -jar moyue-gateway/target/moyue-gateway-1.0.0-SNAPSHOT.jar
java -jar moyue-account/target/moyue-account-1.0.0-SNAPSHOT.jar
java -jar moyue-reader/target/moyue-reader-1.0.0-SNAPSHOT.jar
java -jar moyue-author/target/moyue-author-1.0.0-SNAPSHOT.jar
java -jar moyue-admin/target/moyue-admin-1.0.0-SNAPSHOT.jar
java -jar moyue-ai/target/moyue-ai-1.0.0-SNAPSHOT.jar

# 方式 B：Maven 直接跑（含依赖模块 -am）
mvn -pl moyue-reader -am spring-boot:run
```

> 建议启动顺序：先 `moyue-account`（启动时以 BCrypt 写入演示用户 id=1），其余服务任意。
> 全链路检索需 ES 运行且书籍索引已建（`POST /api/v1/internal/search/books/_index` 全量重建）；
> 邮件触达在 dev 下落入 MailHog（http://localhost:8025 查看）。

## 一键脚本（scripts/）

> 三个脚本均为 **LF 换行**，请在 **Git Bash / macOS / Linux** 下执行；Windows 用户请用 Git Bash。

```bash
cd moyue-parent
chmod +x scripts/*.sh        # 首次使用需赋予可执行权限

./scripts/build.sh           # 1. 构建：mvn clean package -DskipTests
./scripts/start-all.sh       # 2. 启动：先 account（播种用户 id=1）→ gateway → 其余业务服务
./scripts/smoke-test.sh      # 3. 冒烟：经网关 :8080 跑全链路，任一用例失败则 exit 1
```

> 注意：`start-all.sh` 中的服务清单为旧 9 模块拓扑，三端重构后需按上方模块表调整
> （reader / author / admin 替换原 content / social / commerce / platform）。

## 演示数据与 Flyway

* `moyue-common/src/main/resources/db/migration/` 放置全部迁移（**所有服务共享同一份**）：
  * `V1__init.sql`：8 张核心表（user / book / chapter / comment / reward_order / bookshelf / author_income / audit_task）。
  * `V2__seed.sql`：5 本示例书（id 1001–1005，author_id=1）、3 章、2 条评论；用户由 `moyue-account` 启动时写入。
  * `V3__message.sql`：站内信 `notice` 表。
  * `V4__feature_im_points_blog.sql`：IM 3 张 + 积分商城 3 张 + 博客 3 张（+ 种子）。
  * `V5` 评论点赞 / `V6` 公告与 reward_order 修复 / `V7` 积分商城 / `V8` 周边商城 / `V9` AI 客服 / `V10` 审核意见 / `V11` AI 会话 / `V12` 系统管理 RBAC 六张 `sys_*` 表。
  * `V13__content_safety_and_reach.sql`（P2）：**敏感词 `sensitive_word` / 举报 `report` / 触达模板 `message_template` / 触达记录 `message_channel_record`**（+ 种子）。
* 每个服务的 `application.yml` 均启用 `spring.flyway.enabled=true`（baseline-on-migrate）指向 `classpath:db/migration`；版本号全局递增不重复。
* 演示账号：`phone=13800000000`，`password=123456`（id=1，对齐种子 `author_id`/`user_id` 引用）。

## 全链路验证

> 以下均经网关 `:8080` 访问；除登录/注册/刷新外，均需 `Authorization: Bearer <accessToken>`。
> 路径与重构前完全一致，仅承载服务变化（括号内为现承载服务）。

1. **登录拿 token**（白名单免鉴权）
   ```bash
   curl -X POST http://localhost:8080/api/v1/auth/login \
     -H 'Content-Type: application/json' \
     -d '{"phone":"13800000000","password":"123456"}'
   ```
2. **书籍分页 / 详情 / 章节**（author 8092；作者昵称经 Feign `UserClient` 解析）
   ```bash
   curl http://localhost:8080/api/v1/books -H 'Authorization: Bearer <accessToken>'
   curl http://localhost:8080/api/v1/chapters/2001 -H 'Authorization: Bearer <accessToken>'
   ```
3. **阅读 / 书架**（reader 8091）
   ```bash
   curl http://localhost:8080/api/v1/read/bookshelf/1 -H 'Authorization: Bearer <accessToken>'
   ```
4. **搜索与推荐**（reader 8091，Elasticsearch；P2-13 支持 `categoryId` / `sort=relevance|hot|latest` / 推荐位）
   ```bash
   curl "http://localhost:8080/api/v1/search/books?keyword=剑&categoryId=1&sort=hot&page=1&size=10" -H 'Authorization: Bearer <accessToken>'
   curl "http://localhost:8080/api/v1/search/recommend?limit=10" -H 'Authorization: Bearer <accessToken>'
   ```
5. **评论 / 博客 / IM / 积分 / 商城**（reader 8091）
   ```bash
   curl "http://localhost:8080/api/v1/comments?bookId=1001" -H 'Authorization: Bearer <accessToken>'
   curl "http://localhost:8080/api/v1/points/accounts/1" -H 'Authorization: Bearer <accessToken>'
   ```
6. **消息与触达**（reader 8091；P2-14 渠道 SPI：站内信 + 邮件真实现 / 短信推送桩）
   ```bash
   curl http://localhost:8080/api/v1/messages/1 -H 'Authorization: Bearer <accessToken>'
   curl -X POST http://localhost:8080/api/v1/internal/messages/dispatch -H 'Content-Type: application/json' -d '...'  # 服务间端点，不经网关
   ```
7. **打赏与稿酬**（reader 8091）
   ```bash
   curl http://localhost:8080/api/v1/rewards/income/1 -H 'Authorization: Bearer <accessToken>'
   ```
8. **管理端**（admin 8093：审核 / 公告 / 对账 / 统计 / RBAC）
   ```bash
   curl -X POST http://localhost:8080/api/v1/system/login -H 'Content-Type: application/json' \
     -d '{"username":"admin","password":"admin123"}'
   curl http://localhost:8080/api/v1/admin/audit/comments -H 'Authorization: Bearer <adminToken>'
   curl "http://localhost:8080/api/v1/admin/announcements?page=1&size=20" -H 'Authorization: Bearer <adminToken>'
   curl http://localhost:8080/api/v1/admin/stats/overview -H 'Authorization: Bearer <adminToken>'
   curl http://localhost:8080/api/v1/admin/system/depts -H 'Authorization: Bearer <adminToken>'
   ```

## 路由与服务发现

* 网关 `application.yml` 使用 `uri: lb://moyue-<svc>` 经 Nacos 解析实例；`discovery.locator.enabled=false`。
* **路由 id 保留原业务语义（便于日志排查），`uri` 统一指向承载它的三端服务**——例如
  `id: moyue-comment` / `id: moyue-points` / `id: moyue-reward` 的 `uri` 现均为 `lb://moyue-reader`。
* 关键顺序不变：`id: moyue-auth`（含 `/api/v1/users/me`）必须排在 `id: moyue-user`（`/api/v1/users/**`）之前。
* 三端后共 **5 个业务服务**：`moyue-account`（8081）、`moyue-reader`(8091)、`moyue-author`(8092)、`moyue-admin`(8093)、`moyue-ai`(8097)。
* 网关已接入 **Sentinel**（P2-16 骨架）：路由级 QPS 限流 + RT 熔断，编程式规则（无需 Dashboard）；
  熔断降级返回业务码 **40002**，限流返回 **30001**（HTTP 均为 200 承载）。

## 服务间调用（Feign）

* `moyue-api` 定义 Feign 客户端，方法返回类型**包裹 `R<DTO>`**。
* 调用方使用 `@EnableFeignClients("com.moyue")` + `spring-cloud-starter-loadbalancer`（**必须成对出现**）。
* 目标服务名已随三端重构更新：`UserClient` → `moyue-account`；
  `BookClient` / `ChapterClient` → `moyue-author`；
  `CommentClient` / `ImClient` / `BlogClient` / `PointsClient` / `MessageDispatchClient` / `SearchIndexClient` → `moyue-reader`；
  `RiskClient` → `moyue-admin`。
* 典型链路：作品 CRUD（author）→ `SearchIndexClient` 同步检索索引（reader，失败降级不阻断）；
  章节发布前 → `RiskClient` 机审（admin）；审核裁决（admin）→ Feign 回写章节（author）/ 评论（reader）状态；
  XXL-Job 触达任务（admin）→ `MessageDispatchClient`（reader）。

## 任务调度（XXL-Job）

1. 单独启动调度中心（官方镜像，需可访问 MySQL）：
   ```bash
   docker run -d --name xxl-job-admin -p 8088:8080 \
     -e PARAMS="--spring.datasource.url=jdbc:mysql://mysql:3306/xxl_job?useUnicode=true&characterEncoding=utf8mb4&serverTimezone=Asia/Shanghai \
     --spring.datasource.username=root --spring.datasource.password=root" \
     xuxueli/xxl-job-admin:2.4.0
   ```
   > 首次使用需先建 `xxl_job` 库并执行官方 `tables_xxl_job.sql`。
2. 在调度中心「执行器管理」新建执行器 `appname=moyue-job`（自动注册）。
   > 执行器现随 **moyue-admin**（8093）进程启动，RPC 端口仍为 **9099**；`appname` 刻意保持 `moyue-job` 不变。
3. 「任务管理」新增任务，ExecutorHandler 填：`demoJobHandler`（hello）、`messageDispatchJobHandler`（P2-14 触达分发，经 Feign 调 reader，目标服务未启动时不阻断）。

## 统一约定（对齐设计稿）

* 响应体：`{ "code": 0, "message": "success", "data": ..., "traceId": "..." }`；HTTP 统一 200 承载，业务结果靠 `code` 表达。
* 鉴权头：`Authorization: Bearer {accessToken}`；网关注入下游：`X-User-Id` / `X-User-Role`；`AdminRoleInterceptor` 保护 `/api/v1/admin/**`（role=3）。
* BasePath：`/api/v1`。
* 错误码：0 成功 / 10001 参数 / 10002 未登录 / 10003 无权限 / 20001 资源不存在 / **20002 内容被拦截（敏感词，预留）** / 30001 频率超限 / **40002 服务降级（Sentinel 熔断）** / 40001 内部异常 / 50001 支付失败。

## 已知约定 / 遗留

* **P2-13~17 状态**：模块重构（三端聚合）✅；搜索分类/排序/热门推荐 ✅；渠道 SPI + 站内信/邮件 + 短信/推送桩 + 触达任务 ✅；网关 Sentinel 限流熔断骨架 ✅；Profiles + 环境变量 + JWT/DB 口令治理 ✅（test/prod 无明文默认）。
* **T05 待做**：敏感词过滤 / 机审 / 举报闭环的业务代码（`com.moyue.risk`，落 moyue-admin）——V13 四表与种子已就绪，`RiskClient` 契约已定义；`BOOK_LIST`/`BOOK_DETAIL` 缓存名已定义待在书城读路径挂 `@Cacheable`。
* 分类名为演示字典（`BookService.CATEGORY_NAMES`），生产可独立分类服务。
* 所有服务共用同一 MySQL 库与 Nacos 命名空间（dev 演示），多环境隔离靠 profile + 环境变量。
* WebSocket（IM）请**直连 8091**（`ws://localhost:8091/ws/im?userId={userId}`），不要走网关 8080——`JwtAuthGlobalFilter` 对非白名单路径要求 Bearer，握手会被判 10002。
* 打赏支付为演示回调（`POST /api/v1/rewards/{orderNo}/pay`），真实渠道对接与退款未做。
* `deliverables/openapi.yaml` / `moyue-schema.sql` 为快照式人工同步：本轮模块重构**未改任何对外路径**，契约无需变更；但后续 T05 新增端点后需重跑比对。
* 集成测试 4 套（bookshelf / blog / im / points，Testcontainers MySQL）随域迁入 `moyue-reader/src/test`，运行需本机 Docker。
