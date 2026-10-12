// 规格与进度：[任务记录](../../../../../specs/002-order-management/tasks.md#测试进度与证据)
import { act, renderHook, waitFor } from '@testing-library/react';
import { describe, expect, it, vi } from 'vitest';
import { useOrderExport } from '../hooks/use-order-export';
import { createSubmission, submitOrderExport } from '../application/order-export-use-case';
import { created } from '../../../test/handlers';

// 增量：[规格与进度](../../../../../specs/003-select-all-filtered/tasks.md#测试进度与证据)
// [AC-075](../../../../../docs/product/acceptance-criteria.md#ac-075)
// [AC-051](../../../../../docs/product/acceptance-criteria.md#ac-051)
describe('TC-307 排除集合冻结与UNKNOWN', () => {
  it('真实提交用例复制并冻结排除数组及筛选，外部修改不能改变请求', () => {
    const scope = { type: 'FILTERED' as const, filter: { orderNo: 'ORIGINAL' }, excludedOrderIds: ['2', '9007199254740993'] };
    const submission = createSubmission(scope, ['orderNo'], () => 'exclusion-key-1234567890');
    scope.excludedOrderIds.push('3'); scope.filter.orderNo = 'CHANGED';
    expect(submission.request.scope).toEqual({ type: 'FILTERED', filter: { orderNo: 'ORIGINAL' }, excludedOrderIds: ['2', '9007199254740993'] });
    const exclusions = Reflect.get(submission.request.scope, 'excludedOrderIds');
    expect(Object.isFrozen(exclusions)).toBe(true);
    expect(() => exclusions.push('4')).toThrow();
    expect(Object.isFrozen(Reflect.get(submission.request.scope, 'filter'))).toBe(true);
  });
  it('断连后重试保留取消项、筛选、字段和原键，成功仅清一次选择', async () => {
    const deps = { createTask: vi.fn().mockRejectedValueOnce(new TypeError('disconnect')).mockResolvedValue(created),
      newKey: vi.fn(() => 'exclusion-key-1234567890'), clearSelection: vi.fn(), notifyCreated: vi.fn() };
    const scope = { type: 'FILTERED' as const, filter: { keyword: '原筛选' }, excludedOrderIds: ['2'] };
    const { result } = renderHook(() => useOrderExport(deps));
    act(() => result.current.open(scope));
    await act(async () => { await result.current.confirm(); });
    expect(result.current.submission?.localState).toBe('UNKNOWN');
    scope.excludedOrderIds.push('3'); scope.filter.keyword = '新筛选';
    act(() => { result.current.open({ type: 'FILTERED', filter: {} }); result.current.resetDraft(); result.current.setFields(['amount']); });
    await act(async () => { await result.current.retryUnknown(); });
    expect(deps.createTask).toHaveBeenCalledTimes(2);
    expect(deps.createTask.mock.calls[1].slice(0, 2)).toEqual(deps.createTask.mock.calls[0].slice(0, 2));
    expect(deps.createTask.mock.calls[1][0].scope).toEqual({ type: 'FILTERED', filter: { keyword: '原筛选' }, excludedOrderIds: ['2'] });
    expect(deps.newKey).toHaveBeenCalledTimes(1); expect(deps.clearSelection).toHaveBeenCalledTimes(1);
    expect(result.current.submission?.localState).toBe('CONFIRMED');
  });
});

// [AC-006](../../../../../docs/product/acceptance-criteria.md#ac-006)
// [AC-008](../../../../../docs/product/acceptance-criteria.md#ac-008)
// [AC-030](../../../../../docs/product/acceptance-criteria.md#ac-030)
// [AC-051](../../../../../docs/product/acceptance-criteria.md#ac-051)
// [AC-072](../../../../../docs/product/acceptance-criteria.md#ac-072)
// 缺失Hook/用例模块属于导入阻塞，不能据此声称业务断言已执行。
describe('TC-209/TC-210 冻结提交及页面反馈隔离', () => {
  it('固定提交深拷贝范围及字段，外部修改不影响请求', () => {
    const scope = { type: 'SELECTED' as const, orderIds: ['1'] };
    const fields = ['orderNo' as const]; const newKey = vi.fn(() => 'fixed-key-1234567890');
    const submission = createSubmission(scope, fields, newKey);
    scope.orderIds.push('2'); fields.push('orderNo');
    expect(submission.request).toEqual({ scope: { type: 'SELECTED', orderIds: ['1'] }, fields: ['orderNo'] });
    expect(newKey).toHaveBeenCalledTimes(1); expect(submission.localState).toBe('SUBMITTING');
    expect(Object.isFrozen(submission.request)).toBe(true);
    expect(Object.isFrozen(submission.request.scope)).toBe(true);
    expect(Object.isFrozen(Reflect.get(submission.request.scope, 'orderIds'))).toBe(true);
    expect(Object.isFrozen(submission.request.fields)).toBe(true);
    expect(() => (Reflect.get(submission.request.scope, 'orderIds') as string[]).push('9')).toThrow();
    expect(() => (submission.request.fields as unknown as string[]).push('amount')).toThrow();
  });
  it('纯用例确认成功仅返回结果，不承担页面操作', async () => {
    const submission = createSubmission({ type: 'FILTERED', filter: {} }, ['orderNo'], () => 'fixed-key-1234567890');
    const api = vi.fn().mockResolvedValue(created);
    const result = await submitOrderExport(submission, api);
    expect(result.localState).toBe('CONFIRMED'); expect(result.result).toEqual(created);
    expect(api).toHaveBeenCalledWith(submission.request, submission.idempotencyKey, undefined);
  });
  it.each([new TypeError('synthetic disconnect'), new DOMException('synthetic abort', 'AbortError')])
    ('网络或取消错误不能被宣告明确失败', async error => {
      const submission = createSubmission({ type: 'FILTERED', filter: {} }, ['orderNo'], () => 'fixed-key-1234567890');
      const api = vi.fn().mockRejectedValue(error); const result = await submitOrderExport(submission, api);
      expect(result.localState).toBe('UNKNOWN'); expect(result.request).toEqual(submission.request);
      expect(result.idempotencyKey).toBe(submission.idempotencyKey);
    });
  it.each(['clearSelection', 'notifyCreated'] as const)
    ('%s抛错不能把已确认创建改为UNKNOWN或重发', async operation => {
      const deps = { createTask: vi.fn().mockResolvedValue(created), newKey: vi.fn(() => 'fixed-key-1234567890'),
        clearSelection: vi.fn(), notifyCreated: vi.fn() };
      deps[operation].mockImplementation(() => { throw new Error('synthetic page operation failure'); });
      const { result } = renderHook(() => useOrderExport(deps));
      act(() => result.current.open({ type: 'FILTERED', filter: {} }));
      await act(async () => { await result.current.confirm(); });
      expect(result.current.submission?.localState).toBe('CONFIRMED');
      expect(result.current.submission?.result).toEqual(created);
      expect(deps.createTask).toHaveBeenCalledTimes(1); expect(deps.newKey).toHaveBeenCalledTimes(1);
    });
  it('UNKNOWN同键核对成功调用相同反馈，重复确认不会新建第二任务', async () => {
    const deps = { createTask: vi.fn().mockRejectedValueOnce(new TypeError('synthetic disconnect')).mockResolvedValue(created),
      newKey: vi.fn(() => 'fixed-key-1234567890'), clearSelection: vi.fn(), notifyCreated: vi.fn() };
    const { result } = renderHook(() => useOrderExport(deps));
    act(() => result.current.open({ type: 'FILTERED', filter: {} }));
    await act(async () => { await result.current.confirm(); });
    await waitFor(() => expect(result.current.submission?.localState).toBe('UNKNOWN'));
    await act(async () => { await result.current.retryUnknown(); });
    expect(result.current.submission?.localState).toBe('CONFIRMED');
    expect(deps.createTask.mock.calls[0][0]).toEqual(deps.createTask.mock.calls[1][0]);
    expect(deps.createTask.mock.calls[0][1]).toBe(deps.createTask.mock.calls[1][1]);
    expect(deps.newKey).toHaveBeenCalledTimes(1);
    expect(deps.clearSelection).toHaveBeenCalledTimes(1); expect(deps.notifyCreated).toHaveBeenCalledTimes(1);
  });
  it('SUBMITTING与UNKNOWN不能用重置、关闭或新范围破坏原提交', async () => {
    let reject!: (error: Error) => void;
    const pending = new Promise<never>((_, fail) => { reject = fail; });
    const deps = { createTask: vi.fn(() => pending), newKey: vi.fn(() => 'fixed-key-1234567890'),
      clearSelection: vi.fn(), notifyCreated: vi.fn() };
    const { result } = renderHook(() => useOrderExport(deps));
    act(() => result.current.open({ type: 'SELECTED', orderIds: ['1'] }));
    let submission!: Promise<void>;
    act(() => { submission = result.current.confirm(); });
    await waitFor(() => expect(result.current.submission?.localState).toBe('SUBMITTING'));
    const frozen = result.current.submission;
    act(() => { result.current.resetDraft(); result.current.close();
      result.current.open({ type: 'FILTERED', filter: { keyword: '另一范围' } }); result.current.setFields(['amount']); });
    expect(result.current.submission?.idempotencyKey).toBe(frozen?.idempotencyKey);
    expect(result.current.submission?.request).toEqual(frozen?.request);
    await act(async () => { reject(new Error('synthetic disconnect')); await submission; });
    expect(result.current.submission?.localState).toBe('UNKNOWN');
    act(() => { result.current.resetDraft(); result.current.close();
      result.current.open({ type: 'FILTERED', filter: {} }); result.current.setFields(['amount']); });
    expect(result.current.submission?.request).toEqual(frozen?.request);
    expect(deps.newKey).toHaveBeenCalledTimes(1); expect(deps.createTask).toHaveBeenCalledTimes(1);
  });
  it('TC-209 FILTERED嵌套筛选深度冻结，不能修改已提交条件', () => {
    const submission = createSubmission({ type: 'FILTERED', filter: { keyword: '原范围' } }, ['orderNo'], () => 'fixed-key-1234567890');
    const filter = Reflect.get(submission.request.scope, 'filter');
    expect(Object.isFrozen(filter)).toBe(true);
    expect(() => Object.defineProperty(filter, 'keyword', { value: '新范围' })).toThrow();
  });
  it('TC-210 明确拒绝纠正后使用新键，UNKNOWN重试双击只发一份原键请求', async () => {
    let resolveRetry!: (value: typeof created) => void;
    const deps = { createTask: vi.fn().mockRejectedValueOnce({ status: 422, error: {
      code: 'EXPORT_LIMIT_EXCEEDED', message: '请缩小范围', traceId: 'test', outcome: 'REJECTED' } })
      .mockRejectedValueOnce(new TypeError('disconnect')).mockImplementation(() => new Promise(resolve => { resolveRetry = resolve; })),
      newKey: vi.fn().mockReturnValueOnce('first-key-1234567890').mockReturnValue('second-key-1234567890'), clearSelection: vi.fn(), notifyCreated: vi.fn() };
    const { result } = renderHook(() => useOrderExport(deps));
    act(() => result.current.open({ type: 'FILTERED', filter: {} }));
    await act(async () => { await result.current.confirm(); });
    expect(result.current.submission?.localState).toBe('REJECTED');
    act(() => result.current.setFields(['amount']));
    await act(async () => { await result.current.confirm(); });
    expect(result.current.submission?.localState).toBe('UNKNOWN');
    expect(deps.createTask.mock.calls[1][1]).not.toBe(deps.createTask.mock.calls[0][1]);
    let retry!: Promise<void>; act(() => { retry = result.current.retryUnknown(); void result.current.retryUnknown(); });
    await waitFor(() => expect(deps.createTask).toHaveBeenCalledTimes(3));
    expect(deps.createTask.mock.calls[2].slice(0, 2)).toEqual(deps.createTask.mock.calls[1].slice(0, 2));
    await act(async () => { resolveRetry(created); await retry; });
    expect(deps.createTask).toHaveBeenCalledTimes(3); expect(deps.notifyCreated).toHaveBeenCalledTimes(1);
  });
  it.each([{ fields: [] }, { fields: ['orderNo', 'orderNo'] }, { fields: ['unknown'] }])('TC-208 非法字段配置$fields不能发送创建请求', async ({ fields }) => {
    const deps = { createTask: vi.fn(), newKey: vi.fn(), clearSelection: vi.fn(), notifyCreated: vi.fn() };
    const { result } = renderHook(() => useOrderExport(deps));
    act(() => result.current.open({ type: 'FILTERED', filter: {} }));
    try { act(() => result.current.setFields(fields as never)); await act(async () => { await result.current.confirm(); }); }
    catch { /* 输入边界可同步拒绝；必须不发请求。 */ }
    expect(deps.createTask).not.toHaveBeenCalled();
  });
});
