// 规格与进度：[任务记录](../../../../../specs/002-order-management/tasks.md#测试进度与证据)
import { afterEach, describe, expect, it } from 'vitest';
import { render, screen, waitFor } from '@testing-library/react';
import userEvent from '@testing-library/user-event';
import { RouterProvider } from 'react-router-dom';
import { createAppRouter } from '../../../app/router';
import { AppProviders } from '../../../app/app-providers';
import { requests } from '../../../test/handlers';

// [AC-029](../../../../../docs/product/acceptance-criteria.md#ac-029)
// [AC-041](../../../../../docs/product/acceptance-criteria.md#ac-041)
describe('TC-212 已有导航回归', () => {
  let router: ReturnType<typeof createAppRouter> | undefined;
  afterEach(() => router?.dispose());
  function mount(path: string) {
    window.history.replaceState(null, '', path);
    router = createAppRouter();
    render(<AppProviders><RouterProvider router={router} useTransitions={false} /></AppProviders>);
  }
  it('根路径回到订单页，URL和标题一致', async () => {
    mount('/');
    await waitFor(() => expect(window.location.pathname).toBe('/orders'));
    expect(screen.getByRole('heading', { name: '订单管理' })).toBeVisible();
  });
  it('菜单进入静态任务页，URL与选中菜单一致且不请求业务API', async () => {
    mount('/export-tasks');
    expect(await screen.findByRole('heading', { name: '任务管理' })).toBeVisible();
    expect(screen.getByRole('menuitem', { name: '任务管理' })).toHaveClass('ant-menu-item-selected');
    expect(window.location.pathname).toBe('/export-tasks');
    expect(requests).toEqual([]);
  });
  it('未知地址显示404，可通过键盘返回订单页', async () => {
    mount('/unknown-path');
    expect(await screen.findByText('页面不存在')).toBeVisible();
    const back = screen.getByRole('link', { name: '返回订单管理' });
    back.focus();
    await userEvent.keyboard('{Enter}');
    await waitFor(() => expect(window.location.pathname).toBe('/orders'));
    expect(screen.getByRole('heading', { name: '订单管理' })).toBeVisible();
  });
  it('大小写不匹配仍显示404，菜单无错误选中', async () => {
    mount('/ORDERS');
    expect(await screen.findByText('页面不存在')).toBeVisible();
    expect(screen.getByRole('menuitem', { name: '订单管理' })).not.toHaveClass('ant-menu-item-selected');
  });
});
