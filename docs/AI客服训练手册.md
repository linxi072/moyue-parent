# AI 客服训练手册（墨阅小说网 · com.moyue.ai）

> ⚠️ **历史设计稿（拍平前微服务架构）**：本手册撰写于微服务时期（模块 `moyue-ai` / `moyue-search` / Feign 契约 `moyue-api-search` / Nacos 配置中心）。当前已统一为单 `moyue-app` 单体：`moyue-xxx` 即单体内业务包（如 `com.moyue.ai`），跨域调用为进程内 `@Service` 注入，已无 Nacos 配置中心 / Feign；LLM 仍为**外部供应商端点**（非微服务网关）。下文保留作历史参考，包名已同步改写。
> 适用版本：P1-1「AI 客服真实化」落地后（提交 `f1ba0d6`，2026-09-16）
> 面向对象：运营训练师 / 客服负责人 / 接入大模型的运维
> 配套代码：`com.moyue.ai`（会话/编排/索引同步）、`com.moyue.search`（RAG 召回，原 `moyue-api-search` Feign 契约已合并为进程内服务）

---

## 1. 定位与边界

墨阅智能客服「小墨」是**会话内**的自动应答能力，覆盖平台高频业务咨询（签到积分、兑换、打赏、书币会员、审核时效、账号安全、周边商城、投诉举报等）。

**已具备的能力**
- 关键词规则引擎：硬编码高频问答，**永远可用**，是兜底。
- 大模型适配层（LLM）：配置驱动，接入外部 LLM 供应商端点后真实生成。
- RAG 知识库：每轮真实对话自动沉淀进 ES（索引 `moyue-qa`），供大模型检索增强。
- 多轮上下文：最近 10 轮历史注入大模型。
- 转人工：低置信时自动追加转人工提示。

**当前边界（如实说明，避免误用）**
- 关键词规则是 **Java 代码硬编码**（`KeywordRuleReplyEngine.RULES`），**不是后台可配置界面**。新增/修改规则需改代码、编译、部署（见 §4.1）。
- 大模型接入是**配置驱动**：运维在 yml 配 `moyue.ai.llm.enabled=true` + `apiKey` + `endpoint` 即生效（单体 `application.yml`，已无 Nacos 配置中心），业务代码零改动；缺密钥或未启用时**自动降级**到关键词规则，不报错。
- 转人工是**会话内提示**（"请回复「人工」"），**不是跨进程工单派单**；人工坐席接入不在本包范围内。
- 未做敏感词前置机审；RAG 召回内容来自内部问答库，经进程内降级安全降级。

---

## 2. 系统架构

一轮对话的出入口在 `AiService.chat(userId, sessionId, content)`：

```
用户提问
  │
  ▼
会话校验（sessionId 为空则新建；非本人会话返回 10003 防越权）
  │
  ▼
落「用户消息」(role=1)
  │
  ▼
组装 ReplyContext { content, history(最近10轮), ragContext(RAG召回) }
  │
  ▼
主引擎 LlmReplyEngine.reply()  ── enabled 且 apiKey 非空 ──► 调外部 LLM 供应商端点
  │  返回 null（未启用/无密钥/端点异常/空响应）
  ▼
兜底 KeywordRuleReplyEngine.reply()  ── 关键词命中即返回
  │
  ▼
低置信(confident=false)  ► 追加 HUMAN_HANDOFF 转人工提示
  │
  ▼
落「助手消息」(role=2)
  │
  ▼
异步 indexQaAsync()  ► 推 com.moyue.search 建 ES 索引 moyue-qa（失败仅 warn，不影响对话）
```

**关键类**

| 类 | 角色 |
|---|---|
| `AiService` | 会话管理 + 级联编排 + 索引同步 |
| `AiReplyEngine`（接口） | 引擎契约：`reply(ReplyContext) → ReplyResult` |
| `LlmReplyEngine`（`@Primary`） | 大模型引擎，配置驱动，降级返回 null |
| `KeywordRuleReplyEngine` | 关键词规则引擎，永远可用 |
| `LlmGatewayClient` | OpenAI 兼容 `/v1/chat/completions` 真实外呼（外部 LLM 供应商端点） |
| `LlmProperties` | `moyue.ai.llm.*` 配置 |
| `QaSearchService`（进程内注入） | RAG 召回 → `com.moyue.search` `/api/v1/internal/search/qa/retrieve` |

---

## 3. 三类「知识」的来源与训练方式

### 3.1 关键词规则（硬编码，兜底）
来源：代码 `KeywordRuleReplyEngine.RULES`（有序 LinkedHashMap，先命中先返回，大小写不敏感）。

现有 10 条规则（命中任一关键词即返回）：

| 主题 | 触发关键词 | 话术要点 |
|---|---|---|
| 签到积分 | 签到、打卡 | 每日 10 积分，积分商城签到 |
| 积分兑换 | 积分、兑换 | 积分商城兑换，库存余额实时校验 |
| 打赏 | 打赏、赞赏、支持作者 | 微信/支付宝，作者 70% 分成 |
| 充值会员 | 充值、书币、会员 | 灰度中，以积分体系为主 |
| 密码找回 | 密码、找回、忘记 | 演示环境内置账号，正式版短信验证 |
| 审核时效 | 审核、多久、时效 | 24 小时内审核，消息中心通知 |
| 完结 | 完结、完本 | 作品管理发起完结申请 |
| 周边商城 | 周边、商城、实体、包邮、发货 | 48h 发货，整单支付 |
| 投诉举报 | 投诉、举报、侵权 | 1 工作日核实处理 |
| 寒暄 | 你好、在吗、hi、hello | 自我介绍 |

未命中任何规则 → 兜底话术 `FALLBACK`（"这个问题我还需要学习一下…"），`confident=false` → 触发转人工提示。

### 3.2 大模型适配层（配置驱动，真实生成）
来源：运维接入外部 LLM 供应商端点后，由 `LlmReplyEngine` 实时生成。

messages 拼接顺序：
1. `system` = `systemPrompt` +（若有 RAG）`\n\n参考知识库：\n{ragContext}`
2. `history` = 最近 `maxHistory`(默认 10) 轮（user/assistant 交替）
3. `user` = 当前提问

请求参数：`model`(默认 `gpt-4o-mini`)、`temperature=0.3`、`timeoutSeconds=10`。
降级条件：未启用 / `apiKey` 为空 / LLM 端点异常 / 返回空 → 返回 `null content` → 级联关键词引擎。

### 3.3 RAG 知识库（ES 自动沉淀 + 全量重建）
来源：用户与客服的**真实对话**在每轮落库成功后异步写入 ES 索引 `moyue-qa`（IK 分词）。

- 召回：`AiService` 经 `QaSearchService`（进程内注入，`com.moyue.search`）调 `/api/v1/internal/search/qa/retrieve?question=`，取 `ragTopK`(默认 3) 条 `"Q:…\nA:…"` 片段拼为 `ragContext` 注入大模型。
- 安全降级：`com.moyue.search` 未注册 / ES 不可用 / 召回异常 → `QaSearchService` 降级返回空 → `ragContext=null`，**对话照常进行，仅失去知识增强**。
- 全量重建：管理端可经 `AiService.pageQaForIndex(page,size)` 拉取全量问答对（role=1 提问与其后 role=2 回复配对），重建 `moyue-qa` 索引。删除会话时须同步调 `searchIndexService.removeQaBySession(sessionId)` 清理索引（当前无业务入口，见代码 TODO）。

---

## 4. 训练与话术调优操作

### 4.1 新增 / 修改关键词规则（需改代码）
关键词规则是代码内嵌，**运营无法在后台配置**。流程：

1. 编辑 `com/moyue/ai/.../engine/KeywordRuleReplyEngine.java` 的 `RULES` 静态块；
2. 新增一条：`RULES.put(new String[]{"关键词1","关键词2"}, "标准话术");`（LinkedHashMap 顺序即优先级，高频规则靠前）；
3. 话术约束：简洁、友好、准确，仅谈平台业务；超出范围的话术引导转人工；
4. 改完跑 `com.moyue.ai` 包测试（见运维手册 §5）确认无回归；
5. 编译部署后生效。

> 注意：仅靠关键词规则无法覆盖长尾问题，长尾应交由 LLM + RAG（§4.2/§4.3）。

### 4.2 调整 LLM 话术（配置驱动，无需改代码）
修改 `moyue.ai.llm.systemPrompt` 即可调整小墨人设与口径（默认值见 `LlmProperties`：墨阅智能客服小墨，围绕签到积分/兑换/打赏/周边商城/书币会员/审核时效/账号安全）。
其它可调项：`model`（模型）、`timeoutSeconds`（LLM 端点超时）、`maxHistory`（历史轮次）、`temperature`（代码固定 0.3，如需调需改 `LlmGatewayClient`）。

### 4.3 知识库（RAG）沉淀与治理
- 知识库=真实对话，无需手工录入；**高质量回复会被自动沉淀**，越多越准。
- 坏样本治理：误答的问答对已进入 ES，需经管理端重建索引排除或修正源头话术（关键词规则 / systemPrompt）。
- 冷启动：上线初期 ES 为空，RAG 无片段，靠关键词规则 + LLM 通用能力兜底；随对话积累逐步增强。

### 4.4 转人工策略
- 触发：关键词引擎未命中规则（`confident=false`）时，自动追加 `HUMAN_HANDOFF`：
  "（如需人工协助，请回复「人工」，工作日 10:00-18:00 在线）"。
- 如需调整触发口径（例如 LLM 低置信也转人工），改 `AiService.chat()` 的判定逻辑后部署。

---

## 5. 回答质量评估与监控

- **引擎标识**：`ReplyResult.engineName` 记录本轮由 `keyword-rule-v1` 还是 `llm-<model>` 作答，便于前端展示与日志排查。
- **置信度**：`confident=false` 即已追加转人工，可据此统计"需人工介入"占比。
- **日志**：LLM 降级有 `log.warn("LLM 生成失败，降级到关键字引擎 …")`；RAG 召回失败有 `log.warn("RAG 召回失败 …")`。
- **知识库命中**：可经 `com.moyue.search` 的 `moyue-qa` 检索接口直接验证某问题能否召回相关片段。
- **回归基线**：`com.moyue.ai` 包 10 例单测 + `com.moyue.search` 70 例（含 RAG 召回），全绿方可发布。

---

## 6. 上线 Checklist

- [ ] LLM：`moyue.ai.llm.enabled` / `apiKey` / `endpoint` 已配（不启用则自动降级，不阻断）；密钥不打印日志。
- [ ] ES：`com.moyue.search` 的 `moyue-qa` 索引可用（RAG 依赖）；不可用则自动降级。
- [ ] 关键词规则审阅：高频业务已覆盖；坏话术已修订。
- [ ] systemPrompt 人设核对。
- [ ] `com.moyue.ai` + `com.moyue.search` 测试全绿。
- [ ] 转人工提示文案与坐席在线时段一致。

---

## 7. 常见问题（FAQ）

**Q：为什么配了 LLM 还是经常回关键词话术？**
A：可能 `apiKey` 为空 / `enabled=false`（直接降级），或 LLM 端点超时/异常（每轮降级并 warn）。先查 `moyue.ai.llm.*` 配置与 LLM 端点连通性。

**Q：RAG 为什么不生效？**
A：ES/`com.moyue.search` 未启动或 `moyue-qa` 索引为空；召回失败仅 warn 不阻断。先用检索接口验证 `moyue-qa` 是否有数据。

**Q：能否在后台直接加关键词规则？**
A：不能。规则是 Java 硬编码，需按 §4.1 改代码部署。长尾问题建议优先用 LLM+RAG。

**Q：转人工为什么只是提示，没有工单？**
A：当前转人工是会话内提示，跨进程工单派单不在本包范围（见 §1 边界）。
