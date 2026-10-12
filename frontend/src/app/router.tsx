// 设计：[订单计划](../../../specs/002-order-management/plan.md)；验证：[导航测试](../features/orders/__tests__/navigation.test.tsx)。
import { createBrowserRouter, Navigate, Outlet } from 'react-router-dom';
import { AdminLayout } from '../layouts/admin-layout';
import { OrderManagementPage } from '../features/orders/pages/order-management-page';
import { TaskManagementPage } from '../features/export-tasks/pages/task-management-page';
import { NotFoundPage } from '../pages/not-found-page';
import { NAVIGATION_ITEMS } from '../shared/navigation/navigation';
import { RouteErrorFallback } from './route-error-fallback';

/** 集中定义固定路由；子路由错误保留侧栏，布局错误也有安全恢复出口。 */
export function createAppRouter(): ReturnType<typeof createBrowserRouter> {
  return createBrowserRouter([{
    path: '/',
    element: <AdminLayout />,
    errorElement: <RouteErrorFallback />,
    children: [{
      element: <Outlet />,
      errorElement: <RouteErrorFallback />,
      children: [
        { index: true, element: <Navigate to={NAVIGATION_ITEMS[0].path} replace /> },
        { path: NAVIGATION_ITEMS[0].path, caseSensitive: true, element: <OrderManagementPage /> },
        { path: NAVIGATION_ITEMS[1].path, caseSensitive: true, element: <TaskManagementPage /> },
        { path: '*', element: <NotFoundPage /> },
      ],
    }],
  }]);
}
