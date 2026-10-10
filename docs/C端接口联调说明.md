# C 端接口前端联调说明

> 适用对象：前端（管理后台之外的用户侧 / App / H5 调用方）
> 覆盖范围：G-L 系列新增的 4 个用户侧（C 端）接口模块
> - `message` 站内信收件箱（G-L）
> - `commerce` 积分钱包（G-L′）
> - `search` 搜索发现（G-L″）
> - `ai` AI 任务与配额（G-L‴）
>
> 后端代码位置：`moyue-cloud` / `moyue-boot` 对应模块；前端封装已写入 `src/api/{message,commerce,search,ai}.ts`。

---

## 一、前端对接说明（必读）

### 1.1 基础约定

| 项 | 值 / 行为 | 说明 |
| --- | --- | --- |
| 基础路径 `baseURL` | `import.meta.env.VITE_API_BASE \|\| '/api/v1'` | 所有接口只需写相对路径，如 `request.get('/messages')` |
| 统一响应 `R<T>` | `{ code, message, data, traceId? }` | **code=0 成功**，非 0 为业务错误 |
| 响应解包 | 拦截器已把 `body.data` 直接返回 | 业务层拿到的是 `T` 本身，**不要**再 `.data` 解包 |
| 分页结构 `PageResult<T>` | `{ total: number, records: T[] }` | 列表接口直接返回该结构 |
| 鉴权 | 请求拦截器自动附加 `Authorization: Bearer <token>` | token 来自 `useUserStore().token`，登录后自动带上 |
| 网关路由 | `/api/v1/{module}/**` 已指向对应微服务 | **无需新增网关配置** |

### 1.2 调用方式（统一门面 `request`）

```ts
import request from '@/utils/request'

// GET：第二个参数是 axios 的 config，查询参数放 { params }
const page = await request.get<PageResult<MessageVO>>('/messages', { params: { page: 1, size: 10 } })

// POST：第二个参数是 body；无 body 传 null 或 {}
await request.post<boolean>('/messages/read-all')

// 路径参数：用模板字符串拼接
await request.get<AiTaskVO>(`/ai/tasks/${id}`)
```

### 1.3 错误处理（拦截器已统一，业务层只需 try/catch）

| 情况 | 前端表现 | 处理建议 |
| --- | --- | --- |
| `code !== 0`（业务错误） | 拦截器 `ElMessage.error(message)` 并 `reject` | 业务层 `try/catch` 做二次提示或静默 |
| HTTP `401` | 自动 `logout()` + 跳 `/login` | 用户重新登录；检查 token 是否过期 |
| HTTP `403` | `ElMessage.error('无权限访问该资源')` | 多半是**调错了路径**（见 §四 常见问题） |
| HTTP `429` | `ElMessage.error('请求过于频繁')` | Sentinel 限流，前端做防抖 / 重试退避 |

> 注意：C 端接口**不在网关白名单**内，必须带有效 `Bearer` token 才能访问；未登录直接调会触发 `401`。

---

## 二、请求参数与响应结构对照

### 2.1 站内信收件箱 `message` 模块（G-L）

基路径 `/api/v1/messages`，全部限定当前登录用户，无需传 `userId`。

| 接口 | 方法 | 请求参数 | 响应结构 |
| --- | --- | --- | --- |
| 我的消息列表 | `GET /messages` | `MessageQuery`：`page`,`size`,`type?`(1系统/2活动/3私信),`readFlag?`(0未读/1已读) | `PageResult<MessageVO>` |
| 未读计数 | `GET /messages/unread-count` | 无 | `number` |
| 标记已读 | `POST /messages/read` | body：`number[]`（消息 ID 列表） | `boolean` |
| 全部已读 | `POST /messages/read-all` | 无 | `boolean` |

`MessageVO` 字段：

| 字段 | 类型 | 含义 |
| --- | --- | --- |
| `id` | number | 消息 ID |
| `fromUser` | number | 发送人（0 = 系统） |
| `toUser` | number | 接收人（当前用户） |
| `title` | string | 标题 |
| `content` | string | 正文 |
| `type` | number | 1 系统 / 2 活动 / 3 私信 |
| `readFlag` | number | 0 未读 / 1 已读 |
| `createTime` | string | 创建时间（ISO） |

### 2.2 积分钱包 `commerce` 模块（G-L′）

基路径 `/api/v1/points`，全部限定当前登录用户。

| 接口 | 方法 | 请求参数 | 响应结构 |
| --- | --- | --- | --- |
| 我的余额 | `GET /points/balance` | 无 | `number`（BigDecimal，无账户返回 0） |
| 我的积分明细 | `GET /points/records` | `PointsLogQuery`：`page`,`size`,`bizType?` | `PageResult<PointsLogVO>` |
| 每日签到 | `POST /points/sign` | 无 | `number`（签到后余额） |

`PointsLogVO` 字段：

| 字段 | 类型 | 含义 |
| --- | --- | --- |
| `id` | number | 流水 ID |
| `userId` | number | 用户 |
| `bizType` | number | 1 充值 / 2 打赏 / 3 消费 / 4 退款 / **5 签到** |
| `changeAmount` | number | 变动积分（正加负减） |
| `balanceAfter` | number | 变动后余额 |
| `refId` | string | 关联业务单号 |
| `createTime` | string | 时间 |

> 签到幂等：同一天重复调用只返回当前余额，**不会重复 +10**。首日签到自动开户（余额从 0 起算）。

### 2.3 搜索发现 `search` 模块（G-L″）

基路径 `/api/v1/search`，**公开只读**，无用户归属。

| 接口 | 方法 | 请求参数 | 响应结构 |
| --- | --- | --- | --- |
| 热词榜 | `GET /search/hot-words` | `limit`（默认 10，收敛到 [1,20]） | `SearchHotWordVO[]` |
| 搜索联想 | `GET /search/suggest` | `keyword`（必填，空词返回空数组） | `SearchHotWordVO[]` |

`SearchHotWordVO` 字段：`id`、`word`、`hitCount`、`weight`、`enabled`、`createTime`、`remark`。

> 联想为 MySQL 前缀 `LIKE` 降级实现（未接 ES），结果最多 10 条、仅启用中热词。

### 2.4 AI 任务与配额 `ai` 模块（G-L‴）

基路径 `/api/v1/ai`，全部限定当前登录用户。

| 接口 | 方法 | 请求参数 | 响应结构 |
| --- | --- | --- | --- |
| 我的任务列表 | `GET /ai/tasks` | `AiTaskQuery`：`page`,`size`,`taskType?`,`status?` | `PageResult<AiTaskVO>` |
| 任务详情 | `GET /ai/tasks/{id}` | 路径参数 `id` | `AiTaskVO` |
| 我的配额 | `GET /ai/quota` | 无 | `AiQuotaVO` |

`AiTaskVO` 字段：

| 字段 | 类型 | 含义 |
| --- | --- | --- |
| `id` | number | 任务 ID |
| `taskType` | number | 1 续写 / 2 润色 / 3 摘要 / 4 大纲 |
| `prompt` | string | 提示词 |
| `model` | string | 模型标识 |
| `status` | number | 0 待处理 / 1 成功 / 2 失败 |
| `result` | string | 生成结果（当前为占位文本） |
| `costTokens` | number | 消耗 token |
| `userId` | number | 发起用户 |
| `createTime` | string | 时间 |

`AiQuotaVO` 字段：`id`、`userId`、`total`（总额）、`used`（已用）、`remain`（剩余）、`createTime`。

> 任务详情按**归属校验**：查询非本人任务时按「不存在」返回（`code=20001` + message「AI 任务不存在或已下架」），前端按 404 处理即可。

---

## 三、调用示例（已封装在 `src/api/*.ts`）

### 3.1 站内信

```ts
import { pageMyMessages, myUnreadCount, readMessages, readAllMessages } from '@/api/message'

// 1) 拉取第一页未读消息
const page = await pageMyMessages({ page: 1, size: 10, readFlag: 0 })
console.log(page.total, page.records) // PageResult<MessageVO>

// 2) 顶栏红点：未读数
const unread = await myUnreadCount()

// 3) 进入详情后标记已读
await readMessages([msgId])

// 4) "全部已读" 按钮
await readAllMessages()
```

### 3.2 积分钱包

```ts
import { myPointsBalance, pageMyPointsLogs, signIn } from '@/api/commerce'

// 余额
const balance = await myPointsBalance() // number

// 明细（只看签到记录）
const logs = await pageMyPointsLogs({ page: 1, size: 20, bizType: 5 })

// 签到
const after = await signIn() // 返回签到后余额；同日重复调用返回当前余额
```

### 3.3 搜索发现

```ts
import { consumerHotWords, consumerSuggest } from '@/api/search'

// 搜索框下方热词榜
const hot = await consumerHotWords(10)

// 输入联想
const list = await consumerSuggest('斗') // 前缀匹配启用中热词
```

### 3.4 AI 任务

```ts
import { pageMyTasks, getMyTask, myQuota } from '@/api/ai'

// 我的任务列表
const tasks = await pageMyTasks({ page: 1, size: 10, status: 1 })

// 任务详情
try {
  const task = await getMyTask(id)
  console.log(task.status, task.result, task.costTokens)
} catch (e) {
  // code=20001 时表示任务不存在或非本人 —— 提示「任务不存在」
}

// 配额卡片
const quota = await myQuota() // { total, used, remain }
```

---

## 四、联调常见问题定位思路

### 4.1 调用即 `401`
- **现象**：接口直接进登录页，或控制台报 401。
- **定位**：C 端接口**不在网关白名单**，必须登录后携带有效 `Bearer` token。
  - 确认 `useUserStore().token` 非空（已登录）。
  - 确认 token 未过期（过期由拦截器自动登出）。
  - 用浏览器 DevTools → Network 看请求头是否带 `Authorization`。

### 4.2 调用即 `403 无权限`
- **现象**：提示「无权限访问该资源」。
- **定位**：C 端接口**没有**权限注解，正常不会 403。绝大多数情况是**路径调错**：
  - ✅ 正确：`/messages`、`/points/balance`、`/search/hot-words`、`/ai/tasks`
  - ❌ 错误：`/admin/message/messages`、`/admin/commerce/points`…（那是运营端路径）
  - 核对 `src/api/*.ts` 中 C 端函数路径是否以 `/` 开头（即 `/api/v1` 之后的部分），**不要**带 `/admin`。

### 4.3 返回 `code=20001`「不存在或已下架」
- **现象**：调 `getMyTask(id)` 或 `readMessages` 时报该错误。
- **定位**：
  - AI 任务详情：确认该任务 `userId` 是**当前登录用户**，非本人会按「不存在」返回（安全设计，非 bug）。
  - 消息/积分同理：传入的 `userId` 被后端忽略，强制按当前用户查；看到「不存在」说明该资源确实不属于当前用户或 ID 有误。

### 4.4 列表为空 / 计数 0
- **定位**：先确认**后端确实已有数据**（用运营端或直连 DB 核对）。
  - 热词榜为**空数组** → 检查 `moyue_search_hot_word` 是否有 `enabled=1` 的记录。
  - 消息/积分/AI 为空 → 该用户尚无相关数据，属正常（余额接口对无账户用户返回 `0`，不是 `null`）。
  - 注意 C 端接口已强制 `toUser=当前用户`，**不要**试图传 `userId` 去查别人。

### 4.5 跨域 / 404（开发环境）
- **现象**：`OPTIONS` 预检失败、`404 Not Found`。
- **定位**：
  - 开发环境走 Vite 代理：`baseURL` 为 `/api/v1`，由 `vite.config.ts` 代理到网关；确认代理目标与端口正确。
  - 生产环境：`/api/v1/**` 必须经网关；确认网关 `moyue-*` 路由已包含对应模块（message/commerce/search/ai 均已配置）。

### 4.6 数值精度 / 类型
- **余额、积分、token 均为整数**：后端 `BigDecimal` 序列化为 JSON number，JS 用 `number` 即可；不要当字符串拼接，避免精度/格式问题。
- 分页 `total` 是 `number`，直接用于分页器总数。

### 4.7 签到重复调用
- **现象**：连点「签到」多次，余额只 +10 一次。
- **定位**：**这是预期行为**（幂等）。`sign` 先查当日是否已有 `bizType=5` 流水，有则直接返回当前余额；前端可在点击后置灰 / 显示「今日已签到」。

### 4.8 联调自测清单
- [ ] 未登录调用 → 触发 401（或跳登录）
- [ ] 列表接口返回 `PageResult{total,records}`，不包 `R`
- [ ] 消息/积分/AI 列表只含当前用户数据（换账号验证隔离）
- [ ] AI 任务详情：查他人 ID → code=20001
- [ ] 热词榜 `limit>20` 实际最多 20 条
- [ ] 签到幂等：同日多次返回同一余额

---

## 五、与运营端（admin）接口的区别

| 维度 | 运营端 `/api/v1/admin/**` | C 端 `/api/v1/{module}/**` |
| --- | --- | --- |
| 调用方 | 后台管理员 | 终端用户 |
| 身份 | 管理员角色（RBAC 权限注解） | 当前登录用户（网关注入） |
| 是否传 userId | 运营端按需查任意用户 | **不接受前端 userId**，强制当前用户 |
| 典型用途 | 群发消息、调积分、看全量 | 看自己的消息/积分/任务 |

> 前端封装中，admin 函数（如 `pageMessages`、`pagePointsLogs`）与 C 端函数（如 `pageMyMessages`、`pageMyPointsLogs`）**成对存在**，请勿混用。
