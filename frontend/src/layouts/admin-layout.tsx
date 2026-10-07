// 设计与验证：specs/001-project-bootstrap/plan.md、tasks.md；本期仅静态骨架。
import { useEffect } from 'react';
import type { ReactElement } from 'react';
import { Layout } from 'antd';
import { Outlet, useLocation, useNavigate } from 'react-router-dom';
import { resolveNavigation } from '../shared/navigation/navigation';
import { SidebarMenu } from './sidebar-menu';

/** 从当前地址派生菜单和文档标题，为业务页提供统一容器。 */
export function AdminLayout(): ReactElement {
  const location = useLocation();
  const navigate = useNavigate();
  const current = resolveNavigation(location.pathname);

  useEffect(() => {
    document.title = `${current?.title ?? '页面不存在'} | Export Flow`;
  }, [current]);

  return (
    <Layout className="admin-layout">
      <Layout.Sider width={216} theme="light" className="admin-sidebar">
        <div className="app-brand">Export Flow</div>
        <nav aria-label="业务导航">
          <SidebarMenu selectedPath={current?.path} onNavigate={navigate} />
        </nav>
      </Layout.Sider>
      <Layout.Content className="admin-content"><Outlet /></Layout.Content>
    </Layout>
  );
}
