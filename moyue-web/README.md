# 墨阅小说网 · 前端工程（moyue-web）

> Vue 3 + Vite 5 + TypeScript 5 + Pinia 2 + Vue Router 4 + Element Plus + Axios。
> 与后端 `moyue-parent` 配套，演示「登录 → 网关鉴权 → 书城数据获取」全链路。

## 技术选型

- 构建：Vite 5
- 框架：Vue 3.5（`<script setup>` + TS）
- 状态：Pinia 2
- 路由：Vue Router 4（含登录路由守卫）
- UI：Element Plus
- 请求：Axios（统一封装 `R<T>` 解包与鉴权拦截）

## 目录结构

```
moyue-web/
├── package.json
├── vite.config.ts        # devServer 代理 /api -> 网关 8080
├── tsconfig.json
├── index.html
└── src/
    ├── main.ts           # 入口：挂载 Pinia / Router / Element Plus
    ├── App.vue
    ├── env.d.ts
    ├── styles/theme.css  # 主题变量：绯红 / 金 / 纸底 / 墨色
    ├── utils/request.ts  # Axios 实例：注入 Authorization + 统一解 R<T> + 401/10002 跳登录
    ├── stores/user.ts    # Pinia：token / userInfo / login / logout
    ├── api/auth.ts       # login / register / refresh / me
    ├── api/book.ts       # listBooks / bookDetail
    ├── router/index.ts   # 路由表 + 路由守卫
    ├── views/Login.vue   # 登录页
    ├── views/BookList.vue# 书籍列表（鉴权后数据获取）
    └── layout/DefaultLayout.vue # 顶栏 + 退出登录
```

## 本地启动

前置：已启动后端网关(8080) / 鉴权(8081) / 书城(8082)。

```bash
# 1. 安装依赖
npm install

# 2. 启动开发服务器（默认 5173）
npm run dev
```

浏览器访问 http://localhost:5173 ：
1. 进入 `/login`，输入演示账号 `13800000000` / `123456` 登录；
2. 登录成功后自动跳转 `/books`，经网关鉴权拉取书籍列表；
3. 顶栏「退出登录」清除 token 并回到登录页。

## 关键约定（对齐后端）

- 请求基地址 `/api/v1`，经 Vite 代理 `/api` 打到网关 8080；
- 请求拦截注入 `Authorization: Bearer {accessToken}`；
- 响应拦截统一解 `R<T>`，`code !== 0` 时按错误码提示；`10002 / 10003` 或 HTTP 401 清空登录态并跳登录；
- token 与 userInfo 持久化到 `localStorage`，刷新页面保持登录态。

## 构建产物

```bash
npm run build      # 类型检查 + 生产构建到 dist/
npm run preview    # 预览构建产物
```
