// 设计与验证：specs/001-project-bootstrap/plan.md、tasks.md；本期仅静态骨架。
import type { ReactElement } from 'react';
import { NAVIGATION_ITEMS } from '../../../shared/navigation/navigation';
import { StaticBusinessPage } from '../../../shared/components/static-business-page';

/** 提供任务路由的文字占位，不展示任务或进度。 */
export function TaskManagementPage(): ReactElement {
  return <StaticBusinessPage title={NAVIGATION_ITEMS[1].title} />;
}
