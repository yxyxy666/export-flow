// 设计与验证：specs/001-project-bootstrap/plan.md、tasks.md；本期仅静态骨架。
import type { ReactElement } from 'react';
import { Result } from 'antd';
import { Link } from 'react-router-dom';
import { NAVIGATION_ITEMS } from '../shared/navigation/navigation';

/** 未匹配路由显示轻量结果与订单管理返回入口。 */
export function NotFoundPage(): ReactElement {
  return <Result status="404" title="404" subTitle="页面不存在"
    extra={<Link className="return-link" to={NAVIGATION_ITEMS[0].path}>返回订单管理</Link>} />;
}
