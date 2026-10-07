// 设计与验证：specs/001-project-bootstrap/plan.md、tasks.md；本期仅静态骨架。
export type BusinessPath = '/orders' | '/export-tasks';

export interface NavigationItem {
  readonly path: BusinessPath;
  readonly label: string;
  readonly title: string;
}

/** 导航元数据的唯一来源；共享层不依赖应用、布局或业务模块。 */
export const NAVIGATION_ITEMS = [
  { path: '/orders', label: '订单管理', title: '订单管理' },
  { path: '/export-tasks', label: '任务管理', title: '任务管理' },
] as const satisfies readonly NavigationItem[];

/** 去除尾部斜杠后匹配固定路径，未知地址不选中业务菜单。 */
export function resolveNavigation(pathname: string): NavigationItem | undefined {
  const normalizedPath = pathname.replace(/\/+$/, '') || '/';
  return NAVIGATION_ITEMS.find(item => item.path === normalizedPath);
}
