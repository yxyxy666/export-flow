// 增量实施：[计划](../../../../../specs/003-select-all-filtered/plan.md)、[任务](../../../../../specs/003-select-all-filtered/tasks.md)；[测试](../__tests__/order-management.test.tsx)。
// 设计与测试：[任务记录](../../../../../specs/003-select-all-filtered/tasks.md)。
import type { OrderId, OrderFilter, OrderSelectionState, SelectionAction } from '../types/order.types';
import type { ExportScope } from '../../export-tasks/types/export-task.types';
import { orderIdSchema } from './order-schema';

/** 包含与排除共用例外集合；拒绝整批非法或超容量操作，保留原选择。 */
export function reduceOrderSelection(state: OrderSelectionState, action: SelectionAction): OrderSelectionState {
  if (action.type === 'CLEAR') return { mode: 'INCLUDE', ids: new Set() };
  if (action.type === 'SELECT_ALL') return { mode: 'EXCLUDE', ids: new Set() };
  if (action.type === 'INVERT_ALL') return { mode: state.mode === 'INCLUDE' ? 'EXCLUDE' : 'INCLUDE', ids: state.ids };
  const input = action.type === 'TOGGLE' ? [action.id] : action.ids;
  if (input.some(id => !orderIdSchema.safeParse(id).success)) return { ...state, notice: '订单标识无效，请重新选择' };
  const ids = new Set(state.ids);
  for (const id of new Set(input)) {
    if (action.type === 'INVERT_PAGE') {
      if (ids.has(id)) ids.delete(id); else ids.add(id);
    } else {
      const selected = action.type === 'TOGGLE' ? action.isSelected : action.type === 'SELECT_PAGE';
      if (selected === (state.mode === 'INCLUDE')) ids.add(id); else ids.delete(id);
    }
  }
  if (ids.size > 200000) return { ...state, notice: '选择例外超过200000条上限，请缩小范围' };
  return { mode: state.mode, ids };
}
/** 只派生当前行，不枚举其他分页。 */
export function isOrderSelected(state: OrderSelectionState, id: OrderId): boolean {
  return state.mode === 'INCLUDE' ? state.ids.has(id) : !state.ids.has(id);
}
/** 最近有效列表的参考数量；真实受理数量由服务端裁决。 */
export function getSelectionCount(state: OrderSelectionState, total: number): number {
  return state.mode === 'INCLUDE' ? state.ids.size : Math.max(0, total - state.ids.size);
}
/** 将选择意图转为既有包含范围或带排除的筛选范围。 */
export function toSelectionExportScope(state: OrderSelectionState, filter: OrderFilter): ExportScope {
  return state.mode === 'INCLUDE' ? { type: 'SELECTED', orderIds: [...state.ids] }
    : { type: 'FILTERED', filter: { ...filter }, ...(state.ids.size ? { excludedOrderIds: [...state.ids] } : {}) };
}
