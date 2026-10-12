import { defineConfig } from 'vitest/config';
import react from '@vitejs/plugin-react';

// 测试准备独立配置，不提前启用生产API代理或修改生产入口。
export default defineConfig({
  plugins: [react()],
  test: {
    environment: 'jsdom',
    environmentOptions: { jsdom: { url: 'http://localhost/' } },
    setupFiles: ['./src/test/setup.ts'],
    include: ['src/features/orders/__tests__/*.test.{ts,tsx}', 'src/test/*.test.ts'],
    restoreMocks: true,
    // 页面交互大量计算样式，串行文件执行避免多个jsdom争用CPU触发整条用例超时。
    fileParallelism: false,
    clearMocks: true,
    // 多次AntD交互的jsdom样式计算可超过5秒；业务异步等待仍使用700ms断言预算。
    testTimeout: 15000,
    hookTimeout: 10000,
    reporters: ['default', 'junit', 'json'],
    outputFile: { junit: './test-results/junit.xml', json: './test-results/results.json' },
  },
});
