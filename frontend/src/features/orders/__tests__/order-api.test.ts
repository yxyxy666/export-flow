// 规格与进度：[任务记录](../../../../../specs/002-order-management/tasks.md#测试进度与证据)
import { afterEach, describe, expect, it, vi } from 'vitest';
import { createExportTask, queryOrders } from '../../../shared/api/api';
import { requestJson } from '../../../shared/api/http-client';
import { normalizeApiError } from '../../../shared/api/api-error';
import { created, orders } from '../../../test/handlers';

// [AC-051](../../../../../docs/product/acceptance-criteria.md#ac-051)
// [AC-072](../../../../../docs/product/acceptance-criteria.md#ac-072)
// 缺失生产模块或导出时属于加载阻塞；这些测试不提供生产替代实现。
afterEach(() => { vi.useRealTimers(); vi.restoreAllMocks(); });

// 增量：[规格与进度](../../../../../specs/003-select-all-filtered/tasks.md#测试进度与证据)
// [AC-079](../../../../../docs/product/acceptance-criteria.md#ac-079)
// [AC-008](../../../../../docs/product/acceptance-criteria.md#ac-008)
describe('TC-309 排除字段客户端契约', () => {
  it.each([{ excludedOrderIds: undefined }, { excludedOrderIds: [] }, { excludedOrderIds: ['9007199254740993', '2', '2'] }])
    ('合法排除%j通过唯一API发送字符串ID', async ({ excludedOrderIds }) => {
    const fetch = vi.spyOn(globalThis, 'fetch').mockResolvedValue(Response.json(created, { status: 201 }));
    const scope = { type: 'FILTERED' as const, filter: {}, ...(excludedOrderIds ? { excludedOrderIds } : {}) };
    const request = { scope, fields: ['orderNo'] as const };
    await expect(createExportTask(request, 'exclusion-key-1234567890')).resolves.toEqual(created);
    expect(fetch).toHaveBeenCalledTimes(1);
    expect(JSON.parse(String(fetch.mock.calls[0][1]?.body))).toEqual(request);
    expect(new Headers(fetch.mock.calls[0][1]?.headers).get('Idempotency-Key')).toBe('exclusion-key-1234567890');
  });
  it.each([null, 1, {}, [1], ['0'], ['-1'], ['1.2'], ['9223372036854775808'], ['01']].map(excludedOrderIds => ({ excludedOrderIds })))
    ('非法排除%j在网络前拒绝', async ({ excludedOrderIds }) => {
      const fetch = vi.spyOn(globalThis, 'fetch');
      await expect(createExportTask({ scope: { type: 'FILTERED', filter: {}, excludedOrderIds }, fields: ['orderNo'] } as never,
        'exclusion-key-1234567890')).rejects.toMatchObject({ kind: 'protocol', error: { outcome: 'REJECTED' } });
      expect(fetch).not.toHaveBeenCalled();
    });
  it.each([
    { type: 'SELECTED', orderIds: ['1'], excludedOrderIds: [] },
    { type: 'FILTERED', filter: {}, excludedOrderIds: [], unexpected: true },
    { type: 'FILTERED', filter: {}, excludedOrderIds: Array.from({ length: 200001 }, () => '1') },
  ])('互斥、未知字段和原长度边界不可由去重绕过', async scope => {
    const fetch = vi.spyOn(globalThis, 'fetch');
    await expect(createExportTask({ scope, fields: ['orderNo'] } as never, 'exclusion-key-1234567890'))
      .rejects.toMatchObject({ kind: 'protocol', error: { outcome: 'REJECTED' } });
    expect(fetch).not.toHaveBeenCalled();
  });
});

describe('TC-210/TC-211 HTTP协议、超时及取消', () => {
  it.each([
    { scope: { type: 'SELECTED', orderIds: ['not-an-id'] }, fields: ['orderNo'] },
    { scope: { type: 'SELECTED', orderIds: ['9223372036854775808'] }, fields: ['orderNo'] },
    { scope: { type: 'FILTERED', filter: { minAmount: 'NaN', maxAmount: '2.00' } }, fields: ['orderNo'] },
    { scope: { type: 'FILTERED', filter: { createdFrom: '2026-02-30T00:00:00Z' } }, fields: ['orderNo'] },
    { scope: { type: 'FILTERED', filter: {}, total: 10 }, fields: ['orderNo'] },
  ])('TC-210 非法创建输入安全拒绝且不发送请求：%s', async request => {
    const fetch = vi.spyOn(globalThis, 'fetch');
    await expect(createExportTask(request as never, 'fixed-key-1234567890'))
      .rejects.toMatchObject({ kind: 'protocol', error: { outcome: 'REJECTED' } });
    expect(fetch).not.toHaveBeenCalled();
  });
  it('默认十秒超时在边界中止请求，结果保持UNKNOWN', async () => {
    vi.useFakeTimers();
    let signal!: AbortSignal;
    vi.spyOn(globalThis, 'fetch').mockImplementation((_input, init) => new Promise((_resolve, reject) => {
      signal = init!.signal!;
      signal.addEventListener('abort', () => reject(new DOMException('synthetic timeout', 'AbortError')), { once: true });
    }));
    const pending = requestJson('/api/v1/export-tasks', { method: 'POST', body: {} })
      .then((value: unknown) => ({ value, error: undefined }), (error: unknown) => ({ value: undefined, error }));
    await vi.advanceTimersByTimeAsync(9999);
    expect(signal.aborted).toBe(false);
    await vi.advanceTimersByTimeAsync(1);
    expect(signal.aborted).toBe(true);
    expect((await pending).error).toMatchObject({ kind: 'timeout', error: { outcome: 'UNKNOWN' } });
    expect(vi.getTimerCount()).toBe(0);
  });
  it('外部取消传播至fetch并释放超时，不宣告后台回滚', async () => {
    vi.useFakeTimers(); const controller = new AbortController();
    let signal!: AbortSignal;
    vi.spyOn(globalThis, 'fetch').mockImplementation((_input, init) => new Promise((_resolve, reject) => {
      signal = init!.signal!;
      signal.addEventListener('abort', () => reject(new DOMException('synthetic abort', 'AbortError')), { once: true });
    }));
    const pending = requestJson('/api/v1/export-tasks', { method: 'POST', signal: controller.signal })
      .then((value: unknown) => ({ value, error: undefined }), (error: unknown) => ({ value: undefined, error }));
    await vi.advanceTimersByTimeAsync(0); controller.abort();
    expect((await pending).error).toMatchObject({ kind: 'aborted', error: { outcome: 'UNKNOWN' } });
    expect(signal.aborted).toBe(true); expect(vi.getTimerCount()).toBe(0);
  });
  it('成功读取释放计时器且不保留取消监听', async () => {
    vi.useFakeTimers(); let signal!: AbortSignal;
    vi.spyOn(globalThis, 'fetch').mockImplementation(async (_input, init) => {
      signal = init!.signal!; return Response.json({ ok: true });
    });
    const controller = new AbortController();
    expect(await requestJson('/api/v1/orders', { signal: controller.signal })).toEqual({ ok: true });
    expect(vi.getTimerCount()).toBe(0);
    controller.abort(); expect(signal.aborted).toBe(false);
  });
  it.each(['https://example.invalid/api/v1/orders', '//example.invalid/api/v1/orders', '/other', '/api/v1/../private'])
    ('相对API边界拒绝%s且不发送网络请求', async path => {
      const fetch = vi.spyOn(globalThis, 'fetch');
      await expect(requestJson(path)).rejects.toBeDefined(); expect(fetch).not.toHaveBeenCalled();
    });
  it.each([200, 201])('创建%s只调用一次POST并保留原键及有序字段', async status => {
    const fetch = vi.spyOn(globalThis, 'fetch').mockResolvedValue(Response.json(created, { status }));
    const request = { scope: { type: 'FILTERED' as const, filter: {} }, fields: ['amount', 'orderNo'] as const };
    await expect(createExportTask(request, 'fixed-key-1234567890')).resolves.toEqual(created);
    expect(fetch).toHaveBeenCalledTimes(1);
    const [input, init] = fetch.mock.calls[0];
    expect(String(input)).toBe('/api/v1/export-tasks'); expect(init?.method).toBe('POST');
    expect(new Headers(init?.headers).get('Idempotency-Key')).toBe('fixed-key-1234567890');
    expect(JSON.parse(String(init?.body))).toEqual(request);
  });
  it.each([{ ...created, taskId: 123 }, { ...created, fields: [] }, { ...created, totalCount: -1 },
    { ...created, createdAt: 'not-utc' }, { taskId: created.taskId }])
    ('损坏的成功创建响应归类为UNKNOWN，不补发任务GET', async payload => {
      const fetch = vi.spyOn(globalThis, 'fetch').mockResolvedValue(Response.json(payload, { status: 201 }));
      await expect(createExportTask({ scope: { type: 'FILTERED', filter: {} }, fields: ['orderNo'] },
        'fixed-key-1234567890')).rejects.toMatchObject({ kind: 'protocol', error: { outcome: 'UNKNOWN' } });
      expect(fetch).toHaveBeenCalledTimes(1);
    });
  it('查询协议保持64位字符串ID，数值ID不能静默舍入', async () => {
    const page = { items: orders, total: 42, page: 1, pageSize: 20 };
    const fetch = vi.spyOn(globalThis, 'fetch').mockResolvedValueOnce(Response.json(page))
      .mockResolvedValueOnce(Response.json({ ...page, items: [{ ...orders[0], id: 9007199254740992 }] }));
    const query = { filter: {}, page: 1, pageSize: 20 as const, sortDirection: 'DESC' as const };
    expect((await queryOrders(query)).items[0].id).toBe('9007199254740993');
    await expect(queryOrders(query)).rejects.toMatchObject({ kind: 'protocol' });
    expect(fetch).toHaveBeenCalledTimes(2);
  });
  it.each([new TypeError('synthetic network'), { message: 'SQL password=synthetic' },
    { code: 'UNEXPECTED', message: 'java.sql synthetic', traceId: 'test' }])
    ('不可信错误不能宣告REJECTED或透出原始内部信息', error => {
      const result = normalizeApiError(error, 503);
      expect(result.error.outcome).toBe('UNKNOWN');
      expect(result.error.message).not.toMatch(/password|java\.sql|SQL/);
    });
  it('可信服务拒绝保留稳定错误码与outcome', () => {
    const result = normalizeApiError({ code: 'EXPORT_LIMIT_EXCEEDED', message: '请缩小范围',
      traceId: 'synthetic-test', outcome: 'REJECTED', details: { currentCount: 200001, limit: 200000 } }, 422);
    expect(result).toMatchObject({ status: 422, error: { code: 'EXPORT_LIMIT_EXCEEDED', outcome: 'REJECTED' } });
  });
  it('TC-211 已提前取消不启动fetch或遗留定时器', async () => {
    vi.useFakeTimers(); const controller = new AbortController(); controller.abort();
    const fetch = vi.spyOn(globalThis, 'fetch');
    await expect(requestJson('/api/v1/orders', { signal: controller.signal })).rejects.toMatchObject({ kind: 'aborted' });
    expect(fetch).not.toHaveBeenCalled(); expect(vi.getTimerCount()).toBe(0);
  });
  it.each([new Response('not json'), new Response('{', { headers: { 'Content-Type': 'application/json' } })])
    ('TC-210 坏JSON成功响应保持UNKNOWN并释放资源', async response => {
      vi.useFakeTimers(); const controller = new AbortController(); let signal!: AbortSignal;
      vi.spyOn(globalThis, 'fetch').mockImplementation(async (_input, init) => { signal = init!.signal!; return response; });
      await expect(createExportTask({ scope: { type: 'FILTERED', filter: {} }, fields: ['orderNo'] }, 'fixed-key-1234567890', controller.signal))
        .rejects.toMatchObject({ kind: 'protocol', error: { outcome: 'UNKNOWN' } });
      expect(vi.getTimerCount()).toBe(0); controller.abort(); expect(signal.aborted).toBe(false);
    });
  it.each([{ taskId: 'not-uuid' }, { fields: ['orderNo', 'orderNo'] }, { totalCount: 200001 }])
    ('TC-210 非法创建协议字段%s不可展示成功', async change => {
      vi.spyOn(globalThis, 'fetch').mockResolvedValue(Response.json({ ...created, ...change }, { status: 201 }));
      await expect(createExportTask({ scope: { type: 'FILTERED', filter: {} }, fields: ['orderNo'] }, 'fixed-key-1234567890'))
        .rejects.toMatchObject({ kind: 'protocol', error: { outcome: 'UNKNOWN' } });
    });
  it.each([{ page: 0 }, { pageSize: 30 }, { total: -1 }, { total: 9007199254740992 },
    { items: [{ ...orders[0], amount: '1.001' }] }, { items: [{ ...orders[0], createdAt: 'not-date' }] }])
    ('TC-211 非法列表协议%s不可进入缓存', async change => {
      vi.spyOn(globalThis, 'fetch').mockResolvedValue(Response.json({ items: orders, page: 1, pageSize: 20, total: 42, ...change }));
      await expect(queryOrders({ filter: {}, page: 1, pageSize: 20, sortDirection: 'DESC' })).rejects.toMatchObject({ kind: 'protocol' });
    });
  it.each([422, 503])('TC-210 HTTP%s经客户端保留可信拒绝或UNKNOWN并释放监听', async status => {
    vi.useFakeTimers(); const controller = new AbortController(); let signal!: AbortSignal;
    vi.spyOn(globalThis, 'fetch').mockImplementation(async (_input, init) => {
      signal = init!.signal!; return Response.json({ code: status === 422 ? 'EXPORT_LIMIT_EXCEEDED' : 'CREATE_OUTCOME_UNKNOWN',
        message: '合成安全错误', traceId: 'test', outcome: status === 422 ? 'REJECTED' : 'UNKNOWN' }, { status });
    });
    await expect(createExportTask({ scope: { type: 'FILTERED', filter: {} }, fields: ['orderNo'] }, 'fixed-key-1234567890', controller.signal))
      .rejects.toMatchObject({ status, error: { outcome: status === 422 ? 'REJECTED' : 'UNKNOWN' } });
    expect(vi.getTimerCount()).toBe(0); controller.abort(); expect(signal.aborted).toBe(false);
  });
});
