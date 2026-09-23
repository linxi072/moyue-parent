import { defineConfig } from 'vite';

// 墨阅小说网前端开发/构建配置（单体化后后端为 moyue-app，监听 :8080）
export default defineConfig({
  server: {
    port: 5173,
    proxy: {
      // 开发期将所有 /api 请求代理到单体后端，避免跨域
      '/api': {
        target: 'http://localhost:8080',
        changeOrigin: true,
      },
    },
  },
  build: {
    outDir: 'dist',
    sourcemap: true,
  },
});
