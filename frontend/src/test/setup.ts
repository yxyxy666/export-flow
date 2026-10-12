// 规格与进度：[任务记录](../../../specs/002-order-management/tasks.md#测试进度与证据)
import '@testing-library/jest-dom/vitest';
import { afterAll, afterEach, beforeAll, beforeEach, expect, vi } from 'vitest';
import { cleanup, configure } from '@testing-library/react';
import { requests, server, unexpectedRequests } from './handlers';

configure({ asyncUtilTimeout: 700 });
Object.defineProperty(window, 'matchMedia', { writable: true, value: vi.fn().mockImplementation(
  (query: string) => ({ matches: false, media: query, onchange: null,
    addListener: vi.fn(), removeListener: vi.fn(), addEventListener: vi.fn(),
    removeEventListener: vi.fn(), dispatchEvent: vi.fn() }),
) });
class TestResizeObserver {
  observe() {} unobserve() {} disconnect() {}
}
vi.stubGlobal('ResizeObserver', TestResizeObserver);
// jsdom不实现伪元素样式；保留真实元素样式计算供布局和可访问性断言使用。
const getComputedStyle = window.getComputedStyle.bind(window);
window.getComputedStyle = (element: Element) => getComputedStyle(element);
beforeAll(() => server.listen({ onUnhandledFrame: 'error' }));
beforeEach(() => {
  requests.length = 0; unexpectedRequests.length = 0; window.history.replaceState(null, '', '/orders');
});
afterEach(() => {
  cleanup(); server.resetHandlers(); vi.unstubAllEnvs();
  expect(unexpectedRequests, '不允许额外任务详情、状态或SSE请求').toEqual([]);
});
afterAll(() => server.close());
