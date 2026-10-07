// 设计与验证：specs/001-project-bootstrap/plan.md、tasks.md；本期仅静态骨架。
import type { ReactElement } from 'react';
import { NAVIGATION_ITEMS } from '../../../shared/navigation/navigation';
import { StaticBusinessPage } from '../../../shared/components/static-business-page';

/** 提供订单路由的文字占位，业务模块后续按独立规格填充。 */
export function OrderManagementPage(): ReactElement {
  return <StaticBusinessPage title={NAVIGATION_ITEMS[0].title} />;
}
