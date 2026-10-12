// 设计：[函数清单](../../../../../specs/002-order-management/function-design-reference.md)。测试：TC-201/202/203/211。
import { useMemo } from 'react';
import { keepPreviousData, useQuery } from '@tanstack/react-query';
import type { OrderFilterFormValues, OrderQuery, PageSize, SortDirection } from '../types/order.types';
import { useOrderQueryState } from './use-order-query-state';
import { normalizeOrderFilter } from '../models/order-query';
import { toOrderViewModel } from '../models/order-view-model';
import { queryOrders } from '../../../shared/api/api';
import { normalizeApiError } from '../../../shared/api/api-error';
/** 缓存键完整包含已提交条件；旧数据仅用于展示，不作为新范围导出凭据。 */
export function useOrders() {
  const state = useOrderQueryState();
  const queryIdentity = JSON.stringify(state.query);
  const stableQuery = useMemo(() => state.query, [queryIdentity]);
  const result = useQuery({ queryKey: ['orders', stableQuery],
    queryFn: ({ signal }) => queryOrders(stableQuery, signal), placeholderData: keepPreviousData });
  function setQuery(next: OrderQuery) {
    if (JSON.stringify(next) === JSON.stringify(state.query)) void result.refetch();
    else state.setQuery(next);
  }
  return { ...state, pageResult: result.data, rows: result.data?.items.map(toOrderViewModel) ?? [],
    isLoading: result.isFetching, isValidResult: result.isSuccess && !result.isPlaceholderData && !result.isFetching,
    error: result.error ? normalizeApiError(result.error).error : undefined,
    submitFilter(values: OrderFilterFormValues) { setQuery({ ...state.query, filter: normalizeOrderFilter(values), page: 1 }); },
    reset() { setQuery({ filter: {}, page: 1, pageSize: 20, sortDirection: 'DESC' }); },
    changePage(page: number, pageSize: PageSize) {
      setQuery({ ...state.query, page: pageSize !== state.query.pageSize ? 1 : page, pageSize });
    },
    changeSort(sortDirection: SortDirection) { setQuery({ ...state.query, page: 1, sortDirection }); },
    async refresh() { await result.refetch(); },
  };
}
