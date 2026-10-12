// 规格与进度：[任务记录](../../../specs/002-order-management/tasks.md#测试进度与证据)
import { describe, expect, it } from 'vitest';
import { created, orders, requests } from './handlers';
import { http, HttpResponse } from 'msw';
import { server } from './handlers';
import { requestJson } from '../shared/api/http-client';

// 工具验证，不作为业务验收：实际验证锁定MSW3可在当前Vitest5 Node环境拦截网络。
describe('测试环境', () => {
  it('MSW在真实fetch取消后传播信号至在途处理器', async () => {
    let started!: () => void; let release!: () => void; let signal: AbortSignal | undefined;
    const entered = new Promise<void>(resolve => { started = resolve; });
    const barrier = new Promise<void>(resolve => { release = resolve; });
    server.use(http.get('*/api/v1/orders', async ({ request }) => {
      signal = request.signal; started(); await barrier; return HttpResponse.json({});
    }));
    const controller = new AbortController();
    const fetchResult = requestJson('/api/v1/orders', { signal: controller.signal }).catch(error => error);
    try { await entered; controller.abort(); expect(signal?.aborted).toBe(true); }
    finally { release(); await fetchResult; }
  });
  it('MSW真实拦截GET并保留大ID及合成原值', async () => {
    const response = await fetch('http://localhost/api/v1/orders?page=2&pageSize=50');
    expect(response.status).toBe(200);
    expect(await response.json()).toMatchObject({ items: orders, page: 2, pageSize: 50 });
    expect(requests).toHaveLength(1);
  });
  it('MSW真实拦截POST并记录幂等头与原载荷', async () => {
    const body = { scope: { type: 'FILTERED', filter: {} }, fields: ['orderNo'] };
    const response = await fetch('http://localhost/api/v1/export-tasks', {
      method: 'POST', headers: { 'Content-Type': 'application/json', 'Idempotency-Key': 'test-key-1234567890' },
      body: JSON.stringify(body),
    });
    expect(response.status).toBe(201); expect(await response.json()).toEqual(created);
    expect(requests[0]).toMatchObject({ method: 'POST', key: 'test-key-1234567890', body });
  });
});
