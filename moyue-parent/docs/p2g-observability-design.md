# P2-G 可观测性增强 · 设计文档（PRD + 架构）

> 版本：v1（2026-09-19 落地）
> 来源：确认结论（docs/后续迭代更新计划.md §4 ③ P2-G 已纳入 P2 阶段一）
> 设计原则：配置驱动优雅降级、H2 测试（禁用 Docker）、逻辑删除逐实体 @TableLogic、不回退已落地结构与版式。

---

## 1. 背景与目标

Sentinel 已接入（限流/熔断），但缺**分布式追踪 / 指标 / 日志聚合**三件套，运维无法串联链路、无法抓取指标、无法按 traceId 检索日志。

**目标**：全服务统一可观测性，支撑《运维部署手册》与 ELK/Grafana 体系。

**验收（三条，缺一不可）**：
1. 关键链路 **TraceId 串联**（跨服务调用链共享同一 traceId，日志可据此检索）。
2. **Prometheus 指标可采**（各服务暴露 `/actuator/prometheus`）。
3. **ES 日志可查**（日志为 JSON 且带 traceId/userId，由 Filebeat 进 ES）。

**非目标**：告警规则编排、具体追踪后端（Jaeger/Zipkin）部署、Grafana 运行实例（仅提供采集配置与看板 JSON 草稿，不启动外部服务）。

---

## 2. 技术选型

| 能力 | 选型 | 说明 |
|---|---|---|
| 指标 | Micrometer + `micrometer-registry-prometheus` + Spring Boot Actuator | `/actuator/prometheus` 自带，无需自建采集端 |
| 追踪 | 请求头 `X-Trace-Id` 透传（**非** 重后端 OTel 收集器） | 网关生成 → MDC → Feign 透传；满足「串联 + ES 可查」，零外部依赖 |
| 日志 | Spring Boot `logging.pattern.console/file` 属性注入单行 JSON（含 MDC: traceId/userId/service），**零外部依赖**（不引 logstash encoder） | 控制台即 JSON；设 `logging.file.path` 后落盘，Filebeat→ES 按既有 ELK 手册 |
| 接入点 | 统一收敛到 `moyue-common-observability` 模块 | 挂 `moyue-common-security` 传递依赖，全服务零改 pom 即得 |

> 备注：OpenTelemetry SDK + OTLP 导出器为可选后续增强（需部署 collector），本期以轻量头透传达成验收，不引入重后端。

---

## 3. 架构与数据流

```
客户端
  │ X-Trace-Id（可选，缺失则由网关生成）
  ▼
[moyue-gateway] TraceIdGlobalFilter(WebFlux, @Order(-2))
  │ 生成/透传 X-Trace-Id，注入下游请求头 + 回写响应头
  ▼  lb://moyue-content
[moyue-content] TraceIdFilter(Servlet) → MDC(traceId,userId) → 业务 → JSON 日志(traceId)
  │ 经 Feign 调 moyue-search
  ▼  TraceIdFeignInterceptor 把 MDC.traceId 注入 RequestTemplate 头
[moyue-search] TraceIdFilter 读 X-Trace-Id → MDC → JSON 日志（同 traceId）
```

- 网关（WebFlux）与服务（Servlet）使用不同过滤器实现，但共用同一 `Constants.TRACE_ID_HEADER` 与 `TraceIdGenerator`。
- `X-User-Id` 由网关注入、服务 `TraceIdFilter` 顺带写入 MDC，使日志同时带用户维度。

---

## 4. 交付物清单

| 类型 | 文件 | 作用 |
|---|---|---|
| 新模块 | `moyue-common/moyue-common-observability/` | 可观测性聚合组件 |
| 常量 | `moyue-common-core/.../Constants.java` | 新增 `TRACE_ID_HEADER = "X-Trace-Id"` |
| 过滤器 | `.../obs/TraceIdFilter.java` | Servlet 侧 MDC + 响应头回写 |
| 拦截器 | `.../obs/TraceIdFeignInterceptor.java` | Feign 透传 traceId/userId |
| 工具 | `.../obs/TraceIdGenerator.java` | 32 位 hex traceId |
| 自动装配 | `.../obs/ObservabilityAutoConfiguration.java` | 注册上述 Bean（Servlet + 配置开关） |
| 配置后置 | `.../obs/ObservabilityEnvironmentPostProcessor.java` | 默认暴露 prometheus 端点 |
| 日志格式 | 由 `ObservabilityEnvironmentPostProcessor` 经 `logging.pattern.console/file` 注入（**不**在库内放 `logback-spring.xml`，避免抢占消费者日志配置 / XML 解析风险） | 单行 JSON 带 traceId/userId/service |
| 网关 | `moyue-gateway/.../filter/TraceIdGlobalFilter.java` | WebFlux 侧 traceId 生成/透传 |
| 配置 | `moyue-modules/moyue-system/src/main/resources/application.yml` | 显式补 `prometheus` 暴露 |
| 测试 | `.../obs/TraceIdFilterTest.java` / `TraceIdFeignInterceptorTest.java` | 单元测试覆盖生成/透传/清理 |
| 文档 | 本文件 + 计划 §4 状态更新 | — |

---

## 5. 配置开关与降级

- `moyue.observability.enabled`（默认 true，缺失即启用）：置 false 关闭 TraceId 注入（actuator/Prometheus 仍在 classpath，仅不透传）。
- `logging.file.path`（默认 `./logs`）：JSON 日志落盘路径。
- 端点暴露默认 `prometheus,health,info`；`moyue-system` 已显式配置 `health,info,metrics,prometheus`（不覆盖）。

---

## 6. 测试策略（H2 / 单测，禁 Docker）

- `TraceIdFilterTest`：缺失生成、已有透传、响应头回写、MDC 清理、userId 入 MDC。
- `TraceIdFeignInterceptorTest`：缺失生成并注入头、MDC 已有则透传。
- 全 reactor `mvn test` 回归：确认 observability 传递依赖不破坏既有 H2 基线。

---

## 7. 后续（可选，不在本期）

- 接 OpenTelemetry + OTLP 到 tracing 后端。
- 提供 Grafana dashboard JSON 与 Prometheus `scrape_configs` 片段（本期仅文档说明，见 §3 注释）。
- 业务关键路径打 `@Timed` / 自定义 `Counter`/`Timer`（运营看板指标）。
