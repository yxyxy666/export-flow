// 增量实施：[计划](../../../../../specs/003-select-all-filtered/plan.md)、[任务](../../../../../specs/003-select-all-filtered/tasks.md)；[测试](../__tests__/order-management.test.tsx)。
// 设计：[函数清单](../../../../../specs/002-order-management/function-design-reference.md)。测试：TC-207。
import { useReducer, useRef } from 'react';
import { getSelectionCount, reduceOrderSelection } from '../models/order-selection';
import type { OrderSelectionState } from '../types/order.types';
/** 筛选身份改变立即返回空集合，分页与排序不清跨页选择。 */
export function useOrderSelection(filterKey: string, total: number) {
  const [state, dispatch] = useReducer(reduceOrderSelection, { mode: 'INCLUDE', ids: new Set<string>() } as OrderSelectionState);
  const previousKey = useRef(filterKey);
  if (previousKey.current !== filterKey) { previousKey.current = filterKey; dispatch({ type: 'CLEAR' }); }
  return { ...state, count: getSelectionCount(state, total), dispatch };
}
