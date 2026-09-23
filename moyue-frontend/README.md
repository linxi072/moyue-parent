# moyue-frontend（脚手架）

墨阅小说网前端脚手架。单体化后直接调用后端 `moyue-app` 的 `/api/v1`（:8080），不再依赖 `moyue-contract` 生成客户端。

## 开发

```bash
npm install
npm run dev        # Vite 开发服务器 :5173，/api 代理到 http://localhost:8080
```

## 构建

```bash
npm run build      # tsc --noEmit 类型检查 + vite build，产物在 dist/
npm run preview    # 预览构建产物
npm run typecheck  # 仅类型检查
```

## API 客户端样例

`src/api/sample/` 为基于 OpenAPI 生成的 Axios 客户端样例（字段取自 RewardController），`base.ts` 的 `BASE_PATH` 指向 `http://localhost:8080`。
接入具体 UI 框架时，可复用该样例结构或替换为真实生成产物。

## 目录

```
moyue-frontend/
├─ package.json          # axios 运行时 + dev/build/preview/typecheck/gen 脚本
├─ tsconfig.json         # 标准 TS 配置（strict）
├─ vite.config.ts        # 开发代理 /api → :8080
├─ index.html            # 入口
├─ .gitignore
├─ README.md
└─ src/
   ├─ main.ts            # 入口占位
   └─ api/               # API 客户端（sample 为样例）
```
