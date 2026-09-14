# moyue-contract · 契约工具链

以 [openapi-generator](https://github.com/OpenAPITools/openapi-generator) 把各服务的 OpenAPI 契约生成为前端 **TypeScript（Axios）客户端**。

## 为什么是 Maven 插件

项目硬性约束（见 `docs/不可忽视条件.md`）要求「dev 环境不保证 MySQL/Redis/Nacos/ES 在运行」。`openapi-generator-maven-plugin` 支持 `inputSpec` 指向**已提交的静态 spec 文件**，因此整条生成链路可在 `mvn generate-sources` 阶段独立跑通，**零集群依赖**。

## 用法

### 1. 验证链路（无活集群，默认）
用提交的 sample spec 生成 TS 客户端到 `moyue-frontend/src/api/sample/`：

```bash
mvn -pl moyue-contract generate-sources
```

断言产物：`moyue-frontend/src/api/sample/` 下出现 `api/*.ts`（如 `RewardApi`）、`model/*.ts`、`index.ts`、`runtime.ts` 等。

### 2. 生成生产客户端（活集群就绪后）
复制 `pom.xml` 中 `<execution id="gen-sample">` 一段，另起一个 `<execution>`，把 `<inputSpec>` 换为对应服务的契约 URL（推荐走网关聚合路径）：

```xml
<execution>
    <id>gen-account</id>
    <phase>generate-sources</phase>
    <goals><goal>generate</goal></goals>
    <configuration>
        <inputSpec>http://localhost:8080/api/v1/docs/account/v3/api-docs</inputSpec>
        <generatorName>typescript-axios</generatorName>
        <output>${project.basedir}/../moyue-frontend/src/api/account</output>
        <additionalProperties>
            <supportsES6>true</supportsES6>
            <withSeparateModelsAndApi>true</withSeparateModelsAndApi>
        </additionalProperties>
    </configuration>
</execution>
```

对 10 个服务（system/auth/account/content/social/commerce/search/message/risk/ai）各配一段，`mvn -pl moyue-contract generate-sources` 即全量产出。

## 目录

```
moyue-contract/
├─ pom.xml                                  # openapi-generator-maven-plugin（绑定 generate-sources）
├─ specs/
│  └─ sample-system.json                    # 提交的静态 sample（取自 moyue-system RewardController 真实端点）
└─ README.md
```

## 契约来源（各服务开放方式）

- 各业务服务引入 `springdoc-openapi-starter-webmvc-api`，经 `moyue-common-core` 的
  `MoyueOpenApiAutoConfiguration` 自动装配统一元信息，暴露 `/v3/api-docs`。
- `moyue-system` 保留 `springdoc-openapi-starter-webmvc-ui`，在
  `http://localhost:8080/api/v1/docs/system/swagger-ui.html`（经网关）聚合全部 10 个服务契约。
- 详细设计见 `docs/契约工具链.md`。
