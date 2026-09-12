# 墨阅小说网 · 后端工程（moyue-parent）

> Maven 多模块微服务脚手架：**网关 + 鉴权 + 共享 API + 任务调度 + 13 个业务服务** 的完整可运行集。
> 技术栈：Spring Boot 3.2.12、Spring Cloud 2023.0.5、Spring Cloud Alibaba 2023.0.1.0（Nacos）、MyBatis-Plus 3.5.7、XXL-Job 2.4.0、jjwt 0.12.6、Java 17。
> 依赖 MySQL 8 / Redis 7.2 / Nacos 2.3.2（一键 `docker compose up -d`）。
> 统一响应体 `R<T>`、错误码、鉴权头、BasePath 严格对齐[架构设计稿](../deliverables/系统架构设计说明.md)。

## 模块说明

| 模块 | 端口 | 职责 | 主表 |
| --- | --- | --- | --- |
| moyue-parent | - | 父工程：版本与依赖 BOM 统一管理 | - |
| moyue-common | - | 通用模块：`R<T>` / `ResultCode` / `BizException` / 全局异常 / `JwtProvider` / `Constants` / `WebMvcConfig` + **Flyway 迁移 V1/V2/V3/V4** | - |
| moyue-api | - | 共享 DTO（`UserDTO`/`BookSummaryDTO`/`ChapterDTO`/`CommentDTO`/`ConversationDTO`/`MessageDTO`/`PointsAccountDTO`/`PointsProductDTO`/`PointsOrderDTO`/`BlogPostDTO`/`BlogCommentDTO`/`PageResult<T>`）+ Feign 客户端（`UserClient`/`BookClient`/`ChapterClient`/`CommentClient`/`ImClient`/`PointsClient`/`BlogClient`） | - |
| moyue-gateway | 8080 | Spring Cloud Gateway：Nacos `lb://` 路由 + JWT 统一鉴权 + 跨域 | - |
| moyue-auth | 8081 | 认证中心：登录 / 注册 / 刷新 / 当前用户资料 | user（MySQL）+ refreshToken（Redis） |
| moyue-book | 8082 | 书城服务：书籍分页 / 详情（Feign 解析作者昵称） | book |
| moyue-user | 8083 | 用户服务：资料查询 / 分页 | user |
| moyue-read | 8084 | 阅读服务：书架 | bookshelf |
| moyue-chapter | 8085 | 章节服务：章节详情 / 目录分页 | chapter |
| moyue-author | 8086 | 作者服务：稿酬流水 | author_income |
| moyue-comment | 8087 | 评论服务：评论查询 / 发表 | comment |
| moyue-audit | 8088 | 审核服务：待审任务查询（admin） | audit_task |
| moyue-operation | 8089 | 运营服务：打赏订单对账 / 公告（admin） | reward_order |
| moyue-message | 8090 | 消息服务：站内通知查询 / 发送 | notice |
| moyue-stat | 8091 | 统计服务：全站聚合统计（admin，只读 @Select） | 聚合（多表） |
| moyue-job | 8092 | 任务调度执行器（XXL-Job 2.4.0，executor RPC 9099） | - |
| moyue-im | 8093 | 即时通讯服务：单聊 / 群聊会话、消息收发 | chat_conversation / chat_conversation_member / chat_message |
| moyue-points | 8094 | 积分商城服务：积分账户 / 商品 / 兑换订单 | points_account / points_product / points_order |
| moyue-blog | 8095 | 博客空间服务：博客文章 / 评论 / 点赞 | blog_post / blog_comment / blog_like |

## 端口总览

| 服务 | 端口 | 前端 | 中间件 |
| --- | --- | --- | --- |
| 网关 gateway | 8080 | 前端 web | - |
| 鉴权 / 书城 / 用户 / 阅读 / 章节 / 作者 / 评论 / 审核 / 运营 / 消息 / 统计 / 任务 / 即时通讯 / 积分 / 博客 | 8081–8095 | - | - |
| 前端 web | 5173 | - | - |
| MySQL / Redis / Nacos | 3306 / 6379 / 8848 | - | 基础设施 |
| XXL-Job Admin（需单独启动） | 8088 | - | 调度中心 |

## 本地启动

### 1. 启动基础设施（MySQL / Redis / Nacos）
```bash
cd ..            # 回到 moyue-parent 上级目录（docker-compose.yml 所在处）
docker compose up -d
# 健康检查：mysqladmin ping / redis-cli ping / curl http://localhost:8848/nacos/health/check
```

### 2. 构建全部模块
```bash
cd moyue-parent
mvn clean package -DskipTests
```

### 3. 启动各服务（顺序不限，均向 Nacos 注册）
```bash
# 方式 A：直接跑 jar
java -jar moyue-gateway/target/moyue-gateway-1.0.0-SNAPSHOT.jar
java -jar moyue-auth/target/moyue-auth-1.0.0-SNAPSHOT.jar
java -jar moyue-book/target/moyue-book-1.0.0-SNAPSHOT.jar
# ... 其余服务同理

# 方式 B：Maven 直接跑（含依赖模块 -am）
mvn -pl moyue-auth -am spring-boot:run
```

> 建议启动顺序：先 `auth`（启动时以 BCrypt 写入演示用户 id=1），其余服务任意。
> 所有服务共用同一 JWT 密钥（`moyue-jwt-dev-secret-key-0123456789abcdefghij`），由 `moyue-common` 提供。
> IM / 积分 / 博客三个新服务的跨服务昵称解析（作者名、发送者名）均依赖 `UserClient`，目标服务（user）不可用时安全降级为「用户 + id」。

## 一键脚本（scripts/）

> 三个脚本均为 **LF 换行**，请在 **Git Bash / macOS / Linux** 下执行；Windows 用户请用 Git Bash，不要用 CMD / PowerShell 直接跑。

```bash
cd moyue-parent
chmod +x scripts/*.sh        # 首次使用需赋予可执行权限

./scripts/build.sh           # 1. 构建：mvn clean package -DskipTests
./scripts/start-all.sh       # 2. 启动：先 auth（播种用户 id=1）→ gateway → 其余服务，nohup 后台运行
./scripts/smoke-test.sh      # 3. 冒烟：经网关 :8080 跑全链路，任一用例失败则 exit 1
```

* `build.sh`：自动 cd 到脚本目录的上级（即 `moyue-parent`）后执行 `mvn clean package -DskipTests`。
* `start-all.sh`：开头提示先 `cd .. && docker compose up -d` 起基础设施；随后 **auth 优先**（它写入演示用户 id=1，V2/V4 种子数据引用该 id），再启网关，最后逐个启动其余服务。日志统一写入 `moyue-parent/logs/<module>.log`。
* `smoke-test.sh`：覆盖登录取 token → IM（建会话 / 列表 / 发消息 / 消息列表）→ 积分商城（账户 / 商品 / 正常兑换 + **积分不足、库存不足两个失败场景断言 `code=10001`**）→ 博客（发布 / 详情 / 评论 / 评论列表 + **连续三次点赞断言 `1 → 0 → 1` 幂等**）。
  * 失败场景与正常兑换均使用**当场创建的商品**（低价 / 天价 / 零库存），不依赖种子商品状态，脚本可重复执行（每次正常兑换消耗 10 积分）。
  * 可用环境变量覆盖：`BASE`（默认 `http://localhost:8080`）、`PHONE`、`PASSWORD`、`USER_ID`。

## 演示数据与 Flyway

* `moyue-common/src/main/resources/db/migration/` 放置四份迁移（**所有服务共享同一份**，无需各模块重复）：
  * `V1__init.sql`：8 张核心表（user / book / chapter / comment / reward_order / bookshelf / author_income / audit_task），InnoDB / utf8mb4 / 雪花 ID / 逻辑删除 `is_deleted`。
  * `V2__seed.sql`：5 本示例书（id 1001–1005，author_id=1）、3 章、2 条评论。用户由 auth 启动时写入，故此处不硬编码密码。
  * `V3__message.sql`：站内信 `notice` 表。
  * `V4__feature_im_points_blog.sql`：书城扩展三大功能域共 9 张表 + 种子：
    * 即时通讯：`chat_conversation`（会话：type 1=单聊 2=群聊）、`chat_conversation_member`（会话成员：role、last_read_message_id，唯一键 `uk_conv_user`）、`chat_message`（消息：sender_id / content / type / status）。
    * 积分商城：`points_account`（user_id 主键、balance / total_earned / total_spent）、`points_product`（name / description / image_url / cost_points / stock / status）、`points_order`（user_id / product_id / product_name / cost_points / status）。种子：演示用户 id=1 初始积分 500；3 件上架商品（id 2001/2002/2003）。
    * 博客空间：`blog_post`（author_id / title / cover_url / summary / content / status / like_count / comment_count / view_count）、`blog_comment`（post_id / user_id / content / like_count）、`blog_like`（post_id / user_id，唯一键 `uk_post_user`）。种子：演示用户 id=1 发布 1 篇示例文章（id 3001）。
* 每个服务的 `application.yml` 均启用 `spring.flyway.enabled=true`（baseline-on-migrate）指向 `classpath:db/migration`。
* 演示账号：`phone=13800000000`，`password=123456`，启动后由 auth 以 BCrypt 写入 `user` 表（id 固定为 1，以对齐 V2/V4 种子的 `author_id`/`user_id` 引用）。

## 全链路验证

> 以下均经网关 `:8080` 访问；除登录/注册/刷新外，均需 `Authorization: Bearer <accessToken>`。

1. **登录拿 token**（白名单免鉴权）
   ```bash
   curl -X POST http://localhost:8080/api/v1/auth/login \
     -H 'Content-Type: application/json' \
     -d '{"phone":"13800000000","password":"123456"}'
   # 返回 data.accessToken / data.refreshToken
   ```
2. **书籍分页**（网关鉴权 → 网关注入 `X-User-Id`；book 服务经 Feign `UserClient` 解析作者昵称）
   ```bash
   curl http://localhost:8080/api/v1/books -H 'Authorization: Bearer <accessToken>'
   ```
3. **当前用户资料**（读网关注入的 `X-User-Id`）
   ```bash
   curl http://localhost:8080/api/v1/users/me -H 'Authorization: Bearer <accessToken>'
   ```
4. **刷新令牌**
   ```bash
   curl -X POST http://localhost:8080/api/v1/auth/refresh \
     -H 'Content-Type: application/json' -d '{"refreshToken":"<refreshToken>"}'
   ```
5. **其余业务服务示例**
   ```bash
   curl http://localhost:8080/api/v1/read/bookshelf/1 -H 'Authorization: Bearer <accessToken>'      # 书架
   curl http://localhost:8080/api/v1/chapters/2001 -H 'Authorization: Bearer <accessToken>'          # 章节
   curl http://localhost:8080/api/v1/author/income/1 -H 'Authorization: Bearer <accessToken>'        # 作者稿酬
   curl "http://localhost:8080/api/v1/comments?bookId=1001" -H 'Authorization: Bearer <accessToken>' # 评论
   curl http://localhost:8080/api/v1/admin/audit/comments -H 'Authorization: Bearer <accessToken>'   # 审核（admin）
   curl "http://localhost:8080/api/v1/admin/announcements?page=1&size=20" -H 'Authorization: Bearer <accessToken>' # 运营
   curl http://localhost:8080/api/v1/messages/1 -H 'Authorization: Bearer <accessToken>'             # 消息
   curl http://localhost:8080/api/v1/admin/stats/overview -H 'Authorization: Bearer <accessToken>'   # 统计
   ```
6. **即时通讯（IM）**
   ```bash
   # 6.1 创建单聊会话（ownerId=当前登录用户，memberIds 含对方 userId）
   curl -X POST http://localhost:8080/api/v1/im/conversations \
     -H 'Authorization: Bearer <accessToken>' -H 'Content-Type: application/json' \
     -d '{"type":1,"title":"与好友私聊","memberIds":[1,2]}'
   # 6.2 我的会话列表（分页）
   curl "http://localhost:8080/api/v1/im/conversations?userId=1&page=1&size=20" -H 'Authorization: Bearer <accessToken>'
   # 6.3 会话消息列表
   curl "http://localhost:8080/api/v1/im/conversations/{conversationId}/messages?page=1&size=20" -H 'Authorization: Bearer <accessToken>'
   # 6.4 发送消息
   curl -X POST http://localhost:8080/api/v1/im/conversations/{conversationId}/messages \
     -H 'Authorization: Bearer <accessToken>' -H 'Content-Type: application/json' \
     -d '{"senderId":1,"content":"你好，这是一条测试消息","type":1}'
   ```
7. **积分商城**
   ```bash
   # 7.1 查询积分账户（不存在自动初始化）
   curl http://localhost:8080/api/v1/points/accounts/1 -H 'Authorization: Bearer <accessToken>'
   # 7.2 商品列表
   curl http://localhost:8080/api/v1/points/products -H 'Authorization: Bearer <accessToken>'
   # 7.3 兑换商品（原子：扣余额 + 扣库存 + 插订单；余额不足/库存不足返回参数错误）
   curl -X POST http://localhost:8080/api/v1/points/orders \
     -H 'Authorization: Bearer <accessToken>' -H 'Content-Type: application/json' \
     -d '{"userId":1,"productId":2001}'
   # 7.4 我的兑换订单
   curl http://localhost:8080/api/v1/points/orders?userId=1 -H 'Authorization: Bearer <accessToken>'
   # 7.5 后台上下架商品（admin）
   curl -X POST http://localhost:8080/api/v1/admin/points/products \
     -H 'Authorization: Bearer <accessToken>' -H 'Content-Type: application/json' \
     -d '{"name":"会员月卡","description":"30天会员","imageUrl":"","costPoints":300,"stock":100,"status":1}'
   curl -X PUT http://localhost:8080/api/v1/admin/points/products/2001 \
     -H 'Authorization: Bearer <accessToken>' -H 'Content-Type: application/json' \
     -d '{"status":0}'
   ```
8. **博客空间**
   ```bash
   # 8.1 发布文章
   curl -X POST http://localhost:8080/api/v1/blog/posts \
     -H 'Authorization: Bearer <accessToken>' -H 'Content-Type: application/json' \
     -d '{"authorId":1,"title":"我的第一篇博客","coverUrl":"","summary":"摘要","content":"正文内容","status":1}'
   # 8.2 文章列表（authorId 可选；不传则返回全部）
   curl "http://localhost:8080/api/v1/blog/posts?page=1&size=20" -H 'Authorization: Bearer <accessToken>'
   # 8.3 文章详情（view_count 自增）
   curl http://localhost:8080/api/v1/blog/posts/3001 -H 'Authorization: Bearer <accessToken>'
   # 8.4 评论文章
   curl -X POST http://localhost:8080/api/v1/blog/posts/3001/comments \
     -H 'Authorization: Bearer <accessToken>' -H 'Content-Type: application/json' \
     -d '{"userId":1,"content":"写得好！"}'
   # 8.5 文章评论列表
   curl http://localhost:8080/api/v1/blog/posts/3001/comments -H 'Authorization: Bearer <accessToken>'
   # 8.6 点赞/取消点赞（幂等切换：已赞则取消并 like_count-1，未赞则新增并 like_count+1；返回最新 like_count）
   curl -X POST http://localhost:8080/api/v1/blog/posts/3001/like \
     -H 'Authorization: Bearer <accessToken>' -H 'Content-Type: application/json' -d '{"userId":1}'
   ```

## 路由与服务发现

* 网关 `application.yml` 使用 `uri: lb://moyue-<svc>` 经 Nacos 解析实例；`discovery.locator.enabled=false`（显式路由更可控）。
* **关键顺序**：`moyue-auth` 路由同时含 `/api/v1/auth/**` 与 `/api/v1/users/me`，且**必须排在 `moyue-user` 的 `/api/v1/users/**` 之前**——否则 `/users/me` 会被用户服务截走导致 404。
* 三个新路由追加在 message 之后：`moyue-im`（`/api/v1/im/**`）、`moyue-points`（`/api/v1/points/**` + `/api/v1/admin/points/**`）、`moyue-blog`（`/api/v1/blog/**`）。
* 所有业务服务均声明 `@EnableDiscoveryClient` 向 Nacos 注册。

## 服务间调用（Feign）

* `moyue-api` 定义 Feign 客户端，方法返回类型**包裹 `R<DTO>`**（与控制器实际响应 `R<Entity>` 的 JSON 结构一致，否则反序列化失败）。
* 调用方使用 `@EnableFeignClients("com.moyue")` + `spring-cloud-starter-loadbalancer`（**二者必须成对出现**，否则 `name=` 客户端启动报错）。
* 示例：`moyue-book` 通过 `UserClient.getUser(authorId).getData()` 解析作者昵称（目标服务不可用时安全降级为空串）。
* 新功能域跨服务调用：`moyue-im` 经 `UserClient` 解析消息发送者昵称（降级为「用户 + senderId」）；`moyue-blog` 经 `UserClient` 解析文章作者名与评论用户名（降级为「用户 + 对应 id」）。

## 任务调度（XXL-Job）

1. 单独启动调度中心（官方镜像，需可访问 MySQL）：
   ```bash
   docker run -d --name xxl-job-admin -p 8088:8080 \
     -e PARAMS="--spring.datasource.url=jdbc:mysql://mysql:3306/xxl_job?useUnicode=true&characterEncoding=utf8mb4&serverTimezone=Asia/Shanghai \
     --spring.datasource.username=root --spring.datasource.password=root" \
     xuxueli/xxl-job-admin:2.4.0
   ```
   > 首次使用需先建 `xxl_job` 库并执行官方 `tables_xxl_job.sql`（见 XXL-Job 文档）。
2. 在调度中心「执行器管理」新建执行器 `appname=moyue-job`（自动注册）。
3. 「任务管理」新增任务，ExecutorHandler 填：`demoJobHandler`（hello）、`shardingJobHandler`（分片广播）、`statAggregateJobHandler`（Feign 跨服务聚合，目标服务未启动时不阻断）。

## 统一约定（对齐设计稿）

* 响应体：`{ "code": 0, "message": "success", "data": ..., "traceId": "..." }`
* 鉴权头：`Authorization: Bearer {accessToken}`；网关注入下游：`X-User-Id` / `X-User-Role`
* BasePath：`/api/v1`；HTTP 统一 200 承载，业务结果靠 `code` 表达
* 错误码：0 成功 / 10001 参数 / 10002 未登录(token失效) / 10003 无权限 / 20001 资源不存在 / 30001 频率超限 / 40001 内部异常 / 50001 支付失败

## 已知约定 / 遗留

* 【已清理】`moyue-book` 模块下原先的 `vo` 包已整体移除：包内两个遗留类（书籍视图对象已被 `BookSummaryDTO` 取代、本地分页类已被 `com.moyue.api.dto.PageResult` 取代）均已删除，全工程无任何 import 或类型引用。分页类型现统一使用 `com.moyue.api.dto.PageResult`。
* 分类名为演示字典（`BookService.CATEGORY_NAMES`），生产可独立分类服务。
* 所有服务共用同一 MySQL 库与 Nacos 命名空间（dev 演示），未做多环境隔离。
* 集成层 `moyue-api` 的 DTO 由主理人分批落下：`PointsOrderDTO` 原本漏建，由积分服务实现时按 `PointsAccountDTO`/`PointsProductDTO` 风格补建（字段与 `points_order` 表一一对应）。后续新增跨服务返回类型时应一次性补齐全部 DTO。
* IM 服务已支持 WebSocket 实时推送：端点 `ws://localhost:8093/ws/im?userId={userId}`（`moyue-im` 的 `config/WebSocketConfig.java` + `websocket/ImWebSocketHandler.java`）。`ImService.sendMessage` 在消息落库后向会话成员广播，推送失败仅记日志、不阻断 HTTP 发送，客户端仍可轮询拉取历史消息。
  * 注意：WebSocket 请**直连 8093**，不要走网关 8080——网关 `JwtAuthGlobalFilter` 是全局过滤器，对非白名单路径一律要求 `Authorization: Bearer`，握手会被判为 10002。后续如需经网关转发，需新增 `ws` 路由并把该路径加入白名单。
* 【已修复·16-6】后台接口越权防护：所有 `/api/v1/admin/**` 端点（审核 / 运营 / 统计 / 积分后台）现已由 `moyue-common` 的 `AdminRoleInterceptor` 在服务端校验 `X-User-Role=3`，普通用户 / 作者凭有效令牌不再能访问后台数据；非管理员（缺头 / 非数字 / 角色≠3）返回 **10003**。演示用户默认 `role=1`（读者），联调后台接口可将 `moyue-auth` 的 `moyue.demo.role` 设为 `3` 临时取得管理员权限。
* 【已修复·16-17】评论接口不再信任前端 `userId`：`POST /api/v1/comments` 改为只接收 `bookId` / `chapterId` / `content`，评论人一律取网关注入的 `X-User-Id`；新增 `DELETE /api/v1/comments/{id}`（本人 / 管理员）与 `POST /api/v1/comments/{id}/like`（点赞切换，计数走原子 `setSql`）。
* 【新增·P1-7】打赏支付链路（`moyue-operation`，网关路由 `/api/v1/rewards/**`）：`POST /api/v1/rewards` 下单 → `POST /api/v1/rewards/{orderNo}/pay` 支付（以 `order_no` 幂等，重复回调不重复结算）→ 成功后按 70% 写入 `author_income`（类型 2 打赏分成）；另有 `GET /api/v1/rewards`（我的打赏）、`GET /api/v1/rewards/income`（我的稿酬）、`GET /api/v1/rewards/{orderNo}`（订单详情）。真实渠道对接与退款未做（需第三方支付账号）。
* 【新增·P1-8】管理端 / 内部写接口补齐：审核裁决 `PUT /api/v1/admin/audit/tasks/{taskId}/approve|reject`（经 Feign 回写章节 / 评论状态）、公告增删改查 `GET/POST/PUT/DELETE /api/v1/admin/announcements[/{id}]`、订单对账 `GET /api/v1/admin/orders`、用户资料更新 `PUT /api/v1/users/{id}`（本人或管理员）。其中 `/api/v1/internal/**` 为服务间端点，**不在网关任何路由内**，仅供 Feign 调用。
* 【已修复·16-18】**V6 迁移**（`moyue-common/src/main/resources/db/migration/V6__announcement_and_reward_fix.sql`）：为 `reward_order` 补 `is_deleted` 列——原先 V1 漏建该列而实体含 `isDeleted` + 全局 `logic-delete-field: isDeleted` 生效，导致对 `reward_order` 的任何 MyBatis-Plus 查询都会追加 `is_deleted = 0` 并报 `Unknown column 'is_deleted'`；同时新建 `announcement` 表（公告 CRUD 的地基），并改正 `payChannel` 字段类型 `String → Integer`。
* 【已修复·16-19】审核回写显式校验 `R.code`：全局异常处理器以 HTTP 200 + `R.code != 0` 承载业务错误，Feign 默认不抛异常，故 `AuditService` 必须显式判码，否则下游「回写失败」会被误判为成功、审核任务被错误置为已完成且不可重试。
* 【新增·P0-4】**前端页面补齐**（`moyue-web`）：由 4 个 `.vue` 扩到 **21 个页面**——书城、书籍详情、阅读器、我的书架、我的作品、章节管理、博客广场、帖子详情、即时通讯、积分商城、打赏与稿酬、消息中心、个人中心，以及管理后台 5 页（数据概览 / 内容审核 / 公告管理 / 订单对账 / 用户管理）；配套 15 个 api 模块与 4 个公共组件（`PageHeader` / `CoverImage` / `StatusTag` / `EmptyState`）。
* 【新增·设计体系】`src/styles/theme.css` 建立完整设计令牌（品牌色 / 文字层级 / 圆角 / 阴影 / 间距 / 字号 / 语义色），并**接管 Element Plus 主题变量**（主色由默认蓝改为墨阅绯红 `--moyue-crimson`）、接入 `zh-cn` 语言包；`request.ts` 补 `put` / `del`；`src/api/types.ts` 统一分页与状态字典，消除「同一状态两套文案」。
* 【新增·后端】`GET /api/v1/books/mine`（`BookController` + `BookService.listMyBooks`）：原 `GET /books` 不支持按作者过滤，作者后台「我的作品」无地基；authorId 取网关注入头，前端无法伪造。端点总数 68 → 69，`openapi.yaml` 已同步。
* 【已修复·16-8】**契约与 DDL 同步**（`deliverables/`）：
  * `openapi.yaml` 由设计期产物升级为 **2.0.0**：21 paths → **49 paths / 68 operations**（脚本静态提取全部 `*Controller.java` 端点后逐条对齐），新增 `components` 段——`bearerAuth` 安全方案、8 个公共参数（Page/Size/Id/BookId/ChapterId/PostId/ConversationId/OrderNo）、4 种响应（Ok / OkPage / OkLogin / OkUser）、19 个 schema（`R`、`PageResult` 及各请求体）。
  * `moyue-schema.sql` 由 8 表 → **20 表**当前状态快照，与 Flyway V1–V6 逐表对齐（`reward_order` 含 V6 补建的 `is_deleted`；`author_income` / `audit_task` 如实保持无 `is_deleted`）。文件头已标注「快照会 DROP 重建，权威来源为 Flyway 迁移」。
  * 校验结论：端点集合比对 **68 = 68、双向差集 0**；表集合比对 **20 = 20、缺失 0、多余 0**；YAML 可解析且 131 处 `$ref` 全部可解析、无悬空引用。
  * 剩余风险：仍为人工同步，未接入 `springdoc-openapi`；改动接口后需重跑比对。
