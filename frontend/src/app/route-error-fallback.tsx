// 设计与验证：specs/001-project-bootstrap/plan.md、tasks.md；本期仅静态骨架。
import type { ReactElement } from 'react';
import { Alert } from 'antd';
import { NAVIGATION_ITEMS } from '../shared/navigation/navigation';

/** 提供同源重载恢复入口，不显示异常原文或内部细节。 */
export function RouteErrorFallback(): ReactElement {
  return <Alert type="error" title="页面暂时无法显示" description={
    <a href={NAVIGATION_ITEMS[0].path}>返回订单管理</a>
  } />;
}
