// 规格与进度：[任务记录](../../../../../specs/003-select-all-filtered/tasks.md#测试进度与证据)
import { afterEach, describe, expect, it, vi } from 'vitest';
import { act, fireEvent, render, screen, waitFor } from '@testing-library/react';
import userEvent from '@testing-library/user-event';
import { RouterProvider } from 'react-router-dom';
import { AppProviders } from '../../../app/app-providers';
import { createAppRouter } from '../../../app/router';
import { requests } from '../../../test/handlers';
import { ConfigProvider, DatePicker } from 'antd';
import zhCN from 'antd/locale/zh_CN';

// [AC-080](../../../../../docs/product/acceptance-criteria.md#ac-080)
// [AC-003](../../../../../docs/product/acceptance-criteria.md#ac-003)
// [AC-005](../../../../../docs/product/acceptance-criteria.md#ac-005)
// [AC-022](../../../../../docs/product/acceptance-criteria.md#ac-022)
describe('003 Ant Design日期时间控件', () => {
  let router: ReturnType<typeof createAppRouter>;
  afterEach(() => router?.dispose());
  const gets = () => requests.filter(r => r.method === 'GET');
  async function mount(search = '') {
    window.history.replaceState(null, '', `/orders${search}`); router = createAppRouter();
    render(<AppProviders><RouterProvider router={router} useTransitions={false} /></AppProviders>);
    await screen.findByText('ORD-0001'); return userEvent.setup();
  }
  async function choose(user: ReturnType<typeof userEvent.setup>, label: string, value: string) {
    const input = screen.getByLabelText(label);
    await user.click(input); await user.clear(input); await user.paste(value);
    // 必须打开真实DatePicker确认，不允许普通Input冒充组件。
    await user.click(await screen.findByRole('button', { name: '确定' }));
    expect(input).toHaveValue(value);
  }
  it('TC-310 控件测试环境可打开真实日历、确认时间及清空', async () => {
    // 组件能力基线，证明后续业务红灯不是运行器不能操作AntD造成。
    const changed = vi.fn(); const user = userEvent.setup();
    render(<ConfigProvider locale={zhCN} button={{ autoInsertSpace: false }}><DatePicker aria-label="日期控件基线" showTime showNow={false}
      format="YYYY-MM-DD HH:mm:ss" allowClear onChange={changed} /></ConfigProvider>);
    await choose(user, '日期控件基线', '2026-10-07 08:00:17');
    expect(changed).toHaveBeenCalledTimes(1);
    expect(changed.mock.calls[0][0].format('YYYY-MM-DD HH:mm:ss')).toBe('2026-10-07 08:00:17');
    const input = screen.getByLabelText('日期控件基线'); await user.click(input);
    await user.click(await screen.findByTitle('2026-10-08'));
    await user.click(screen.getByRole('button', { name: '确定' }));
    expect(input).toHaveValue('2026-10-08 08:00:17');
    // jsdom不计算CSS :hover，直接派发点击验证真实清空处理器；不冒充浏览器指针验证。
    await user.hover(input); fireEvent.click(screen.getByRole('button', { name: /^(clear|清除|清空)$/i }));
    expect(input).toHaveValue(''); expect(changed.mock.calls.at(-1)![0]).toBeNull();
  });
  it('TC-310 日历可选择日期，秒级时间可确认，编辑不查询', async () => {
    const user = await mount('?createdFrom=2026-10-07T00%3A00%3A00Z');
    const start = screen.getByLabelText('下单开始时间');
    expect(start).toHaveValue('2026-10-07 08:00:00');
    await user.click(start);
    await user.click(await screen.findByTitle('2026-10-08'));
    await user.click(screen.getByRole('button', { name: '确定' }));
    expect(start).toHaveValue('2026-10-08 08:00:00'); expect(gets()).toHaveLength(1);
    await choose(user, '下单开始时间', '2026-10-08 08:00:17');
    expect(gets()).toHaveLength(1);
    await user.click(screen.getByRole('button', { name: '查询' }));
    await waitFor(() => expect(gets()).toHaveLength(2));
    expect(gets()[1].url.searchParams.get('createdFrom')).toBe('2026-10-08T00:00:17Z');
  });
  it.each(['开始', '结束'])('TC-310 单侧%s条件支持清空后无时间查询', async side => {
    const user = await mount(); const label = `下单${side}时间`;
    await choose(user, label, '2026-10-07 08:00:01');
    await user.click(screen.getByRole('button', { name: '查询' })); await waitFor(() => expect(gets()).toHaveLength(2));
    const field = side === '开始' ? 'createdFrom' : 'createdTo';
    expect(gets()[1].url.searchParams.get(field)).toBe('2026-10-07T00:00:01Z');
    expect(gets()[1].url.searchParams.has(side === '开始' ? 'createdTo' : 'createdFrom')).toBe(false);
    // Ant Design公开的清空按钮，检查实际组件的allowClear交互。
    const input = screen.getByLabelText(label); await user.hover(input);
    fireEvent.click(screen.getByRole('button', { name: /^(clear|清除|清空)$/i }));
    expect(input).toHaveValue(''); await user.click(screen.getByRole('button', { name: '查询' }));
    await waitFor(() => expect(gets()).toHaveLength(3)); expect(gets()[2].url.searchParams.has(field)).toBe(false);
  });
  it('TC-310 倒置范围阻止查询并给开始字段关联错误', async () => {
    const user = await mount();
    await choose(user, '下单开始时间', '2026-10-08 08:00:00');
    await choose(user, '下单结束时间', '2026-10-07 08:00:00');
    await user.click(screen.getByRole('button', { name: '查询' }));
    expect(await screen.findByText(/开始时间.*结束时间|时间范围/)).toBeVisible();
    expect(screen.getByLabelText('下单开始时间')).toHaveAttribute('aria-invalid', 'true');
    expect(screen.getByLabelText('下单开始时间')).toHaveAttribute('aria-describedby');
    expect(gets()).toHaveLength(1);
  });
  it('TC-311 历史POP及重置更新两侧控件，不保留未提交时间', async () => {
    const user = await mount('?createdFrom=2026-10-07T00%3A00%3A00Z&createdTo=2026-10-08T00%3A00%3A01Z');
    await choose(user, '下单开始时间', '2026-10-07 09:00:00');
    await user.click(screen.getByRole('button', { name: '查询' })); await waitFor(() => expect(gets()).toHaveLength(2));
    await act(async () => { await router.navigate(-1); });
    await waitFor(() => expect(screen.getByLabelText('下单开始时间')).toHaveValue('2026-10-07 08:00:00'));
    expect(screen.getByLabelText('下单结束时间')).toHaveValue('2026-10-08 08:00:01');
    await user.click(screen.getByRole('button', { name: '重置' }));
    await waitFor(() => expect(screen.getByLabelText('下单开始时间')).toHaveValue(''));
    expect(screen.getByLabelText('下单结束时间')).toHaveValue('');
    await waitFor(() => expect(gets().at(-1)!.url.searchParams.has('createdFrom')).toBe(false));
  });
  it('TC-311 本机夏令时跳时对应的合法香港时间不被改写，仍按香港转UTC', async () => {
    // 同一用例分别用TZ=UTC和America/New_York启动Vitest；不动态修改运行中Date时区。
    const user = await mount('?createdFrom=2026-03-07T18%3A30%3A00Z');
    const input = screen.getByLabelText('下单开始时间'); expect(input).toHaveValue('2026-03-08 02:30:00');
    await user.click(input); expect(await screen.findByRole('button', { name: '确定' })).toBeVisible();
    await user.click(screen.getByRole('button', { name: '确定' }));
    expect(input).toHaveValue('2026-03-08 02:30:00');
    await user.click(screen.getByRole('button', { name: '查询' })); await waitFor(() => expect(gets()).toHaveLength(2));
    expect(gets()[1].url.searchParams.get('createdFrom')).toBe('2026-03-07T18:30:00Z');
  });
  it('TC-311 本机夏令时跳时仍能输入新的香港墙上时间并提交相同UTC', async () => {
    const user = await mount();
    await choose(user, '下单开始时间', '2026-03-08 02:30:00');
    await user.click(screen.getByRole('button', { name: '查询' }));
    await waitFor(() => expect(gets()).toHaveLength(2));
    expect(gets()[1].url.searchParams.get('createdFrom')).toBe('2026-03-07T18:30:00Z');
  });
});
