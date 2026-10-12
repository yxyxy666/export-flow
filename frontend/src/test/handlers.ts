// 规格与进度：[任务记录](../../../specs/002-order-management/tasks.md#测试进度与证据)
import { http, HttpResponse } from 'msw';
import { setupServer } from 'msw/node';

/** 纯合成订单；64位ID、公式开头原值、前导零和时区边界不能被转换或损坏。 */
export const orders = [
  { id: '9007199254740993', orderNo: 'ORD-0001', customerName: '=测试客户',
    customerPhone: '0013800000001', status: 'PAID', channel: 'WEB', amount: '1234.50',
    currency: 'CNY' as const, region: '香港', createdAt: '2026-10-07T00:00:00Z' },
  { id: '9007199254740994', orderNo: 'ORD-0002', customerName: '合成客户乙',
    customerPhone: '0013800000002', status: 'PENDING', channel: 'APP', amount: '0.00',
    currency: 'CNY' as const, region: '深圳', createdAt: '2026-10-07T00:00:00Z' },
];
export const fields = ['orderNo', 'customerName', 'customerPhone', 'status', 'channel',
  'amount', 'currency', 'region', 'createdAt'];
export const created = {
  taskId: 'db6533d0-636c-4bd7-8a5a-e67ea9d73ae5',
  taskNo: 'EXP20261011-DB6533D0636C4BD78A5AE67EA9D73AE5',
  scopeType: 'FILTERED', totalCount: 42, fields, createdAt: '2026-10-11T00:00:00Z',
};
export interface RecordedRequest { method: string; url: URL; body?: unknown; key?: string | null }
export const requests: RecordedRequest[] = [];
export const unexpectedRequests: string[] = [];
export const handlers = [
  http.get('*/api/v1/orders', ({ request }) => {
    const url = new URL(request.url);
    requests.push({ method: 'GET', url });
    return HttpResponse.json({ items: orders, total: 42,
      page: Number(url.searchParams.get('page') ?? 1),
      pageSize: Number(url.searchParams.get('pageSize') ?? 20) });
  }),
  http.post('*/api/v1/export-tasks', async ({ request }) => {
    const body = await request.json();
    requests.push({ method: 'POST', url: new URL(request.url), body,
      key: request.headers.get('Idempotency-Key') });
    return HttpResponse.json(created, { status: 201 });
  }),
  http.all('*/api/v1/*', ({ request }) => {
    unexpectedRequests.push(`${request.method} ${new URL(request.url).pathname}`);
    requests.push({ method: request.method, url: new URL(request.url) });
    return HttpResponse.json({ code: 'TEST_UNEXPECTED_REQUEST', message: '测试发现非约定API请求', traceId: 'test-only' }, { status: 501 });
  }),
];
export const server = setupServer(...handlers);
