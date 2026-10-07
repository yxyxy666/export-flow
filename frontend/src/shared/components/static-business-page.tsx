// 设计与验证：specs/001-project-bootstrap/plan.md、tasks.md；本期仅静态骨架。
import type { ReactElement } from 'react';

export interface StaticBusinessPageProps { title: string }

/** 两个静态页面共用语义化标题，不添加业务数据或操作。 */
export function StaticBusinessPage({ title }: StaticBusinessPageProps): ReactElement {
  return <section className="static-business-page"><h1>{title}</h1></section>;
}
