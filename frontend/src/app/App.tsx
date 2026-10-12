// 设计：[订单计划](../../../specs/002-order-management/plan.md)；验证：[页面测试](../features/orders/__tests__/order-management.test.tsx)。
import type { ReactElement } from 'react';
import { RouterProvider } from 'react-router-dom';
import { AppProviders } from './app-providers';
import { createAppRouter } from './router';

const router = createAppRouter();

/** 组合中文界面配置和唯一的应用路由入口。 */
export function App(): ReactElement {
  // URL与查询键同步切换，避免transition期间旧查询仍可操作；保留旧行由查询层控制。
  return <AppProviders><RouterProvider router={router} useTransitions={false} /></AppProviders>;
}
