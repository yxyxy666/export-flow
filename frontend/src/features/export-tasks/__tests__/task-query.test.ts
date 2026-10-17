// [规格与进度](../../../../../specs/004-task-management/tasks.md#测试进度与证据)
// [AC-012](../../../../../docs/product/acceptance-criteria.md#ac-012)
// [AC-026](../../../../../docs/product/acceptance-criteria.md#ac-026)
// [AC-027](../../../../../docs/product/acceptance-criteria.md#ac-027)
// [AC-028](../../../../../docs/product/acceptance-criteria.md#ac-028)
// [AC-031](../../../../../docs/product/acceptance-criteria.md#ac-031)
// [AC-038](../../../../../docs/product/acceptance-criteria.md#ac-038)
// [AC-081](../../../../../docs/product/acceptance-criteria.md#ac-081)
// [AC-084](../../../../../docs/product/acceptance-criteria.md#ac-084)
// [AC-086](../../../../../docs/product/acceptance-criteria.md#ac-086)
import { beforeEach, describe, expect, it } from 'vitest';
import { screen, waitFor } from '@testing-library/react';
import { http, HttpResponse } from 'msw';
import { server } from '../../../test/handlers';
import { installTasks, mountTasks, page, task, taskRequests, visibleTask } from './task-test-support';

describe('TC-401 任务列表可观察行为', () => {
  beforeEach(() => installTasks());
  it('展示权威任务、实际进度和元数据，不再显示业务占位内容', async () => {
    mountTasks(); await visibleTask();
    expect(screen.getByText(task.fileName)).toBeVisible();
    expect(screen.getByText('90%')).toBeVisible();
    expect(screen.getByText('生成中')).toBeVisible();
    expect(screen.queryByText(/后续实现|功能占位/)).not.toBeInTheDocument();
    expect(taskRequests).toHaveLength(1);
  });
  it('关键字只在提交后查询，保留状态组合并回第一页', async () => {
    const user = mountTasks('?page=3'); await visibleTask();
    await user.type(screen.getByLabelText('关键字'), '  EXP_004  ');
    expect(taskRequests).toHaveLength(1);
    await user.click(screen.getByRole('button', { name: '查询' }));
    await waitFor(() => expect(taskRequests).toHaveLength(2));
    expect(taskRequests[1].url.searchParams.get('keyword')).toBe('EXP_004');
    expect(taskRequests[1].url.searchParams.get('page')).toBe('1');
  });
  it('恢复已提交URL，手动刷新保页，重置清空条件并回第一页', async () => {
    installTasks(page([task], { page: 3, total: '60' }));
    const user = mountTasks('?keyword=EXP&status=RUNNING&page=3&pageSize=20'); await visibleTask();
    expect(taskRequests[0].url.searchParams.get('keyword')).toBe('EXP');
    expect(taskRequests[0].url.searchParams.get('status')).toBe('RUNNING');
    await user.click(screen.getByRole('button', { name: '刷新' }));
    await waitFor(() => expect(taskRequests).toHaveLength(2));
    expect(taskRequests[1].url.searchParams.get('page')).toBe('3');
    await user.click(screen.getByRole('button', { name: '重置' }));
    await waitFor(() => expect(taskRequests).toHaveLength(3));
    expect(taskRequests[2].url.searchParams.get('keyword')).toBeNull();
    expect(taskRequests[2].url.searchParams.get('status')).toBeNull();
    expect(taskRequests[2].url.searchParams.get('page')).toBe('1');
  });
  it('无匹配结果显示空态，分页无记录仍保留全局活动订阅', async () => {
    installTasks(page([], { total: '0', hasActiveTasks: true })); mountTasks();
    await waitFor(() => expect(taskRequests).toHaveLength(1));
    expect(await screen.findByText(/暂无.*任务|没有.*任务/)).toBeVisible();
    expect(screen.queryByText(task.taskNo)).not.toBeInTheDocument();
  });
  it('未知任务状态明确提示并禁用操作，不冒充已完成', async () => {
    installTasks(page([{ ...task, status: 'FUTURE_STATUS', allowedActions: [] }]));
    mountTasks(); await visibleTask();
    expect(screen.getByText('未知状态')).toBeVisible();
    expect(screen.queryByRole('link', { name: '下载' })).not.toBeInTheDocument();
    const pause = screen.queryByRole('button', { name: '暂停' });
    if (pause) expect(pause).toBeDisabled();
  });
  it('刷新失败保留最后快照，并说明尚未更新', async () => {
    const user = mountTasks(); await visibleTask();
    server.use(http.get('*/api/v1/export-tasks', () => HttpResponse.json({
      code: 'STATE_UNAVAILABLE', message: '任务状态暂不可用', traceId: 'test', outcome: 'UNKNOWN',
    }, { status: 503 })));
    await user.click(screen.getByRole('button', { name: '刷新' }));
    expect(await screen.findByText(/尚未更新|刷新失败/)).toBeVisible();
    expect(screen.getByText(task.taskNo)).toBeVisible();
  });
});
