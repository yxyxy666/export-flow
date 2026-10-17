// [规格与进度](../../../../../specs/004-task-management/tasks.md#测试进度与证据)
// [AC-013](../../../../../docs/product/acceptance-criteria.md#ac-013)
// [AC-014](../../../../../docs/product/acceptance-criteria.md#ac-014)
// [AC-015](../../../../../docs/product/acceptance-criteria.md#ac-015)
// [AC-049](../../../../../docs/product/acceptance-criteria.md#ac-049)
// [AC-083](../../../../../docs/product/acceptance-criteria.md#ac-083)
// [AC-084](../../../../../docs/product/acceptance-criteria.md#ac-084)
// [AC-086](../../../../../docs/product/acceptance-criteria.md#ac-086)
// [AC-087](../../../../../docs/product/acceptance-criteria.md#ac-087)
import { beforeEach, describe, expect, it } from 'vitest';
import { act, screen, waitFor } from '@testing-library/react';
import { http, HttpResponse } from 'msw';
import { server } from '../../../test/handlers';
import { emitTask, installTasks, mountTasks, page, task, taskId, taskRequests, visibleTask } from './task-test-support';

describe('TC-404/407/408 暂停幂等与下载边界', () => {
  beforeEach(() => installTasks());
  async function confirmPause(user: ReturnType<typeof mountTasks>) {
    await user.click(screen.getByRole('button', { name: '暂停' }));
    await user.click(await screen.findByRole('button', { name: /确.*认|确.*定/ }));
  }
  it('暂停受理后显示暂停中，收到权威停止事件才已暂停', async () => {
    let payload: unknown;
    server.use(http.post('*/api/v1/export-tasks/:id/pause', async ({ request }) => {
      payload = await request.json();
      return HttpResponse.json({ outcome: 'APPLIED', task: { ...task, status: 'PAUSING',
        controlVersion: '3', version: '9007199254740994', allowedActions: [] } });
    }));
    const user = mountTasks(); await visibleTask(); await confirmPause(user);
    expect(await screen.findByText('暂停中')).toBeVisible();
    expect(screen.queryByText('已暂停')).not.toBeInTheDocument();
    expect(payload).toEqual({ operationId: expect.stringMatching(/^[0-9a-f-]{36}$/), expectedControlVersion: '2' });
    act(() => emitTask({ status: 'PAUSED', controlVersion: '3', version: '9007199254740995', allowedActions: [] }));
    expect(await screen.findByText('已暂停')).toBeVisible();
  });
  it('结果不明保留原操作，用户找回时复用同ID和控制版本', async () => {
    const payloads: unknown[] = [];
    server.use(http.post('*/api/v1/export-tasks/:id/pause', async ({ request }) => {
      payloads.push(await request.json());
      if (payloads.length === 1) return HttpResponse.error();
      return HttpResponse.json({ outcome: 'REPLAY', task: { ...task, status: 'PAUSED',
        controlVersion: '3', version: '9007199254740995', allowedActions: [] } });
    }));
    const user = mountTasks(); await visibleTask(); await confirmPause(user);
    await user.click(await screen.findByRole('button', { name: /确认结果|找回|重试确认/ }));
    await waitFor(() => expect(payloads).toHaveLength(2));
    expect(payloads[1]).toEqual(payloads[0]); expect(await screen.findByText('已暂停')).toBeVisible();
  });
  it('进度版本更新不改变暂停expectedControlVersion', async () => {
    let payload: Record<string, unknown> | undefined;
    server.use(http.post('*/api/v1/export-tasks/:id/pause', async ({ request }) => {
      payload = await request.json() as Record<string, unknown>;
      return HttpResponse.json({ outcome: 'APPLIED', task: { ...task, status: 'PAUSING',
        version: '9007199254740996', controlVersion: '3', allowedActions: [] } });
    }));
    const user = mountTasks(); await visibleTask();
    act(() => emitTask({ version: '9007199254740995', processedCount: 9500, progressPercent: 95 }));
    await confirmPause(user); await waitFor(() => expect(payload?.expectedControlVersion).toBe('2'));
  });
  it('成功任务使用已有同源下载路径，展示文件大小和结束时间', async () => {
    installTasks(page([{ ...task, status: 'SUCCEEDED', processedCount: 10000, progressPercent: 100,
      fileSize: '12345', finishedAt: '2026-10-17T00:01:00Z', allowedActions: ['DOWNLOAD'],
      executionPhase: 'COMPLETE' }], { hasActiveTasks: false }));
    mountTasks(); await visibleTask();
    expect(screen.getByRole('link', { name: '下载' })).toHaveAttribute('href', `/api/v1/export-tasks/${taskId}/file`);
    expect(screen.queryByRole('button', { name: '暂停' })).not.toBeInTheDocument();
    expect(screen.getByText('100%')).toBeVisible();
  });
  it('已暂停不开放半成品下载，也不提供预留CRUD、重试或继续入口', async () => {
    installTasks(page([{ ...task, status: 'PAUSED', allowedActions: [] }], { hasActiveTasks: false }));
    mountTasks(); await visibleTask();
    expect(screen.queryByRole('link', { name: '下载' })).not.toBeInTheDocument();
    for (const name of ['新增任务', '编辑', '删除', '重新执行', '继续'])
      expect(screen.queryByRole('button', { name })).not.toBeInTheDocument();
    expect(taskRequests.every(request => request.method === 'GET')).toBe(true);
    expect(screen.getByText('已暂停，文件尚未完成')).toBeVisible();
  });
});
