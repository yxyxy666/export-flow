import { fileURLToPath } from 'node:url';
import { defineConfig, loadEnv } from 'vite';
import react from '@vitejs/plugin-react';

// 设计：订单规格quickstart；开发同源API代理，生产使用同源网关。
export default defineConfig(({ mode }) => {
  // 前后端共用项目根的端口配置，只读取端口键，不将连接凭据注入客户端。
  const environment = loadEnv(mode, fileURLToPath(new URL('..', import.meta.url)), 'EXPORT_FLOW_BACKEND_PORT');
  const backendPort = environment.EXPORT_FLOW_BACKEND_PORT || '8080';
  if (!/^\d+$/.test(backendPort) || Number(backendPort) < 1 || Number(backendPort) > 65535) {
    throw new Error('EXPORT_FLOW_BACKEND_PORT 必须为1至65535的端口号');
  }
  return {
    plugins: [react()],
    server: { host: '127.0.0.1', port: 5173, strictPort: true,
      proxy: { '/api/v1': { target: `http://127.0.0.1:${backendPort}`, changeOrigin: false } } },
    preview: { host: '127.0.0.1', port: 4173, strictPort: true },
  };
});
