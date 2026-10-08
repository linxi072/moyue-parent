# 墨阅小说网 · moyue-parent

> 墨阅小说网后端与管理的**单一伞仓（monorepo）**。后端提供两种可运行形态——**单体（boot）** 与 **微服务（cloud）**，二者共享同一套业务代码与 Flyway 迁移，前端为 Vue3 管理后台。

---

## 一、仓库结构

```
moyue-parent/
├── moyue-boot/        # 单体后端：Spring Boot 3.2 多模块，可直接 java -jar 启动，无需注册中心
├── moyue-cloud/        # 微服务后端：Gateway + Auth + System + 7 业务服务（content/social/commerce/
│                       #            search/message/risk/ai），依赖 Nacos 服务发现
├── moyue-web/          # 管理后台前端：Vue3 + Vite5 + Element Plus + Pinia
├── e2e/                # 端到端冒烟脚本（Python + requests），覆盖 8 个场景
├── docs/               # 架构 / 设计 / 运维文档（保留）
├── deliverables/       # 交付物
├── scripts/            # 运维脚本
├── tools/              # 工具
├── .env.example        # 环境变量示例
├── .gitignore
└── README.md
```

> **双后端一致性**：`moyue-boot` 与 `moyue-cloud` 的业务源码通过 `sync.sh` 逐字节比对保持一致（仅 `moyue-auth/pom.xml` 因两仓角色不同列入豁免：cloud=可执行服务，boot=依赖库）。一次修复同时覆盖两套架构。

---

## 二、技术栈

| 层 | 选型 |
| --- | --- |
| 语言 / JDK | Java 21（构建兼容 17） |
| 框架 | Spring Boot 3.2.5 |
| 持久层 | MyBatis-Plus 3.5.7 + Flyway（V12–V43） |
| 数据源 / 缓存 | Druid + Redis / Redisson |
| 微服务 | Spring Cloud Gateway / OpenFeign / Nacos 2.3（仅 cloud 形态） |
| 前端 | Vue3 + Vite5 + Element Plus + Pinia |
| 安全 | JWT（`X-User-Id` 由网关注入，防前端伪造） |

---

## 三、七大业务管理模块（已深化为可运营模块）

每个模块均从「后台管理脚手架」升级为带**状态流转**与**异常分支**的真实领域能力：

| 模块 | 后端路径 | 核心运营能力 |
| --- | --- | --- |
| content | `moyue-{boot,cloud}/moyue-modules/moyue-content` | 章节草稿/发布/下架/排序、作品上/下架、全局书架查询 |
| social | `…/moyue-social` | 评论置顶/下架/恢复/删除、`ImConversation` 会话禁用与运营查看消息 |
| commerce | `…/moyue-commerce` | 订单状态机、支付/退款幂等、积分账户与兑换/签到 |
| search | `…/moyue-search` | 热词管理；热词榜按「权重→搜索次数→id」稳定排序 |
| message | `…/moyue-message` | 站内信管理、未读计数、消息模板 |
| risk | `…/moyue-risk` | 举报工单流转（待处理→已处理）、敏感词多级命中（拦截优先于警告） |
| ai | `…/moyue-ai` | 任务状态机（排队→运行→完成/失败）、配额扣减/重置/不足拒绝 |

> 数据库迁移统一由 `moyue-system` 执行（Flyway 仅在该模块开启）。业务模块涉及的新表/新列见 `V35__* ` ~ `V43__*`。

---

## 四、快速开始

### 4.1 依赖（Docker 一键起）

```bash
docker run -d --name moyue-mysql  -p 3306:3306   -e MYSQL_ROOT_PASSWORD=root -e MYSQL_DATABASE=moyue mysql:8.0
docker run -d --name moyue-redis  -p 6379:6379   redis:7-alpine
# 仅微服务形态需要 Nacos（注意 2.x 需额外暴露 gRPC 端口 9848）
docker run -d --name moyue-nacos  -p 8848:8848 -p 9848:9848 \
  -e MODE=standalone -e JVM_XMS=256m -e JVM_XMX=384m nacos/nacos-server:v2.3.2
```

### 4.2 单体形态（moyue-boot）

```bash
cd moyue-boot
mvn -Plocal-jdk20 -DskipTests package
java -jar moyue-modules/moyue-system/target/moyue-system-*.jar
# 管理后台接口前缀：/api/v1/admin/**
```

### 4.3 微服务形态（moyue-cloud）

```bash
cd moyue-cloud
mvn -Plocal-jdk20 -DskipTests package
# 依次启动：system -> auth -> 各业务服务 -> gateway
# 或参考 e2e/start-cloud.sh 批量拉起（需先 unset SERVER__PORT 以免被沙箱环境变量劫持端口）
# 网关入口：http://localhost:8080/api/v1/...
```

### 4.4 前端（moyue-web）

```bash
cd moyue-web
npm install
npm run dev          # 开发
npm run build        # 产物 dist/，类型检查 vue-tsc --noEmit
```

---

## 五、测试与自测验证

### 5.1 单元测试（Mockito，无需起服务）

```bash
cd moyue-cloud
mvn -Plocal-jdk20 -pl moyue-modules/moyue-content,moyue-modules/moyue-social,\
moyue-modules/moyue-risk,moyue-modules/moyue-ai test
# 覆盖：content 5 / social 11 / risk 7 / ai 10 —— 共 33 例，全部通过
```

### 5.2 端到端冒烟（e2e/）

依赖 MySQL + Redis + Nacos 全部就绪，且微服务栈已启动：

```bash
cd e2e
bash start-infra.sh     # 拉起并等待基础设施
bash start-cloud.sh     # 拉起 10 个微服务
python3 ai_e2e.py        # M5 AI：任务状态机 + 配额
python3 content_e2e.py   # M6 内容：章节生命周期 + 书架
python3 social_e2e.py    # M7 社交：评论运营 + IM 会话
python3 cloud_e2e.py commerce_e2e.py search_e2e.py message_e2e.py risk_e2e.py
# 8 个脚本共 137 项断言，全部通过
```

> E2E 脚本已做幂等处理（开头重置测试数据、结尾还原配额），可重复运行。

---

## 六、API 约定

- 管理后台前缀：`/api/v1/admin/**`（后端 `Constants.ADMIN_PATH_PREFIX`）
- 业务 C 端前缀：`/api/v1/**`
- 统一响应：`R<T>`；分页：`PageResult<T>`（`total` + `records`）
- 异常码：`PARAM_ERROR=10001`、`NOT_FOUND=20001`、`PAY_FAILED=50001`、`FORBIDDEN`、`UNAUTHORIZED`
- 权限注解：`@RequiresPermissions("module:action:op")`

---

## 七、文档索引（docs/）

架构说明书、运维部署手册、时序图、AI 客服训练手册、迭代计划等见 `docs/` 目录。
