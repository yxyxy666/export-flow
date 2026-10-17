// 规格与进度：[任务记录](../../../../../specs/002-order-management/tasks.md#测试进度与证据)
import { afterEach, describe, expect, it, vi } from 'vitest';
import { act, isInaccessible, render, screen, waitFor, within } from '@testing-library/react';
import userEvent from '@testing-library/user-event';
import { RouterProvider } from 'react-router-dom';
import { http, HttpResponse } from 'msw';
import { AppProviders } from '../../../app/app-providers';
import { createAppRouter } from '../../../app/router';
import { created, orders, requests, server } from '../../../test/handlers';

// [AC-002](../../../../../docs/product/acceptance-criteria.md#ac-002)
// [AC-005](../../../../../docs/product/acceptance-criteria.md#ac-005)
// [AC-006](../../../../../docs/product/acceptance-criteria.md#ac-006)
// [AC-030](../../../../../docs/product/acceptance-criteria.md#ac-030)
// [AC-060](../../../../../docs/product/acceptance-criteria.md#ac-060)
// [AC-065](../../../../../docs/product/acceptance-criteria.md#ac-065)
// [AC-072](../../../../../docs/product/acceptance-criteria.md#ac-072)
describe('TC-202/205/206/208/209/210/211 页面边界补充', () => {
  let router: ReturnType<typeof createAppRouter>;
  let view: ReturnType<typeof render>;
  afterEach(() => { router?.dispose(); vi.restoreAllMocks(); vi.useRealTimers(); });
  function mount(search = '') {
    window.history.replaceState(null, '', `/orders${search}`); router = createAppRouter();
    view = render(<AppProviders><RouterProvider router={router} useTransitions={false} /></AppProviders>); return userEvent.setup();
  }
  const gets = () => requests.filter(r => r.method === 'GET');
  const posts = () => requests.filter(r => r.method === 'POST');
  const row = () => within(screen.getByText('ORD-0001').closest('tr')!);
  async function loaded() { await screen.findByText('ORD-0001'); }
  async function open(user: ReturnType<typeof userEvent.setup>, selected = false) {
    await user.click(screen.getByRole('button', { name: selected ? '导出已选' : '导出筛选结果' }));
    return screen.findByRole('dialog');
  }
  it('四区域包含分页、总数与九个业务列', async () => {
    mount(); await loaded();
    for (const name of ['订单号','客户姓名','客户手机号','状态','渠道','金额','币种','地区','下单时间'])
      expect(screen.getByRole('columnheader', { name: new RegExp(name) })).toBeVisible();
    expect(screen.getByRole('button', { name: '查询' })).toBeVisible();
    expect(screen.getByRole('button', { name: '导出已选' })).toBeVisible();
    expect(screen.getByTitle('2')).toBeVisible(); expect(screen.getByText(/共\s*42\s*条/)).toBeVisible();
  });
  it('组合表单提交枚举、香港时间与金额，只发送一次UTC请求', async () => {
    const user = mount(); await loaded();
    await user.click(screen.getByRole('combobox', { name: '状态' })); await user.click(await screen.findByRole('option', { name: '已支付' }));
    await user.click(screen.getByRole('combobox', { name: '渠道' })); await user.click(await screen.findByRole('option', { name: 'Web 商城' }));
    // [AC-080](../../../../../docs/product/acceptance-criteria.md#ac-080)
    // 日期控件增量：[测试进度](../../../../../specs/003-select-all-filtered/tasks.md#测试进度与证据)
    for (const [label, value] of [['下单开始时间', '2026-10-07 08:00:00'], ['下单结束时间', '2026-10-07 08:00:01']]) {
      await user.click(screen.getByLabelText(label)); await user.paste(value);
      // 组合用例只定位当前可见确认文字，避免对密集时间列表重复计算所有节点的可访问名称。
      // 控件角色及可访问性由TC-310独立验证；API和字段断言保持原样。
      const confirm = screen.getAllByText('确定').find(element => !isInaccessible(element));
      expect(confirm).toBeDefined(); await user.click(confirm!);
    }
    await user.type(screen.getByLabelText('最小金额'), '2'); await user.type(screen.getByLabelText('最大金额'), '10');
    // 日历弹层通过portal保留在body，查询按钮只在表单内查找，避免扫描两份密集时间列表。
    const form = screen.getByLabelText('最小金额').closest('form')!;
    await user.click(within(form).getByRole('button', { name: '查询' })); await waitFor(() => expect(gets()).toHaveLength(2));
    const p = gets()[1].url.searchParams;
    expect(Object.fromEntries(p)).toMatchObject({ status: 'PAID', channel: 'WEB', createdFrom: '2026-10-07T00:00:00Z',
      createdTo: '2026-10-07T00:00:01Z', minAmount: '2.00', maxAmount: '10.00' });
  // 本例同时操作两个真实AntD日期/秒级时间弹层；独立预算只用于jsdom组件执行，不改变断言或等待策略。
  }, 45_000);
  it('范围校验失败同时保留URL、旧结果和选择', async () => {
    const user = mount('?page=2'); await loaded(); await user.click(row().getByRole('checkbox'));
    const search = window.location.search;
    await user.type(screen.getByLabelText('最小金额'), '20'); await user.type(screen.getByLabelText('最大金额'), '10');
    await user.click(screen.getByRole('button', { name: '查询' }));
    expect(gets()).toHaveLength(1); expect(window.location.search).toBe(search);
    expect(row().getByRole('checkbox')).toBeChecked(); expect(screen.getByText('ORD-0001')).toBeVisible();
  });
  it.each(['page', 'sort'])('页面自身%s变更保留内存关键字', async operation => {
    const user = mount(); await loaded(); await user.type(screen.getByLabelText('关键字'), '合成客户');
    await user.click(screen.getByRole('button', { name: '查询' })); await waitFor(() => expect(gets()).toHaveLength(2));
    await user.click(operation === 'page' ? screen.getByTitle('2') : screen.getByRole('columnheader', { name: /下单时间/ }));
    await waitFor(() => expect(gets()).toHaveLength(3));
    expect(gets()[2].url.searchParams.get('keyword')).toBe('合成客户');
    expect(screen.getByLabelText('关键字')).toHaveValue('合成客户'); expect(window.location.search).not.toContain('keyword');
  });
  it('后退和前进清选择及未提交配置，不恢复内存关键字', async () => {
    const user = mount('?orderNo=ORD-0001'); await loaded();
    await user.clear(screen.getByLabelText('订单号')); await user.type(screen.getByLabelText('关键字'), '合成客户');
    await user.click(screen.getByRole('button', { name: '查询' })); await waitFor(() => expect(gets()).toHaveLength(2));
    await waitFor(() => expect(row().getByRole('checkbox')).toBeEnabled());
    await user.click(row().getByRole('checkbox')); const dialog = await open(user, true);
    await user.click(within(dialog).getByRole('checkbox', { name: '金额' }));
    await act(async () => { await router.navigate(-1); });
    await waitFor(() => expect(screen.getByRole('textbox', { name: '订单号' })).toHaveValue('ORD-0001'));
    expect(screen.queryByRole('dialog')).not.toBeInTheDocument(); expect(row().getByRole('checkbox')).not.toBeChecked();
    expect(screen.getByText(/关键字需重新输入/)).toBeVisible();
    await act(async () => { await router.navigate(1); });
    expect(screen.getByLabelText('关键字')).toHaveValue(''); expect(row().getByRole('checkbox')).not.toBeChecked();
  });
  it('刷新重建不恢复内存关键字或选择', async () => {
    const user = mount(); await loaded();
    await user.type(screen.getByLabelText('关键字'), '刷新前关键字'); await user.click(screen.getByRole('button', { name: '查询' }));
    await waitFor(() => expect(row().getByRole('checkbox')).toBeEnabled());
    await user.click(row().getByRole('checkbox')); const search = window.location.search;
    view.unmount(); router.dispose(); mount(search); await loaded();
    expect(screen.getByLabelText('关键字')).toHaveValue(''); expect(row().getByRole('checkbox')).not.toBeChecked();
  });
  it('混合URL保留合法项、替换非法项并显示规范化提示', async () => {
    mount('?orderNo=ORD-0001&page=-1&status=ALIEN&pageSize=50'); await loaded();
    expect(gets()[0].url.searchParams.get('orderNo')).toBe('ORD-0001');
    expect(gets()[0].url.searchParams.get('pageSize')).toBe('50');
    expect(window.location.search).not.toContain('ALIEN'); expect(window.location.search).not.toContain('page=-1');
    expect(screen.getByText(/无效|非法|已重置|已恢复/)).toBeVisible();
  });
  it('跨页选取后POST包含两页全部ID并按页面列序发送字段', async () => {
    server.use(http.get('*/api/v1/orders', ({ request }) => {
      const page = Number(new URL(request.url).searchParams.get('page') ?? 1);
      return HttpResponse.json({ items: page === 1 ? orders : [{ ...orders[0], id: '3', orderNo: 'PAGE-TWO' }], total: 42, page, pageSize: 20 });
    }));
    const user = mount(); await loaded(); await user.click(row().getByRole('checkbox'));
    await user.click(screen.getByTitle('2')); await screen.findByText('PAGE-TWO');
    await waitFor(() => expect(within(screen.getByText('PAGE-TWO').closest('tr')!).getByRole('checkbox')).toBeEnabled());
    await user.click(within(screen.getByText('PAGE-TWO').closest('tr')!).getByRole('checkbox'));
    const dialog = await open(user, true);
    await user.click(within(dialog).getByRole('button', { name: '确认' })); await waitFor(() => expect(posts()).toHaveLength(1));
    expect(posts()[0].body).toEqual({ scope: { type: 'SELECTED', orderIds: expect.arrayContaining(['3', '9007199254740993']) },
      fields: ['orderNo','customerName','customerPhone','status','channel','amount','currency','region','createdAt'] });
    expect((posts()[0].body as { scope: { orderIds: string[] } }).scope.orderIds).toHaveLength(2);
  });
  it('字段子集POST仍按页面列序发送而不按点击顺序排列', async () => {
    const user = mount(); await loaded(); const dialog = await open(user);
    for (const name of ['客户姓名','金额','金额'])
      await user.click(within(dialog).getByRole('checkbox', { name }));
    await user.click(within(dialog).getByRole('button', { name: '确认' }));
    await waitFor(() => expect(posts()).toHaveLength(1));
    expect((posts()[0].body as { fields: string[] }).fields)
      .toEqual(['orderNo','customerPhone','status','channel','amount','currency','region','createdAt']);
  });
  it('取消后全新配置恢复九列默认值并支持键盘打开与焦点回归', async () => {
    const user = mount(); await loaded(); const button = screen.getByRole('button', { name: '导出筛选结果' });
    button.focus(); await user.keyboard('{Enter}'); const dialog = await screen.findByRole('dialog');
    await waitFor(() => expect(dialog.contains(document.activeElement)).toBe(true));
    await user.click(within(dialog).getByRole('checkbox', { name: '金额' }));
    await user.click(within(dialog).getByRole('button', { name: '取消' })); await waitFor(() => expect(button).toHaveFocus());
    await user.keyboard('{Enter}'); (await within(await screen.findByRole('dialog')).findAllByRole('checkbox')).forEach(box => expect(box).toBeChecked());
  });
  it('SELECTED明确拒绝保留选择，重开保留字段，纠正后使用新键', async () => {
    server.use(http.post('*/api/v1/export-tasks', async ({ request }) => {
      requests.push({ method: 'POST', url: new URL(request.url), body: await request.json(), key: request.headers.get('Idempotency-Key') });
      return HttpResponse.json({ code: 'EXPORT_LIMIT_EXCEEDED', message: '请缩小范围', traceId: 'test', outcome: 'REJECTED' }, { status: 422 });
    }));
    const user = mount(); await loaded(); await user.click(row().getByRole('checkbox'));
    let dialog = await open(user, true); await user.click(within(dialog).getByRole('checkbox', { name: '金额' }));
    await user.click(within(dialog).getByRole('button', { name: '确认' })); await screen.findByText('请缩小范围');
    expect(row().getByRole('checkbox')).toBeChecked();
    await user.click(within(dialog).getByRole('button', { name: '取消' })); dialog = await open(user, true);
    expect(within(dialog).getByRole('checkbox', { name: '金额' })).not.toBeChecked();
    await user.click(within(dialog).getByRole('checkbox', { name: '金额' })); await user.click(within(dialog).getByRole('button', { name: '确认' }));
    await waitFor(() => expect(posts()).toHaveLength(2)); expect(posts()[1].key).not.toBe(posts()[0].key);
  });
  it.each(['SUBMITTING', 'UNKNOWN'])('%s冻结分页、排序、选择工具和筛选输入', async state => {
    let release!: () => void; const barrier = new Promise<void>(resolve => { release = resolve; });
    server.use(http.post('*/api/v1/export-tasks', async () => { if (state === 'UNKNOWN') return HttpResponse.error(); await barrier; return HttpResponse.json(created, { status: 201 }); }));
    try {
      const user = mount(); await loaded(); await user.click(row().getByRole('checkbox')); const dialog = await open(user, true);
      await user.click(within(dialog).getByRole('button', { name: '确认' }));
      if (state === 'UNKNOWN') await screen.findByRole('button', { name: '重试确认' });
      const before = gets().length;
      for (const label of ['订单号','关键字','最小金额','最大金额']) expect(screen.getByRole('textbox', { name: label })).toBeDisabled();
      expect(screen.getByLabelText(/下单开始时间|开始时间/)).toBeDisabled(); expect(screen.getByLabelText(/下单结束时间|结束时间/)).toBeDisabled();
      for (const name of ['状态','渠道']) expect(screen.getByRole('combobox', { name })).toBeDisabled();
      expect(row().getByRole('checkbox')).toBeDisabled();
      for (const name of ['全选当前页','反选当前页','清空已选']) expect(screen.getByRole('button', { name })).toBeDisabled();
      await user.click(screen.getByTitle('2')); await user.click(screen.getByRole('columnheader', { name: /下单时间/ }));
      expect(gets()).toHaveLength(before); expect(row().getByRole('checkbox')).toBeChecked();
      within(dialog).getAllByRole('checkbox').forEach(box => expect(box).toBeDisabled());
    } finally { release(); }
  });
  it('成功恢复可编辑状态、焦点且无延迟轮询或SSE', async () => {
    const eventSource = vi.fn(); vi.stubGlobal('EventSource', eventSource);
    try {
      const user = mount(); await loaded(); const dialog = await open(user);
      await user.click(within(dialog).getByRole('button', { name: '确认' })); await screen.findByText(/导出任务已创建/);
      expect(screen.getByLabelText('关键字')).toBeEnabled(); expect(screen.getByRole('button', { name: '查询' })).toBeEnabled();
      expect(screen.getByRole('button', { name: '重置' })).toBeEnabled();
      await waitFor(() => expect(screen.getByRole('button', { name: '导出筛选结果' })).toHaveFocus());
      vi.useFakeTimers(); await act(async () => { await vi.advanceTimersByTimeAsync(60000); });
      expect(gets()).toHaveLength(1); expect(eventSource).not.toHaveBeenCalled();
    } finally { vi.unstubAllGlobals(); }
  });
  it('关键字和载荷不进入console日志', async () => {
    const spies = [vi.spyOn(console, 'log'), vi.spyOn(console, 'info'), vi.spyOn(console, 'warn'), vi.spyOn(console, 'error')];
    const user = mount(); await loaded(); await user.type(screen.getByLabelText('关键字'), 'synthetic-private-customer');
    await user.click(screen.getByRole('button', { name: '查询' })); const dialog = await open(user);
    await user.click(within(dialog).getByRole('button', { name: '确认' })); await screen.findByText(/导出任务已创建/);
    const log = JSON.stringify(spies.flatMap(spy => spy.mock.calls));
    expect(log).not.toContain('synthetic-private-customer'); expect(log).not.toContain(posts()[0].key!);
    expect(localStorage.length).toBe(0); expect(sessionStorage.length).toBe(0);
  });
  it.each(['keyword','page','pageSize','sort','POP'])('TC-211 %s变化取消在途旧请求，迟到结果不污染新键', async operation => {
    // 观察浏览器传输边界的真实signal；MSW3 Node从复用socket重建的服务端Request不代表客户端signal。
    const transport = vi.spyOn(globalThis, 'fetch');
    let release!: () => void; const pending = new Promise<void>(resolve => { release = resolve; });
    const seen: { url: URL; signal: AbortSignal }[] = [];
    server.use(http.get('*/api/v1/orders', async ({ request }) => {
      seen.push({ url: new URL(request.url), signal: request.signal });
      const sequence = seen.length;
      if (sequence === 2) await pending;
      const url = new URL(request.url);
      return HttpResponse.json({ items: [{ ...orders[0], orderNo: sequence === 2 ? 'STALE-RESULT' : sequence === 1 ? 'ORD-0001' : 'LATEST-RESULT' }],
        total: 42, page: Number(url.searchParams.get('page') ?? 1), pageSize: Number(url.searchParams.get('pageSize') ?? 20) });
    }));
    try {
      const user = mount(); await loaded();
      const input = screen.getByLabelText('关键字'); await user.type(input, '旧查询');
      await user.click(screen.getByRole('button', { name: '查询' })); await waitFor(() => expect(seen).toHaveLength(2));
      if (operation === 'keyword') { await user.clear(input); await user.type(input, '新查询'); await user.click(screen.getByRole('button', { name: '查询' })); }
      else if (operation === 'page') await user.click(screen.getByTitle('2'));
      else if (operation === 'pageSize') { await user.click(screen.getByRole('combobox', { name: /每页|条数/ })); await user.click(await screen.findByRole('option', { name: /50/ })); }
      else if (operation === 'sort') await user.click(screen.getByRole('columnheader', { name: /下单时间/ }));
      else await act(async () => { await router.navigate(-1); });
      await waitFor(() => expect(transport.mock.calls[1][1]?.signal?.aborted).toBe(true));
      await waitFor(() => expect(seen).toHaveLength(3));
      const next = seen[2].url.searchParams;
      if (operation === 'keyword') expect(next.get('keyword')).toBe('新查询');
      if (operation === 'page') expect(next.get('page')).toBe('2');
      if (operation === 'pageSize') expect(next.get('pageSize')).toBe('50');
      if (operation === 'sort') expect(next.get('sortDirection')).toBe('ASC');
      if (operation === 'POP') expect(next.has('keyword')).toBe(false);
      await screen.findByText('LATEST-RESULT'); release();
      await act(async () => { await Promise.resolve(); });
      expect(screen.queryByText('STALE-RESULT')).not.toBeInTheDocument();
    } finally { release(); }
  });
  it('UNKNOWN离页明确告知结果未确认且离页不会撤销受理任务', async () => {
    // 任务页现已查询权威列表；本例只授权目标页的一次只读快照，不放宽其他额外请求。
    server.use(http.get('*/api/v1/export-tasks', ({ request }) => {
      requests.push({ method: 'GET', url: new URL(request.url) });
      return HttpResponse.json({ items: [], page: 1, pageSize: 20, total: '0', activityVersion: '0', hasActiveTasks: false });
    }));
    server.use(http.post('*/api/v1/export-tasks', () => HttpResponse.error()));
    const user = mount(); await loaded(); const dialog = await open(user);
    await user.click(within(dialog).getByRole('button', { name: '确认' }));
    await screen.findByRole('button', { name: '重试确认' });
    expect(screen.getByText(/结果.*(未确认|尚未确认)/)).toBeVisible();
    expect(screen.getByText(/离开.*(不.*撤销|不.*取消)|重新进入.*核对/)).toBeVisible();
    await act(async () => { await router.navigate('/export-tasks'); });
    expect(window.location.pathname).toBe('/export-tasks');
    await waitFor(() => expect(requests.filter(request => request.url.pathname === '/api/v1/export-tasks' && request.method === 'GET')).toHaveLength(1));
    expect(screen.queryByText(/任务已取消|后台已取消|已撤销/)).not.toBeInTheDocument();
  });

  it('TC-208 失效结果直接显示禁用原因且不能继续导出旧total', async () => {
    const user = mount(); await loaded();
    server.use(http.get('*/api/v1/orders', () => HttpResponse.json({ code: 'DEPENDENCY_UNAVAILABLE', message: '暂时无法查询，请重试', traceId: 'synthetic' }, { status: 503 })));
    await user.type(screen.getByLabelText('关键字'), '新范围');await user.click(screen.getByRole('button', { name: '查询' }));
    await screen.findByText(/暂时无法查询|查询失败/);
    expect(screen.getByRole('button', { name: '导出筛选结果' })).toBeDisabled();
    expect(screen.getByText(/结果.*失效|请先.*(查询|重试)|查询失败.*导出/)).toBeVisible();
  });
  // [AC-020](../../../../../docs/product/acceptance-criteria.md#ac-020)
  // [AC-033](../../../../../docs/product/acceptance-criteria.md#ac-033)
  it('TC-203/207 新范围查询未完成时旧行仅展示，禁止选择并在有效结果后恢复', async () => {
    const user = mount(); await loaded(); await user.click(row().getByRole('checkbox'));
    let release!: () => void;
    const barrier = new Promise<void>(resolve => { release = resolve; });
    let requested = false;
    server.use(http.get('*/api/v1/orders', async ({ request }) => {
      requested = true; await barrier;
      const params = new URL(request.url).searchParams;
      return HttpResponse.json({ items: [{ ...orders[0], id: '3', orderNo: 'NEW-RANGE' }],
        total: 1, page: Number(params.get('page') ?? 1), pageSize: Number(params.get('pageSize') ?? 20) });
    }));
    try {
      await user.type(screen.getByLabelText('关键字'), '新范围');
      await user.click(screen.getByRole('button', { name: '查询' }));
      await waitFor(() => expect(requested).toBe(true));
      expect(screen.getByText('ORD-0001')).toBeVisible();
      expect(row().getByRole('checkbox')).not.toBeChecked();
      screen.getAllByRole('checkbox').forEach(box => expect(box).toBeDisabled());
      for (const name of ['全选当前页', '反选当前页', '导出已选'])
        expect(screen.getByRole('button', { name })).toBeDisabled();
      expect(screen.getByText('已选 0 条')).toBeVisible();
      release(); await screen.findByText('NEW-RANGE');
      const checkbox = within(screen.getByText('NEW-RANGE').closest('tr')!).getByRole('checkbox');
      await waitFor(() => expect(checkbox).toBeEnabled());
      await user.click(screen.getByRole('button', { name: '全选当前页' }));
      expect(checkbox).toBeChecked(); expect(screen.getByText('已选 1 条')).toBeVisible();
    } finally { release(); }
  });
  it.each(['non-json','bad-json','rejected','unknown'])('TC-210 %s响应经过真实客户端和页面分类', async scenario => {
    server.use(http.post('*/api/v1/export-tasks', async ({ request }) => {
      requests.push({ method: 'POST', url: new URL(request.url), body: await request.json(), key: request.headers.get('Idempotency-Key') });
      if (scenario === 'non-json') return new HttpResponse('synthetic bad proxy', { headers: { 'Content-Type': 'text/html' } });
      if (scenario === 'bad-json') return new HttpResponse('{', { headers: { 'Content-Type': 'application/json' } });
      return HttpResponse.json({ code: scenario === 'rejected' ? 'EXPORT_LIMIT_EXCEEDED' : 'CREATE_OUTCOME_UNKNOWN',
        message: scenario === 'rejected' ? '请缩小范围' : '结果尚未确认', traceId: 'synthetic', outcome: scenario === 'rejected' ? 'REJECTED' : 'UNKNOWN' }, { status: scenario === 'rejected' ? 422 : 503 });
    }));
    const user = mount();await loaded();const dialog = await open(user);await user.click(within(dialog).getByRole('button', { name: '确认' }));
    if (scenario === 'rejected') { await screen.findByText('请缩小范围'); expect(screen.getByLabelText('关键字')).toBeEnabled(); }
    else { const retry = await screen.findByRole('button', { name: '重试确认' }); expect(screen.getByLabelText('关键字')).toBeDisabled();
      await user.click(retry);await waitFor(() => expect(posts()).toHaveLength(2));expect(posts()[1].key).toBe(posts()[0].key);expect(posts()[1].body).toEqual(posts()[0].body); }
    expect(screen.queryByText(/导出任务已创建/)).not.toBeInTheDocument();
  });

});
