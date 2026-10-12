// 规格与进度：[任务记录](../../../../../specs/002-order-management/tasks.md#测试进度与证据)
import { afterEach, describe, expect, it } from 'vitest';
import { render, screen, waitFor, within } from '@testing-library/react';
import userEvent from '@testing-library/user-event';
import { RouterProvider } from 'react-router-dom';
import { http, HttpResponse } from 'msw';
import { AppProviders } from '../../../app/app-providers';
import { createAppRouter } from '../../../app/router';
import { created, fields, orders, requests, server } from '../../../test/handlers';

// 增量：[规格与进度](../../../../../specs/003-select-all-filtered/tasks.md#测试进度与证据)
// [AC-075](../../../../../docs/product/acceptance-criteria.md#ac-075)
// [AC-077](../../../../../docs/product/acceptance-criteria.md#ac-077)
// [AC-078](../../../../../docs/product/acceptance-criteria.md#ac-078)
// [AC-030](../../../../../docs/product/acceptance-criteria.md#ac-030)
// [AC-051](../../../../../docs/product/acceptance-criteria.md#ac-051)
// [AC-072](../../../../../docs/product/acceptance-criteria.md#ac-072)
describe('003 导出实际选择', () => {
  let router: ReturnType<typeof createAppRouter>;
  afterEach(() => router?.dispose());
  const posts = () => requests.filter(r => r.method === 'POST');
  const checkbox = (name: string) => within(screen.getByText(name).closest('tr')!).getByRole('checkbox');
  async function mount(search = '') {
    window.history.replaceState(null, '', `/orders${search}`); router = createAppRouter();
    render(<AppProviders><RouterProvider router={router} useTransitions={false} /></AppProviders>);
    await screen.findByText('ORD-0001'); return userEvent.setup();
  }
  async function confirm(user: ReturnType<typeof userEvent.setup>, selected = true) {
    await user.click(screen.getByRole('button', { name: selected ? '导出已选' : '导出筛选结果' }));
    const dialog = await screen.findByRole('dialog');
    await user.click(within(dialog).getByRole('button', { name: '确认' }));
    await waitFor(() => expect(posts().length).toBeGreaterThan(0));
  }
  it('TC-306 全选后取消跨页ID，POST含原已提交条件和排除，不含草稿或分页', async () => {
    server.use(http.get('*/api/v1/orders', ({ request }) => {
      const url = new URL(request.url); const page = Number(url.searchParams.get('page') ?? 1);
      requests.push({ method: 'GET', url });
      return HttpResponse.json({ items: page === 1 ? orders : [{ ...orders[0], id: '3', orderNo: 'PAGE2-1' }], total: 42, page, pageSize: 20 });
    }));
    const user = await mount('?orderNo=ORD-0001&sortDirection=ASC');
    await user.click(screen.getByRole('button', { name: '全选筛选结果' })); await user.click(checkbox('ORD-0001'));
    await user.click(screen.getByTitle('2')); await screen.findByText('PAGE2-1'); await user.click(checkbox('PAGE2-1'));
    await user.clear(screen.getByLabelText('订单号')); await user.type(screen.getByLabelText('订单号'), 'UNSUBMITTED');
    await confirm(user);
    const scope = (posts()[0].body as { scope: { type: string; filter: unknown; excludedOrderIds: string[] } }).scope;
    expect(scope.type).toBe('FILTERED'); expect(scope.filter).toEqual({ orderNo: 'ORD-0001' });
    expect(scope.excludedOrderIds).toEqual(expect.arrayContaining(['9007199254740993', '3']));
    expect(scope.excludedOrderIds).toHaveLength(2);
    expect(Object.keys(scope).sort()).toEqual(['excludedOrderIds', 'filter', 'type']);
    expect(requests.filter(r => r.method === 'GET')).toHaveLength(2);
  });
  it('TC-306 逐条选中后全部反选导出补集，再次反选恢复SELECTED范围', async () => {
    const user = await mount(); await user.click(checkbox('ORD-0001'));
    await user.click(screen.getByRole('button', { name: '全部反选' }));
    expect(checkbox('ORD-0001')).not.toBeChecked(); expect(checkbox('ORD-0002')).toBeChecked();
    await user.click(screen.getByRole('button', { name: '全部反选' }));
    expect(checkbox('ORD-0001')).toBeChecked(); expect(checkbox('ORD-0002')).not.toBeChecked();
    await confirm(user);
    expect(posts()[0].body).toEqual({ scope: { type: 'SELECTED', orderIds: ['9007199254740993'] }, fields });
  });
  it('TC-306 全选空排除省略字段，原筛选导出忽略用户取消项', async () => {
    const user = await mount(); await user.click(screen.getByRole('button', { name: '全选筛选结果' }));
    await confirm(user); expect(posts()[0].body).toEqual({ scope: { type: 'FILTERED', filter: {} }, fields });
    await screen.findByText(/导出任务已创建/);
    await user.click(screen.getByRole('button', { name: '全选筛选结果' })); await user.click(checkbox('ORD-0001'));
    await confirm(user, false); await waitFor(() => expect(posts()).toHaveLength(2));
    expect(posts()[1].body).toEqual({ scope: { type: 'FILTERED', filter: {} }, fields });
  });
  it.each(['success', 'rejected', 'unknown'] as const)('TC-308 %s后的取消状态与冻结符合受理结果', async outcome => {
    let calls = 0;
    server.use(http.post('*/api/v1/export-tasks', async ({ request }) => {
      requests.push({ method: 'POST', url: new URL(request.url), body: await request.json(), key: request.headers.get('Idempotency-Key') });
      calls++;
      if (outcome === 'unknown' && calls === 1) return HttpResponse.error();
      if (outcome === 'rejected') return HttpResponse.json({ code: 'EXPORT_LIMIT_EXCEEDED', message: '请缩小范围', traceId: 'test', outcome: 'REJECTED' }, { status: 422 });
      return HttpResponse.json(created, { status: calls === 1 ? 201 : 200 });
    }));
    const user = await mount(); await user.click(screen.getByRole('button', { name: '全选筛选结果' }));
    await user.click(checkbox('ORD-0001')); await confirm(user);
    if (outcome === 'rejected') {
      await screen.findByText('请缩小范围');
      expect(checkbox('ORD-0001')).not.toBeChecked(); expect(checkbox('ORD-0002')).toBeChecked();
      expect(screen.getByLabelText('关键字')).toBeEnabled();
      expect(screen.queryByText(/导出任务已创建/)).not.toBeInTheDocument();
    } else {
      if (outcome === 'unknown') {
        const retry = await screen.findByRole('button', { name: '重试确认' });
        for (const name of ['全选筛选结果', '全部反选', '清空已选', '重置']) expect(screen.getByRole('button', { name })).toBeDisabled();
        expect(checkbox('ORD-0001')).not.toBeChecked(); expect(checkbox('ORD-0002')).toBeChecked();
        expect(screen.getByLabelText('下单开始时间')).toBeDisabled();
        await user.click(retry); await waitFor(() => expect(posts()).toHaveLength(2));
        expect(posts()[1].body).toEqual(posts()[0].body); expect(posts()[1].key).toBe(posts()[0].key);
      }
      await screen.findByText(/导出任务已创建/);
      expect(checkbox('ORD-0001')).not.toBeChecked(); expect(checkbox('ORD-0002')).not.toBeChecked();
      expect(screen.getByRole('button', { name: '导出已选' })).toBeDisabled();
    }
    expect(window.location.pathname).toBe('/orders');
  });
});

// [AC-006](../../../../../docs/product/acceptance-criteria.md#ac-006)
// [AC-007](../../../../../docs/product/acceptance-criteria.md#ac-007)
// [AC-008](../../../../../docs/product/acceptance-criteria.md#ac-008)
// [AC-009](../../../../../docs/product/acceptance-criteria.md#ac-009)
// [AC-019](../../../../../docs/product/acceptance-criteria.md#ac-019)
// [AC-060](../../../../../docs/product/acceptance-criteria.md#ac-060)
// [AC-030](../../../../../docs/product/acceptance-criteria.md#ac-030)
// [AC-051](../../../../../docs/product/acceptance-criteria.md#ac-051)
// [AC-072](../../../../../docs/product/acceptance-criteria.md#ac-072)
describe('订单导出流程', () => {
  let router: ReturnType<typeof createAppRouter> | undefined;
  afterEach(() => router?.dispose());
  function mount(search = '') {
    window.history.replaceState(null, '', `/orders${search}`);
    router = createAppRouter();
    render(<AppProviders><RouterProvider router={router} useTransitions={false} /></AppProviders>);
    return userEvent.setup();
  }
  const posts = () => requests.filter(r => r.method === 'POST');
  async function openFiltered(user: ReturnType<typeof userEvent.setup>) {
    await screen.findByText('ORD-0001');
    await waitFor(() => expect(screen.getByRole('button', { name: '导出筛选结果' })).toBeEnabled());
    await user.click(screen.getByRole('button', { name: '导出筛选结果' }));
    return screen.findByRole('dialog');
  }
  it('TC-208 无选择禁用导出已选，原因直接可见', async () => {
    mount(); await screen.findByText('ORD-0001');
    expect(screen.getByRole('button', { name: '导出已选' })).toBeDisabled();
    expect(screen.getByText(/请.*选择.*订单|未选择.*订单/)).toBeVisible();
  });
  it('TC-208 空结果禁用筛选导出并展示文字原因', async () => {
    server.use(http.get('*/api/v1/orders', () => HttpResponse.json({ items: [], total: 0, page: 1, pageSize: 20 })));
    mount();
    expect(await screen.findByRole('button', { name: '导出筛选结果' })).toBeDisabled();
    expect(await screen.findByText(/没有.*订单|暂无.*订单|结果为空/)).toBeVisible();
  });
  it('TC-208 默认九列选中，不能取消最后一列，取消后恢复发起按钮焦点', async () => {
    const user = mount(); const dialog = await openFiltered(user);
    const boxes = within(dialog).getAllByRole('checkbox');
    expect(boxes).toHaveLength(9);
    boxes.forEach(box => expect(box).toBeChecked());
    for (const box of boxes.slice(1)) await user.click(box);
    await user.click(boxes[0]);
    expect(boxes[0]).toBeChecked();
    await user.click(within(dialog).getByRole('button', { name: '取消' }));
    await waitFor(() => expect(screen.getByRole('button', { name: '导出筛选结果' })).toHaveFocus());
  });
  it('TC-209 已选导出仅提交已选ID和有序字段', async () => {
    const user = mount(); await screen.findByText('ORD-0001');
    const row = screen.getByText('ORD-0001').closest('tr')!;
    await user.click(within(row).getByRole('checkbox'));
    await user.click(screen.getByRole('button', { name: '导出已选' }));
    const dialog = await screen.findByRole('dialog');
    await user.click(within(dialog).getByRole('button', { name: '确认' }));
    await waitFor(() => expect(posts()).toHaveLength(1));
    expect(posts()[0].body).toEqual({ scope: { type: 'SELECTED', orderIds: ['9007199254740993'] }, fields });
    expect(posts()[0].key).toMatch(/^[\x21-\x7e]{16,128}$/);
    expect(await screen.findByText(/导出任务已创建/)).toBeVisible();
    expect(screen.getByRole('button', { name: '导出已选' })).toBeDisabled();
    expect(within(row).getByRole('checkbox')).not.toBeChecked();
  });
  it('TC-209 连续双击确认只提交一次，等待期间冻结查询、重置和字段', async () => {
    let release!: () => void; let started = 0;
    const barrier = new Promise<void>(resolve => { release = resolve; });
    server.use(http.post('*/api/v1/export-tasks', async () => {
      started += 1; await barrier; return HttpResponse.json(created, { status: 201 });
    }));
    try {
      const user = mount(); const dialog = await openFiltered(user);
      await user.dblClick(within(dialog).getByRole('button', { name: '确认' }));
      await waitFor(() => expect(started).toBe(1));
      expect(screen.getByRole('button', { name: '查询' })).toBeDisabled();
      expect(screen.getByRole('button', { name: '重置' })).toBeDisabled();
      within(dialog).getAllByRole('checkbox').forEach(box => expect(box).toBeDisabled());
    } finally { release(); }
  });
  it('TC-209 筛选导出固定已提交条件，不含当前页、排序或未提交草稿', async () => {
    const user = mount('?orderNo=ORD-0001&page=2&pageSize=20&sortDirection=ASC');
    await screen.findByText('ORD-0001');
    await user.clear(screen.getByLabelText('订单号'));
    await user.type(screen.getByLabelText('订单号'), 'UNSUBMITTED');
    const dialog = await openFiltered(user);
    await user.click(within(dialog).getByRole('button', { name: '确认' }));
    await waitFor(() => expect(posts()).toHaveLength(1));
    expect(posts()[0].body).toEqual({ scope: { type: 'FILTERED', filter: { orderNo: 'ORD-0001' } }, fields });
  });
  it.each([201, 200])('TC-210 %s只凭POST提示已创建及编号，保留当前查询并留在订单页', async status => {
    server.use(http.post('*/api/v1/export-tasks', async ({ request }) => {
      requests.push({ method: 'POST', url: new URL(request.url), body: await request.json(),
        key: request.headers.get('Idempotency-Key') });
      return HttpResponse.json(created, { status });
    }));
    const user = mount('?page=2&pageSize=20&sortDirection=ASC');
    const originalSearch = window.location.search;
    const dialog = await openFiltered(user);
    await user.click(within(dialog).getByRole('button', { name: '确认' }));
    expect(await screen.findByText(/导出任务已创建/)).toBeVisible();
    expect(screen.getByText(new RegExp(created.taskNo))).toBeVisible();
    expect(window.location.pathname).toBe('/orders');
    expect(window.location.search).toBe(originalSearch);
    expect(posts()).toHaveLength(1);
    expect(requests.filter(r => r.method === 'GET' && r.url.pathname.includes('export-tasks'))).toEqual([]);
    expect(screen.queryByRole('dialog')).not.toBeInTheDocument();
  });
  it('TC-210 创建后保留内存关键字，不重新查询也不展示文件生成完成', async () => {
    const user = mount(); await screen.findByText('ORD-0001');
    await user.type(screen.getByLabelText('关键字'), '合成客户');
    await user.click(screen.getByRole('button', { name: '查询' }));
    await waitFor(() => expect(requests.filter(r => r.method === 'GET')).toHaveLength(2));
    const dialog = await openFiltered(user);
    await user.click(within(dialog).getByRole('button', { name: '确认' }));
    expect(await screen.findByText(/导出任务已创建/)).toBeVisible();
    expect(screen.getByLabelText('关键字')).toHaveValue('合成客户');
    expect(requests.filter(r => r.method === 'GET')).toHaveLength(2);
    expect(posts()[0].body).toEqual({ scope: { type: 'FILTERED', filter: { keyword: '合成客户' } }, fields });
    expect(screen.queryByText(/文件.*生成完成|导出已完成/)).not.toBeInTheDocument();
    expect(window.location.search).not.toContain('keyword');
  });
  it('TC-210 筛选导出明确拒绝保留字段配置，展示安全错误不宣称成功', async () => {
    server.use(http.post('*/api/v1/export-tasks', () => HttpResponse.json({
      code: 'EXPORT_LIMIT_EXCEEDED', message: '当前42条，单次上限200000条，请缩小范围',
      traceId: 'synthetic-reject', outcome: 'REJECTED', details: { currentCount: 42, limit: 200000 },
    }, { status: 422 })));
    const user = mount(); const dialog = await openFiltered(user);
    await user.click(within(dialog).getByRole('checkbox', { name: '客户姓名' }));
    await user.click(within(dialog).getByRole('button', { name: '确认' }));
    expect(await screen.findByText(/请缩小范围/)).toBeVisible();
    expect(within(screen.getByRole('dialog')).getByRole('checkbox', { name: '客户姓名' })).not.toBeChecked();
    expect(screen.queryByText(/导出任务已创建/)).not.toBeInTheDocument();
    expect(window.location.pathname).toBe('/orders');
  });
  it('TC-210 断连后冻结原载荷及键，同键重试成功共用页面反馈', async () => {
    let calls = 0;
    server.use(http.post('*/api/v1/export-tasks', async ({ request }) => {
      requests.push({ method: 'POST', url: new URL(request.url), body: await request.json(),
        key: request.headers.get('Idempotency-Key') });
      calls += 1;
      return calls === 1 ? HttpResponse.error() : HttpResponse.json(created, { status: 200 });
    }));
    const user = mount(); const dialog = await openFiltered(user);
    await user.click(within(dialog).getByRole('button', { name: '确认' }));
    const retry = await screen.findByRole('button', { name: '重试确认' });
    expect(screen.getByRole('button', { name: '查询' })).toBeDisabled();
    expect(screen.getByRole('button', { name: '重置' })).toBeDisabled();
    expect(calls).toBe(1);
    await user.click(retry);
    expect(await screen.findByText(/导出任务已创建/)).toBeVisible();
    expect(posts()).toHaveLength(2);
    expect(posts()[0].body).toEqual(posts()[1].body);
    expect(posts()[0].key).toBe(posts()[1].key);
    expect(window.location.pathname).toBe('/orders');
  });
});
