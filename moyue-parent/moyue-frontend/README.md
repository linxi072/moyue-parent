# moyue-frontend（脚手架）

墨阅小说网前端脚手架。当前仅用于**接收** `moyue-contract` 由 OpenAPI 契约生成的 TypeScript（Axios）客户端。

## 生成 API 客户端

在**项目根**（`moyue-parent/`）执行：

```bash
mvn -pl moyue-contract generate-sources
```

产物落到 `src/api/<service>/`（默认 sample：`src/api/sample/`），包含：

- `api/*.ts` —— 按 OpenAPI tag/operationId 生成的 API 类（如 `RewardApi`）
- `model/*.ts` —— 请求/响应模型
- `index.ts` / `runtime.ts` —— 统一导出与 Axios 运行时封装
- `base.ts` / `common.ts` —— 基础配置

## 使用生成产物

```ts
import { Configuration, RewardApi } from './api/sample';

const api = new RewardApi(new Configuration({
  basePath: 'http://localhost:8080',           // 走网关
  accessToken: 'Bearer <jwt>'                  // 对应 OpenAPI 的 bearerAuth 安全方案
}));

// GET /api/v1/rewards?page=1&size=20
const { data } = await api.myRewards(1, 20);
```

## 目录

```
moyue-frontend/
├─ package.json          # axios 运行时依赖 + gen/typecheck 脚本
├─ tsconfig.json         # 标准 TS 配置（strict）
├─ .gitignore
├─ README.md
└─ src/
   └─ api/               # 生成产物目录（由 moyue-contract 写入 <service>/ 子目录）
```

## 说明

- 本目录不做打包配置（无 Vite/Webpack），仅承载生成产物；接入具体前端框架时再补 `vite.config.ts` 等。
- 契约来源与生成器配置见 [`../moyue-contract/README.md`](../moyue-contract/README.md) 与 [`../docs/契约工具链.md`](../docs/契约工具链.md)。
