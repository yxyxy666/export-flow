// 设计：[函数清单](../../../specs/002-order-management/function-design-reference.md)。测试：TC-211。
import { QueryClient } from '@tanstack/react-query';
/** 各应用实例独立缓存；禁止隐式提交重试、定时刷新和窗口焦点查询。 */
export function createQueryClient(): QueryClient {
  return new QueryClient({ defaultOptions: {
    queries: { retry: false, refetchOnWindowFocus: false, refetchOnReconnect: false, staleTime: 0, gcTime: 0 },
    mutations: { retry: false },
  } });
}
