// 设计与验证：specs/001-project-bootstrap/plan.md、tasks.md；本期仅静态骨架。
import type { ReactElement } from 'react';
import { RouterProvider } from 'react-router-dom';
import { AppProviders } from './app-providers';
import { createAppRouter } from './router';

const router = createAppRouter();

/** 组合中文界面配置和唯一的应用路由入口。 */
export function App(): ReactElement {
  return <AppProviders><RouterProvider router={router} /></AppProviders>;
}
