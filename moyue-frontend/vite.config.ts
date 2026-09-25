import { defineConfig } from 'vite';

// 墨阅小说网前端开发/构建配置（单体化后后端为 moyue-app，监听 :8080）
// VITE_API_TARGET 可覆盖代理目标，便于在无 MySQL/Redis/ES 的环境下指向本地契约 mock：
//   VITE_API_TARGET=http://localhost:8081 npm run dev
const apiTarget = process.env.VITE_API_TARGET ?? 'http://localhost:8080';

export default defineConfig({
  server: {
    port: 5173,
    proxy: {
      // 开发期将所有 /api 请求代理到后端（或 mock），避免跨域
      '/api': {
        target: apiTarget,
        changeOrigin: true,
      },
    },
  },
  build: {
    outDir: 'dist',
    sourcemap: true,
  },
});
