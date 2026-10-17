// [规格与进度](../../../../../specs/004-task-management/tasks.md#测试进度与证据)
// [AC-012](../../../../../docs/product/acceptance-criteria.md#ac-012)
// [AC-028](../../../../../docs/product/acceptance-criteria.md#ac-028)
// [AC-031](../../../../../docs/product/acceptance-criteria.md#ac-031)
// [AC-039](../../../../../docs/product/acceptance-criteria.md#ac-039)
// [AC-040](../../../../../docs/product/acceptance-criteria.md#ac-040)
// [AC-042](../../../../../docs/product/acceptance-criteria.md#ac-042)
// [AC-043](../../../../../docs/product/acceptance-criteria.md#ac-043)
// [AC-044](../../../../../docs/product/acceptance-criteria.md#ac-044)
// [AC-072](../../../../../docs/product/acceptance-criteria.md#ac-072)
// [AC-082](../../../../../docs/product/acceptance-criteria.md#ac-082)
// [AC-084](../../../../../docs/product/acceptance-criteria.md#ac-084)
import { beforeEach, describe, expect, it, vi } from 'vitest';
import { act, screen, waitFor } from '@testing-library/react';
import { http, HttpResponse } from 'msw';
import { server } from '../../../test/handlers';
import { connectionId, emitTask, installTasks, mountTasks, page, stream, task, TaskEventSource, taskRequests, visibleTask } from './task-test-support';

describe('TC-402/403 SSE事件驱动，无计时间隔断言', () => {
  beforeEach(() => installTasks());
  it('首次快照后只有一条连接，ready桥接一次快照', async () => {
    mountTasks(); await visibleTask();
    expect(stream().url).toContain('/api/v1/export-tasks/events');
    act(() => stream().emit('stream-ready', { connectionId, activityVersion: '10' }));
    await waitFor(() => expect(taskRequests).toHaveLength(2));
    expect(TaskEventSource.instances.filter(item => item.readyState !== 2)).toHaveLength(1);
  });
  it('纯进度事件直接合并，重复和旧版本无效，不追加查询或重排', async () => {
    mountTasks(); await visibleTask(); const requests = taskRequests.length;
    act(() => emitTask({ version: '9007199254740994', progressPercent: 91, processedCount: 9100 }));
    expect(await screen.findByText('91%')).toBeVisible();
    act(() => emitTask({ version: task.version, progressPercent: 20, processedCount: 2000 }));
    expect(screen.getByText('91%')).toBeVisible();
    expect(taskRequests).toHaveLength(requests);
  });
  it('关闭idle后补一次最终快照，不依赖终态通知先到达', async () => {
    mountTasks(); await visibleTask(); const current = stream();
    act(() => current.emit('stream-ready', { connectionId, activityVersion: '10' }));
    await waitFor(() => expect(taskRequests).toHaveLength(2));
    act(() => current.emit('stream-idle', { connectionId, activityVersion: '10' }));
    await waitFor(() => expect(taskRequests).toHaveLength(3));
    expect(current.readyState).toBe(2);
  });
  it('旧活动版本idle不能关闭已见新事件的连接', async () => {
    mountTasks(); await visibleTask(); const current = stream();
    act(() => emitTask({ version: '9007199254740994' }, '11'));
    act(() => current.emit('stream-idle', { connectionId, activityVersion: '10' }));
    expect(current.readyState).not.toBe(2);
  });
  it('ready后其他connectionId的idle不得关闭当前连接', async () => {
    mountTasks(); await visibleTask(); const current = stream();
    act(() => current.emit('stream-ready', { connectionId, activityVersion: '10' }));
    await waitFor(() => expect(taskRequests).toHaveLength(2));
    act(() => current.emit('stream-idle', { connectionId: '33333333-3333-4333-8333-333333333333', activityVersion: '10' }));
    expect(current.readyState).not.toBe(2);
    expect(taskRequests).toHaveLength(2);
  });
  it('HTTP快照活动版本高于idle时保持当前连接', async () => {
    const user = mountTasks(); await visibleTask(); const current = stream();
    act(() => current.emit('stream-ready', { connectionId, activityVersion: '10' }));
    await waitFor(() => expect(taskRequests).toHaveLength(2));
    server.use(http.get('*/api/v1/export-tasks', ({ request }) => {
      taskRequests.push({ method: 'GET', url: new URL(request.url) });
      return HttpResponse.json(page([task], { activityVersion: '12' }));
    }));
    await user.click(screen.getByRole('button', { name: '刷新' }));
    await waitFor(() => expect(taskRequests).toHaveLength(3));
    act(() => current.emit('stream-idle', { connectionId, activityVersion: '11' }));
    expect(current.readyState).not.toBe(2);
  });
  it('协议错误后手动刷新成功会清除错误并重新建立唯一连接', async () => {
    const user = mountTasks(); await visibleTask(); const current = stream();
    act(() => current.emit('task-updated', '{broken-json'));
    await screen.findByText(/协议错误/);
    await user.click(screen.getByRole('button', { name: '刷新' }));
    await waitFor(() => expect(TaskEventSource.instances.filter(item => item.readyState !== 2)).toHaveLength(1));
    expect(screen.queryByText(/协议错误/)).not.toBeInTheDocument();
  });
  it('idle最终快照失败保持关闭，不能用旧活动状态重连', async () => {
    mountTasks(); await visibleTask(); const current = stream();
    act(() => current.emit('stream-ready', { connectionId, activityVersion: '10' }));
    await waitFor(() => expect(taskRequests).toHaveLength(2));
    server.use(http.get('*/api/v1/export-tasks', () => HttpResponse.json({ code: 'STATE_UNAVAILABLE', message: '暂不可用', outcome: 'UNKNOWN' }, { status: 503 })));
    act(() => current.emit('stream-idle', { connectionId, activityVersion: '10' }));
    await screen.findByText('刷新失败，列表尚未更新');
    expect(current.readyState).toBe(2);
    expect(TaskEventSource.instances.filter(item => item.readyState !== 2)).toHaveLength(0);
  });
  it('idle最终快照已无活动时不打开替代连接，释放后旧回调无效', async () => {
    mountTasks(); await visibleTask(); const current = stream();
    act(() => current.emit('stream-ready', { connectionId, activityVersion: '10' }));
    await waitFor(() => expect(taskRequests).toHaveLength(2));
    server.use(http.get('*/api/v1/export-tasks', () => HttpResponse.json(page([{ ...task, status: 'PAUSED', allowedActions: [] }], { hasActiveTasks: false }))));
    act(() => current.emit('stream-idle', { connectionId, activityVersion: '10' }));
    await screen.findByText('已暂停');
    expect(TaskEventSource.instances).toHaveLength(1);
    act(() => current.emit('task-updated', { activityVersion: '20', task: { ...task, version: '9007199254740994', progressPercent: 99 } }));
    expect(screen.queryByText('99%')).not.toBeInTheDocument();
  });
  it('页面隐藏释放连接，恢复可见先查快照', async () => {
    mountTasks(); await visibleTask(); const current = stream();
    const visibility = vi.spyOn(document, 'visibilityState', 'get');
    visibility.mockReturnValue('hidden'); act(() => document.dispatchEvent(new Event('visibilitychange')));
    expect(current.readyState).toBe(2);
    visibility.mockReturnValue('visible'); act(() => document.dispatchEvent(new Event('visibilitychange')));
    await waitFor(() => expect(taskRequests).toHaveLength(2));
  });
  it('全环境没有活跃任务时不开连接，页面仍能手动刷新', async () => {
    installTasks(page([{ ...task, status: 'PAUSED', allowedActions: [] }], { hasActiveTasks: false }));
    const user = mountTasks(); await visibleTask(); expect(TaskEventSource.instances).toHaveLength(0);
    await user.click(screen.getByRole('button', { name: '刷新' }));
    await waitFor(() => expect(taskRequests).toHaveLength(2));
  });
  it('坏JSON关闭当前连接并提示协议错误，不冒充合法更新', async () => {
    mountTasks(); await visibleTask(); const current = stream();
    act(() => current.emit('task-updated', '{broken-json'));
    expect(await screen.findByText(/协议|响应无效/)).toBeVisible();
    expect(current.readyState).toBe(2); expect(screen.getByText('90%')).toBeVisible();
  });
  it('离线关闭连接，联网恢复先查快照', async () => {
    mountTasks(); await visibleTask(); const current = stream();
    const online = vi.spyOn(navigator, 'onLine', 'get');
    online.mockReturnValue(false); act(() => window.dispatchEvent(new Event('offline')));
    expect(current.readyState).toBe(2);
    online.mockReturnValue(true); act(() => window.dispatchEvent(new Event('online')));
    await waitFor(() => expect(taskRequests).toHaveLength(2));
  });
  it('刷新在途时多个成员变化只合并一次补偿，旧HTTP不能复活已筛除任务', async () => {
    const user = mountTasks('?status=RUNNING'); await visibleTask();
    let release!: () => void; const gate = new Promise<void>(resolve => { release = resolve; });
    let queries = 0;
    server.use(http.get('*/api/v1/export-tasks', async () => {
      queries++;
      if (queries === 1) { await gate; return HttpResponse.json(page([task])); }
      return HttpResponse.json(page([], { hasActiveTasks: false }));
    }));
    try {
      await user.click(screen.getByRole('button', { name: '刷新' }));
      await waitFor(() => expect(queries).toBe(1));
      act(() => {
        emitTask({ status: 'PAUSING', version: '9007199254740994', allowedActions: [] });
        emitTask({ status: 'PAUSED', version: '9007199254740995', allowedActions: [] }, '12');
      });
      release();
      await waitFor(() => expect(queries).toBe(2));
      await waitFor(() => expect(screen.queryByText(task.taskNo)).not.toBeInTheDocument());
      expect(queries).toBe(2);
    } finally { release(); }
  });
  it('本标签创建通知唤醒空闲列表，不发送执行请求', async () => {
    installTasks(page([], { hasActiveTasks: false })); mountTasks();
    await waitFor(() => expect(taskRequests).toHaveLength(1));
    // 接口缺失单列覆盖缺口，不提前创建生产通知模块。
    const path = '../../../shared/events/task-created';
    const notices = await import(/* @vite-ignore */ path);
    act(() => notices.notifyTaskCreated({ taskId: task.taskId, taskNo: task.taskNo }));
    await waitFor(() => expect(taskRequests).toHaveLength(2));
    expect(taskRequests.every(request => request.method === 'GET')).toBe(true);
  });
});
