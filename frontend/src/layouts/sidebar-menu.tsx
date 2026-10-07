// 设计与验证：specs/001-project-bootstrap/plan.md、tasks.md；本期仅静态骨架。
import type { ReactElement } from 'react';
import { Menu } from 'antd';
import { NAVIGATION_ITEMS } from '../shared/navigation/navigation';
import type { BusinessPath } from '../shared/navigation/navigation';

export interface SidebarMenuProps {
  selectedPath?: BusinessPath;
  onNavigate: (path: BusinessPath) => void;
}

/** 使用基础菜单控件渲染固定导航，保持键盘操作与焦点。 */
export function SidebarMenu(props: SidebarMenuProps): ReactElement {
  /** 校验菜单键后通知父布局，任意外部地址不能进入导航。 */
  function handleMenuSelect(event: { key: string }): void {
    const item = NAVIGATION_ITEMS.find(item => item.path === event.key);
    if (item) props.onNavigate(item.path);
  }

  return <Menu mode="inline" selectedKeys={props.selectedPath ? [props.selectedPath] : []}
    items={NAVIGATION_ITEMS.map(item => ({ key: item.path, label: item.label }))}
    onClick={handleMenuSelect} />;
}
