// 规格与进度：[任务记录](../../../../../specs/002-order-management/tasks.md#测试进度与证据)
import { describe, expect, it } from 'vitest';
import { reduceOrderSelection } from '../models/order-selection';

// 增量规格：[任务记录](../../../../../specs/003-select-all-filtered/tasks.md#测试进度与证据)
// [AC-073](../../../../../docs/product/acceptance-criteria.md#ac-073)
// [AC-076](../../../../../docs/product/acceptance-criteria.md#ac-076)
// [AC-077](../../../../../docs/product/acceptance-criteria.md#ac-077)
// [AC-078](../../../../../docs/product/acceptance-criteria.md#ac-078)
// [AC-033](../../../../../docs/product/acceptance-criteria.md#ac-033)
type DraftSelection = { mode: 'INCLUDE' | 'EXCLUDE'; ids: ReadonlySet<string>; notice?: string };
function transition(state: DraftSelection, action: Record<string, unknown>) {
  // 只适配待新增动作的测试类型，不实现任何选择行为；调用真实reducer。
  return reduceOrderSelection(state, action as Parameters<typeof reduceOrderSelection>[1]) as DraftSelection;
}
describe('003 全局选择集合', () => {
  it('TC-301 全选清除原例外，以排除空集合表示所有未加载页', () => {
    const state: DraftSelection = { mode: 'INCLUDE', ids: new Set(['9007199254740993']) };
    const next = transition(state, { type: 'SELECT_ALL' });
    expect(next.mode).toBe('EXCLUDE'); expect([...next.ids]).toEqual([]);
    expect([...state.ids]).toEqual(['9007199254740993']);
  });
  it.each(['INCLUDE', 'EXCLUDE'] as const)('TC-301 %s全局反选两次恢复集合且不依赖页ID', mode => {
    const state: DraftSelection = { mode, ids: new Set(['1', '9007199254740993']) };
    const inverted = transition(state, { type: 'INVERT_ALL' });
    expect(inverted.mode).toBe(mode === 'INCLUDE' ? 'EXCLUDE' : 'INCLUDE');
    expect([...inverted.ids]).toEqual([...state.ids]);
    expect(transition(inverted, { type: 'INVERT_ALL' })).toEqual(state);
    expect(transition({ mode, ids: new Set() }, { type: 'INVERT_ALL' }).ids.size).toBe(0);
  });
  it('TC-303 排除模式取消和恢复单行幂等，原集合不可变', () => {
    const original: DraftSelection = { mode: 'EXCLUDE', ids: new Set(['8']) };
    const canceled = transition(original, { type: 'TOGGLE', id: '2', isSelected: false });
    expect(canceled.mode).toBe('EXCLUDE'); expect([...canceled.ids].sort()).toEqual(['2', '8']);
    expect([...original.ids]).toEqual(['8']);
    expect(transition(canceled, { type: 'TOGGLE', id: '2', isSelected: false })).toEqual(canceled);
    expect([...transition(canceled, { type: 'TOGGLE', id: '2', isSelected: true }).ids]).toEqual(['8']);
  });
  it('TC-303 全选当前页移除该页排除，反选只改变当前页且去重', () => {
    const original: DraftSelection = { mode: 'EXCLUDE', ids: new Set(['1', '8']) };
    const selected = transition(original, { type: 'SELECT_PAGE', ids: ['1', '2', '2'] });
    expect(selected.mode).toBe('EXCLUDE'); expect([...selected.ids]).toEqual(['8']);
    const inverted = transition(original, { type: 'INVERT_PAGE', ids: ['1', '2', '2'] });
    expect(inverted.mode).toBe('EXCLUDE'); expect([...inverted.ids].sort()).toEqual(['2', '8']);
    expect(transition(inverted, { type: 'INVERT_PAGE', ids: ['1', '2'] })).toEqual(original);
    expect(transition(original, { type: 'CLEAR' })).toEqual({ mode: 'INCLUDE', ids: new Set() });
  });
  it.each(['INCLUDE', 'EXCLUDE'] as const)('TC-304 %s例外容量拒绝整个动作，保留集合并提示', mode => {
    // 单次内存边界断言，不生成订单、不做性能测量。
    const ids = new Set(Array.from({ length: 200000 }, (_, i) => String(i + 1)));
    const state: DraftSelection = { mode, ids };
    const next = transition(state, { type: 'INVERT_PAGE', ids: ['200001', '200002'] });
    expect(next.mode).toBe(mode); expect(next.ids).toEqual(ids);
    expect(next.notice).toMatch(/200.?000|上限|容量|缩小/);
    const legal = transition(next, { type: 'INVERT_PAGE', ids: ['1'] });
    expect(legal.ids.size).toBe(199999); expect(legal.notice).toBeUndefined();
  });
  it.each(['0', '-1', '9223372036854775808'])('TC-304 非法例外%s保留整批集合并可见提示', id => {
    const state: DraftSelection = { mode: 'EXCLUDE', ids: new Set(['9']) };
    const next = transition(state, { type: 'INVERT_PAGE', ids: ['1', id] });
    expect(next.mode).toBe(state.mode); expect(next.ids).toEqual(state.ids);
    expect(next.notice).toBeTruthy();
  });
});

// [AC-020](../../../../../docs/product/acceptance-criteria.md#ac-020)
// [AC-033](../../../../../docs/product/acceptance-criteria.md#ac-033)
describe('TC-207 跨页选择集合', () => {
  it('切换页后继续勾选保留上一页大ID，原集合不可变', () => {
    const original: DraftSelection = { mode: 'INCLUDE', ids: new Set(['9007199254740993']) };
    const next = transition(original, { type: 'TOGGLE', id: '2', isSelected: true });
    expect([...next.ids]).toEqual(['9007199254740993', '2']);
    expect([...original.ids]).toEqual(['9007199254740993']);
  });
  it('全选当前页合并去重而不删除其他页', () => {
    const next = transition({ mode: 'INCLUDE', ids: new Set(['1']) }, { type: 'SELECT_PAGE', ids: ['2', '3', '2'] });
    expect([...next.ids].sort()).toEqual(['1', '2', '3']);
  });
  it('反选仅改变当前页，未展示的选择保留', () => {
    const next = transition({ mode: 'INCLUDE', ids: new Set(['1', '2']) }, { type: 'INVERT_PAGE', ids: ['2', '3'] });
    expect([...next.ids].sort()).toEqual(['1', '3']);
  });
  it('清空选择产生新空集合，取消单行幂等', () => {
    const original: DraftSelection = { mode: 'INCLUDE', ids: new Set(['1', '2']) };
    expect(transition(original, { type: 'CLEAR' }).ids.size).toBe(0);
    expect([...original.ids]).toEqual(['1', '2']);
    const next = transition(original, { type: 'TOGGLE', id: '2', isSelected: false });
    expect([...transition(next, { type: 'TOGGLE', id: '2', isSelected: false }).ids]).toEqual(['1']);
  });
  it.each(['0', '-1', '1.2', '9223372036854775808', 'not-an-id'])
    ('TC-304 INCLUDE非法Long ID%s不可进入集合，安全拒绝并提示', id => {
      const next = transition({ mode: 'INCLUDE', ids: new Set() }, { type: 'TOGGLE', id, isSelected: true });
      expect(next.ids.size).toBe(0); expect(next.notice).toBeTruthy();
    });
});
