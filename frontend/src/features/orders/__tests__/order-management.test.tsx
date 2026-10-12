// 规格与进度：[任务记录](../../../../../specs/002-order-management/tasks.md#测试进度与证据)
import { afterEach, describe, expect, it, vi } from 'vitest';
import { act, render, screen, waitFor, within } from '@testing-library/react';
import userEvent from '@testing-library/user-event';
import { RouterProvider } from 'react-router-dom';
import { http, HttpResponse } from 'msw';
import { createAppRouter } from '../../../app/router';
import { AppProviders } from '../../../app/app-providers';
import { orders, requests, server } from '../../../test/handlers';

// 增量规格：[任务记录](../../../../../specs/003-select-all-filtered/tasks.md#测试进度与证据)
// [AC-073](../../../../../docs/product/acceptance-criteria.md#ac-073)
// [AC-074](../../../../../docs/product/acceptance-criteria.md#ac-074)
// [AC-076](../../../../../docs/product/acceptance-criteria.md#ac-076)
// [AC-077](../../../../../docs/product/acceptance-criteria.md#ac-077)
// [AC-019](../../../../../docs/product/acceptance-criteria.md#ac-019)
describe('003 跨页范围及边界', () => {
  let router: ReturnType<typeof createAppRouter>;
  let view: ReturnType<typeof render>;
  afterEach(() => router?.dispose());
  async function mount(search = '') {
    window.history.replaceState(null, '', `/orders${search}`); router = createAppRouter();
    view = render(<AppProviders><RouterProvider router={router} useTransitions={false} /></AppProviders>);
    await screen.findByText(search.includes('page=2') ? 'PAGE2-1' : 'ORD-0001'); return userEvent.setup();
  }
  const gets = () => requests.filter(r => r.method === 'GET');
  const checkbox = (name: string) => within(screen.getByText(name).closest('tr')!).getByRole('checkbox');
  function pages(total = 42) {
    server.use(http.get('*/api/v1/orders', ({ request }) => {
      const url = new URL(request.url); const page = Number(url.searchParams.get('page') ?? 1);
      requests.push({ method: 'GET', url });
      return HttpResponse.json({ items: page === 1 ? orders : orders.map((order, i) => ({
        ...order, id: String(i + 3), orderNo: `PAGE2-${i + 1}`,
      })), total, page, pageSize: Number(url.searchParams.get('pageSize') ?? 20) });
    }));
  }
  it('TC-302 全选不请求其他页，翻页取消和回页恢复显示正确', async () => {
    pages(); const user = await mount();
    await user.click(screen.getByRole('button', { name: '全选筛选结果' }));
    expect(gets()).toHaveLength(1); expect(requests.filter(r => r.method === 'POST')).toHaveLength(0);
    expect(screen.getByText(/已全选筛选结果.*42/)).toBeVisible();
    expect(checkbox('ORD-0001')).toBeChecked(); expect(checkbox('ORD-0002')).toBeChecked();
    await user.click(checkbox('ORD-0001'));
    await user.click(screen.getByTitle('2')); await screen.findByText('PAGE2-1');
    expect(checkbox('PAGE2-1')).toBeChecked(); expect(checkbox('PAGE2-2')).toBeChecked();
    await user.click(checkbox('PAGE2-1'));
    await user.click(screen.getByTitle('1')); await screen.findByText('ORD-0001');
    expect(checkbox('ORD-0001')).not.toBeChecked(); expect(checkbox('ORD-0002')).toBeChecked();
    await user.click(checkbox('ORD-0001')); expect(checkbox('ORD-0001')).toBeChecked();
    expect(gets()).toHaveLength(3); // 仅用户打开的1、2、1页，没有全量ID请求。
    await user.click(screen.getByTitle('2')); await screen.findByText('PAGE2-1');
    expect(checkbox('PAGE2-1')).not.toBeChecked();
  });
  it('TC-302 全选后表头取消仅当前页，当前页工具不影响其他页', async () => {
    pages(); const user = await mount(); await user.click(screen.getByRole('button', { name: '全选筛选结果' }));
    await user.click(within(screen.getByRole('table')).getAllByRole('checkbox')[0]);
    expect(checkbox('ORD-0001')).not.toBeChecked(); expect(checkbox('ORD-0002')).not.toBeChecked();
    await user.click(screen.getByTitle('2')); await screen.findByText('PAGE2-1');
    expect(checkbox('PAGE2-1')).toBeChecked();
    await user.click(screen.getByRole('button', { name: '反选当前页' }));
    expect(checkbox('PAGE2-1')).not.toBeChecked();
    await user.click(screen.getByRole('button', { name: '全选当前页' }));
    expect(checkbox('PAGE2-1')).toBeChecked();
    await user.click(screen.getByTitle('1')); await screen.findByText('ORD-0001');
    expect(checkbox('ORD-0001')).not.toBeChecked();
  });
  it.each(['pageSize', 'sort', 'draft'] as const)('TC-305 %s保持全量范围及单行例外', async operation => {
    pages(); const user = await mount(); await user.click(screen.getByRole('button', { name: '全选筛选结果' }));
    await user.click(checkbox('ORD-0001'));
    if (operation === 'pageSize') {
      await user.click(screen.getByRole('combobox', { name: /每页|条数/ }));
      await user.click(await screen.findByRole('option', { name: /50/ }));
    } else if (operation === 'sort') await user.click(screen.getByRole('columnheader', { name: /下单时间/ }));
    else await user.type(screen.getByLabelText('关键字'), '未提交');
    if (operation !== 'draft') await waitFor(() => expect(gets()).toHaveLength(2));
    expect(checkbox('ORD-0001')).not.toBeChecked(); expect(checkbox('ORD-0002')).toBeChecked();
    expect(screen.getByRole('button', { name: '导出已选' })).toBeEnabled();
    expect(gets()).toHaveLength(operation === 'draft' ? 1 : 2);
  });
  it.each(['query', 'reset', 'POP', 'remount', 'clear'] as const)('TC-305 %s清除全部页选择', async operation => {
    pages(); const user = await mount('?page=2'); await user.click(screen.getByRole('button', { name: '全选筛选结果' }));
    if (operation === 'query') {
      await user.type(screen.getByLabelText('关键字'), '新条件'); await user.click(screen.getByRole('button', { name: '查询' }));
    } else if (operation === 'POP') {
      await user.click(screen.getByTitle('1')); await screen.findByText('ORD-0001');
      await act(async () => { await router.navigate(-1); }); await screen.findByText('PAGE2-1');
    } else if (operation === 'remount') {
      view.unmount(); router.dispose(); await mount();
    } else await user.click(screen.getByRole('button', { name: operation === 'reset' ? '重置' : '清空已选' }));
    await waitFor(() => expect(screen.getByRole('button', { name: '导出已选' })).toBeDisabled());
    within(screen.getByRole('table')).getAllByRole('checkbox').forEach(box => expect(box).not.toBeChecked());
  });
  it('TC-305 超过导出上限仍可全选，排除至上限后允许导出', async () => {
    pages(200001); const user = await mount();
    await user.click(screen.getByRole('button', { name: '全选筛选结果' }));
    expect(screen.getByRole('button', { name: '导出已选' })).toBeDisabled();
    expect(screen.getByText(/已全选筛选结果.*200.?001/)).toBeVisible();
    expect(screen.getAllByText(/200.?000.*缩小|超过200.?000/).length).toBeGreaterThan(0);
    await user.click(checkbox('ORD-0001'));
    expect(screen.getByRole('button', { name: '导出已选' })).toBeEnabled();
    expect(gets()).toHaveLength(1);
  });
  it('TC-305 取消全部匹配行禁用空导出，但仍能清空或全局反选', async () => {
    pages(2); const user = await mount(); await user.click(screen.getByRole('button', { name: '全选筛选结果' }));
    await user.click(within(screen.getByRole('table')).getAllByRole('checkbox')[0]);
    expect(screen.getByRole('button', { name: '导出已选' })).toBeDisabled();
    expect(screen.getByRole('button', { name: '清空已选' })).toBeEnabled();
    await user.click(screen.getByRole('button', { name: '全部反选' }));
    expect(checkbox('ORD-0001')).toBeChecked(); expect(checkbox('ORD-0002')).toBeChecked();
    await user.click(screen.getByRole('button', { name: '清空已选' }));
    expect(screen.getByRole('button', { name: '导出已选' })).toBeDisabled();
  });
  it.each(['loading', 'error', 'empty'] as const)('TC-305 %s禁用全选和全部反选并有文字原因', async scenario => {
    let release = () => {};
    const barrier = new Promise<void>(resolve => { release = resolve; });
    server.use(http.get('*/api/v1/orders', async () => {
      if (scenario === 'loading') await barrier;
      return scenario === 'error' ? HttpResponse.json({ code: 'DEPENDENCY_UNAVAILABLE', message: '请重试', traceId: 'test' }, { status: 503 })
        : HttpResponse.json({ items: [], total: 0, page: 1, pageSize: 20 });
    }));
    try {
      router = createAppRouter(); view = render(<AppProviders><RouterProvider router={router} useTransitions={false} /></AppProviders>);
      if (scenario !== 'loading') await screen.findByText(scenario === 'error' ? /查询失败/ : /没有匹配订单/);
      expect(screen.getByRole('button', { name: '全选筛选结果' })).toBeDisabled();
      expect(screen.getByRole('button', { name: '全部反选' })).toBeDisabled();
      expect(screen.getAllByText(/查询中|失效|没有匹配订单|请重试/).length).toBeGreaterThan(0);
    } finally { release(); }
  });
  it('TC-305 同条件重查失效期间不导出，成功后更新全量参考数', async () => {
    pages(42); const user = await mount(); await user.click(screen.getByRole('button', { name: '全选筛选结果' }));
    let release = () => {};
    const barrier = new Promise<void>(resolve => { release = resolve; });
    server.use(http.get('*/api/v1/orders', async ({ request }) => {
      requests.push({ method: 'GET', url: new URL(request.url) }); await barrier;
      return HttpResponse.json({ items: orders, total: 43, page: 1, pageSize: 20 });
    }));
    try {
      await user.click(screen.getByRole('button', { name: '查询' })); await waitFor(() => expect(gets()).toHaveLength(2));
      for (const name of ['全选筛选结果', '全部反选', '导出已选']) expect(screen.getByRole('button', { name })).toBeDisabled();
      release(); await screen.findByText(/已全选筛选结果.*43/);
      expect(checkbox('ORD-0001')).toBeChecked();
    } finally { release(); }
  });
});

// 规格：specs/002-order-management/tasks.md；断言只经过真实页面和网络边界。
describe('订单管理页面', () => {
  let router: ReturnType<typeof createAppRouter> | undefined;
  afterEach(() => router?.dispose());
  function mount(search = '') {
    window.history.replaceState(null, '', `/orders${search}`);
    router = createAppRouter();
    render(<AppProviders><RouterProvider router={router} useTransitions={false} /></AppProviders>);
    return userEvent.setup();
  }
  async function loaded() { await screen.findByText('ORD-0001'); }
  const queryRequests = () => requests.filter(r => r.url.pathname === '/api/v1/orders');

  // [AC-001](../../../../../docs/product/acceptance-criteria.md#ac-001)
  it('TC-201 初次进入立即请求无筛选第一页20条并展示四个区域', async () => {
    mount();
    await waitFor(() => expect(queryRequests()).toHaveLength(1));
    const params = queryRequests()[0].url.searchParams;
    expect(params.get('page') ?? '1').toBe('1');
    expect(params.get('pageSize') ?? '20').toBe('20');
    expect(params.has('keyword')).toBe(false);
    expect(screen.getByRole('button', { name: '查询' })).toBeVisible();
    expect(screen.getByRole('button', { name: '导出筛选结果' })).toBeVisible();
    expect(screen.getByRole('table')).toBeVisible();
    await loaded();
  });
  // [AC-002](../../../../../docs/product/acceptance-criteria.md#ac-002)
  // [AC-035](../../../../../docs/product/acceptance-criteria.md#ac-035)
  it('TC-202 编辑草稿不请求，查询时同时提交订单号和关键字并回第一页', async () => {
    const user = mount('?page=2'); await loaded();
    await user.type(screen.getByLabelText('订单号'), 'ORD-0001');
    await user.type(screen.getByLabelText('关键字'), '测试客户');
    expect(queryRequests()).toHaveLength(1);
    await user.click(screen.getByRole('button', { name: '查询' }));
    await waitFor(() => expect(queryRequests()).toHaveLength(2));
    const params = queryRequests()[1].url.searchParams;
    expect(params.get('orderNo')).toBe('ORD-0001');
    expect(params.get('keyword')).toBe('测试客户');
    expect(params.get('page') ?? '1').toBe('1');
    expect(window.location.search).not.toContain('keyword');
  });
  // [AC-003](../../../../../docs/product/acceptance-criteria.md#ac-003)
  it('TC-202 重置立即清筛选、选择和分页，重新请求无筛选第一页20条', async () => {
    const user = mount('?orderNo=ORD-0001&page=2&pageSize=50'); await loaded();
    const row = screen.getByText('ORD-0001').closest('tr')!;
    await user.click(row.querySelector('input[type="checkbox"]')!);
    await user.click(screen.getByRole('button', { name: '重置' }));
    await waitFor(() => expect(queryRequests()).toHaveLength(2));
    const params = queryRequests()[1].url.searchParams;
    expect(params.has('orderNo')).toBe(false);
    expect(params.get('page') ?? '1').toBe('1');
    expect(params.get('pageSize') ?? '20').toBe('20');
    expect(screen.getByRole('button', { name: '导出已选' })).toBeDisabled();
  });
  // [AC-004](../../../../../docs/product/acceptance-criteria.md#ac-004)
  it.each([20, 50, 100])('TC-203 URL指定每页%s条时按服务端分页请求', async pageSize => {
    mount(`?page=2&pageSize=${pageSize}`);
    await waitFor(() => expect(queryRequests()).toHaveLength(1));
    expect(queryRequests()[0].url.searchParams.get('page')).toBe('2');
    expect(queryRequests()[0].url.searchParams.get('pageSize') ?? '20').toBe(String(pageSize));
  });
  it('TC-203 修改每页数量回到第一页并只请求一次新范围', async () => {
    const user = mount('?page=2'); await loaded();
    await user.click(screen.getByRole('combobox', { name: /每页|条数/ }));
    await user.click(await screen.findByRole('option', { name: /50/ }));
    await waitFor(() => expect(queryRequests()).toHaveLength(2));
    const params = queryRequests()[1].url.searchParams;
    expect(params.get('page') ?? '1').toBe('1'); expect(params.get('pageSize')).toBe('50');
  });
  // [AC-034](../../../../../docs/product/acceptance-criteria.md#ac-034)
  it('TC-203 切换下单时间排序回第一页，保留同筛选选择', async () => {
    const user = mount('?page=2'); await loaded();
    const row = screen.getByText('ORD-0001').closest('tr')!;
    await user.click(row.querySelector('input[type="checkbox"]')!);
    await user.click(screen.getByRole('columnheader', { name: /下单时间/ }));
    await waitFor(() => expect(queryRequests()).toHaveLength(2));
    const params = queryRequests()[1].url.searchParams;
    expect(params.get('page') ?? '1').toBe('1');
    expect(params.get('sortDirection')).toBe('ASC');
    expect(screen.getByRole('button', { name: '导出已选' })).toBeEnabled();
  });
  // [AC-020](../../../../../docs/product/acceptance-criteria.md#ac-020)
  // [AC-033](../../../../../docs/product/acceptance-criteria.md#ac-033)
  it('TC-207 查询新筛选清选择，修改未提交草稿则保留选择', async () => {
    const user = mount(); await loaded();
    const row = screen.getByText('ORD-0001').closest('tr')!;
    await user.click(row.querySelector('input[type="checkbox"]')!);
    await user.type(screen.getByLabelText('关键字'), '新条件');
    expect(screen.getByRole('button', { name: '导出已选' })).toBeEnabled();
    await user.click(screen.getByRole('button', { name: '查询' }));
    await waitFor(() => expect(queryRequests()).toHaveLength(2));
    expect(screen.getByRole('button', { name: '导出已选' })).toBeDisabled();
  });
  it('TC-207 翻页保留选择，全选和反选只改变当前页，清空才移除所有页', async () => {
    server.use(http.get('*/api/v1/orders', ({ request }) => {
      const page = Number(new URL(request.url).searchParams.get('page') ?? 1);
      requests.push({ method: 'GET', url: new URL(request.url) });
      return HttpResponse.json({ items: page === 1 ? orders : orders.map((order, index) => ({
        ...order, id: String(index + 3), orderNo: `SECOND-PAGE-${index + 1}`,
      })), total: 42, page, pageSize: 20 });
    }));
    const user = mount(); await loaded();
    await user.click(within(screen.getByText('ORD-0001').closest('tr')!).getByRole('checkbox'));
    await user.click(screen.getByTitle('2'));
    await screen.findByText('SECOND-PAGE-1');
    await user.click(screen.getByRole('button', { name: '全选当前页' }));
    expect(screen.getByText(/已选\s*3\s*(条|个)/)).toBeVisible();
    await user.click(screen.getByRole('button', { name: '反选当前页' }));
    expect(screen.getByText(/已选\s*1\s*(条|个)/)).toBeVisible();
    await user.click(screen.getByTitle('1'));
    await loaded();
    expect(within(screen.getByText('ORD-0001').closest('tr')!).getByRole('checkbox')).toBeChecked();
    await user.click(screen.getByRole('button', { name: '清空已选' }));
    expect(screen.getByRole('button', { name: '导出已选' })).toBeDisabled();
    expect(within(screen.getByText('ORD-0001').closest('tr')!).getByRole('checkbox')).not.toBeChecked();
  });
  // [AC-065](../../../../../docs/product/acceptance-criteria.md#ac-065)
  it('TC-206 浏览器后退恢复安全条件并清内存关键字，不写长期存储', async () => {
    const user = mount('?orderNo=ORD-0001'); await loaded();
    await user.clear(screen.getByLabelText('订单号'));
    await user.type(screen.getByLabelText('关键字'), '合成客户');
    await user.click(screen.getByRole('button', { name: '查询' }));
    await waitFor(() => expect(queryRequests()).toHaveLength(2));
    await act(async () => { await router!.navigate(-1); });
    await waitFor(() => expect(queryRequests().at(-1)!.url.searchParams.get('orderNo')).toBe('ORD-0001'));
    expect(queryRequests().at(-1)!.url.searchParams.has('keyword')).toBe(false);
    expect(screen.getByLabelText('关键字')).toHaveValue('');
    expect(window.location.search).not.toContain('合成客户');
    expect(localStorage.length).toBe(0); expect(sessionStorage.length).toBe(0);
  });
  // [AC-005](../../../../../docs/product/acceptance-criteria.md#ac-005)
  it('TC-205 最小金额大于最大金额时阻止请求并关联字段错误', async () => {
    const user = mount(); await loaded();
    const min = screen.getByLabelText('最小金额');
    await user.type(min, '20');
    await user.type(screen.getByLabelText('最大金额'), '10');
    await user.click(screen.getByRole('button', { name: '查询' }));
    expect(queryRequests()).toHaveLength(1);
    expect(await screen.findByText(/最小金额.*最大金额|金额范围/)).toBeVisible();
    expect(min).toHaveAttribute('aria-invalid', 'true');
  });
  it('TC-205 开始时间晚于结束时间时保留旧结果并阻止请求', async () => {
    const user = mount(); await loaded();
    // [AC-080](../../../../../docs/product/acceptance-criteria.md#ac-080)
    // 增量日期交互：[测试进度](../../../../../specs/003-select-all-filtered/tasks.md#测试进度与证据)
    for (const [label, value] of [['下单开始时间', '2026-10-08 08:00:00'], ['下单结束时间', '2026-10-07 08:00:00']]) {
      await user.click(screen.getByLabelText(label)); await user.paste(value);
      await user.click(await screen.findByRole('button', { name: '确定' }));
    }
    await user.click(screen.getByRole('button', { name: '查询' }));
    expect(queryRequests()).toHaveLength(1);
    expect(await screen.findByText(/开始时间.*结束时间|时间范围/)).toBeVisible();
    expect(screen.getByLabelText(/下单开始时间|开始时间/)).toHaveAttribute('aria-invalid', 'true');
    expect(screen.getByText('ORD-0001')).toBeVisible();
  });
  // [AC-065](../../../../../docs/product/acceptance-criteria.md#ac-065)
  it('TC-206 敏感URL关键字主动移除，非法分页恢复默认且不请求非法参数', async () => {
    mount('?keyword=synthetic-private&page=-1&pageSize=30');
    await waitFor(() => expect(queryRequests()).toHaveLength(1));
    expect(window.location.search).not.toContain('keyword');
    const params = queryRequests()[0].url.searchParams;
    expect(params.has('keyword')).toBe(false); expect(params.get('page') ?? '1').toBe('1');
    expect(params.get('pageSize') ?? '20').toBe('20');
    expect(await screen.findByText(/关键字需重新输入/)).toBeVisible();
  });
  it('TC-211 加载中禁止把尚未收到的结果作为筛选导出范围', async () => {
    let release!: () => void;
    const barrier = new Promise<void>(resolve => { release = resolve; });
    server.use(http.get('*/api/v1/orders', async () => {
      await barrier; return HttpResponse.json({ items: orders, total: 42, page: 1, pageSize: 20 });
    }));
    try {
      mount();
      expect(await screen.findByRole('button', { name: '导出筛选结果' })).toBeDisabled();
      expect(screen.getByText(/加载|查询中/)).toBeVisible();
    } finally { release(); }
  });
  it('TC-211 查询错误显示安全信息，手动重试可以恢复', async () => {
    server.use(http.get('*/api/v1/orders', () => HttpResponse.json({
      code: 'DEPENDENCY_UNAVAILABLE', message: '暂时无法查询，请重试', traceId: 'test-query',
    }, { status: 503 })));
    const user = mount();
    expect(await screen.findByText(/暂时无法查询|查询失败/)).toBeVisible();
    server.resetHandlers();
    await user.click(screen.getByRole('button', { name: /重试|刷新/ }));
    await loaded();
  });
  it('TC-211 新查询加载时不能用上一次total启用筛选导出', async () => {
    let release!: () => void; let started!: () => void;
    const pending = new Promise<void>(resolve => { release = resolve; });
    const entered = new Promise<void>(resolve => { started = resolve; });
    server.use(http.get('*/api/v1/orders', async ({ request }) => {
      if (new URL(request.url).searchParams.has('keyword')) { started(); await pending; }
      return HttpResponse.json({ items: orders, total: 42, page: 1, pageSize: 20 });
    }));
    try {
      const user = mount(); await loaded();
      await user.type(screen.getByLabelText('关键字'), '新范围');
      await user.click(screen.getByRole('button', { name: '查询' }));
      await entered;
      expect(screen.getByRole('button', { name: '导出筛选结果' })).toBeDisabled();
    } finally { release(); }
  });
  it('TC-211 离开订单页面中止未完成查询，迟到响应不能污染静态任务页', async () => {
    const transport = vi.spyOn(globalThis, 'fetch');
    let release!: () => void; let requestSignal: AbortSignal | undefined;
    const pending = new Promise<void>(resolve => { release = resolve; });
    server.use(http.get('*/api/v1/orders', async ({ request }) => {
      requestSignal = request.signal; await pending;
      return HttpResponse.json({ items: orders, total: 42, page: 1, pageSize: 20 });
    }));
    try {
      mount(); await waitFor(() => expect(requestSignal).toBeDefined());
      await act(async () => { await router!.navigate('/export-tasks'); });
      // 检查实际传输signal；MSW3 Node服务端Request由socket另行创建。
      await waitFor(() => expect(transport.mock.calls[0][1]?.signal?.aborted).toBe(true));
      release();
      expect(window.location.pathname).toBe('/export-tasks');
      expect(screen.queryByText('ORD-0001')).not.toBeInTheDocument();
    } finally { release(); }
  });
  it('TC-211 后发查询先返回时，迟到的旧响应不能覆盖新结果', async () => {
    let release!: () => void;
    const barrier = new Promise<void>(resolve => { release = resolve; });
    server.use(http.get('*/api/v1/orders', async ({ request }) => {
      const keyword = new URL(request.url).searchParams.get('keyword');
      if (keyword === '旧') await barrier;
      return HttpResponse.json({ items: [{ ...orders[0], orderNo: keyword === '新' ? 'NEW-ORDER' : 'ORD-0001' }],
        total: 1, page: 1, pageSize: 20 });
    }));
    try {
      const user = mount(); await loaded();
      const input = screen.getByLabelText('关键字');
      await user.type(input, '旧'); await user.click(screen.getByRole('button', { name: '查询' }));
      await user.clear(input); await user.type(input, '新');
      await user.click(screen.getByRole('button', { name: '查询' }));
      expect(await screen.findByText('NEW-ORDER')).toBeVisible();
      release();
      await waitFor(() => expect(screen.queryByText('ORD-0001')).not.toBeInTheDocument());
    } finally { release(); }
  });
});
