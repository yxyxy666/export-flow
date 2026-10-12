import { defineConfig } from 'vite';
import react from '@vitejs/plugin-react';

// 设计：订单规格quickstart；开发同源API代理，生产使用同源网关。
export default defineConfig({
  plugins: [react()],
  server: { host: '127.0.0.1', port: 5173, strictPort: true,
    proxy: { '/api/v1': { target: 'http://127.0.0.1:8080', changeOrigin: false } } },
  preview: { host: '127.0.0.1', port: 4173, strictPort: true },
});
