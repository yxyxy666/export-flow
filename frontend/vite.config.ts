import { defineConfig } from 'vite';
import react from '@vitejs/plugin-react';

// 静态骨架仅在本机运行，不配置业务 API 代理。
export default defineConfig({
  plugins: [react()],
  server: { host: '127.0.0.1', port: 5173, strictPort: true },
  preview: { host: '127.0.0.1', port: 4173, strictPort: true },
});
