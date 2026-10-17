// [规格与证据](../../../../../specs/005-task-resume-and-live-progress/tasks.md)
// [AC-095](../../../../../docs/product/acceptance-criteria.md#ac-095)
// [AC-090](../../../../../docs/product/acceptance-criteria.md#ac-090)
// [AC-091](../../../../../docs/product/acceptance-criteria.md#ac-091)
import { describe, expect, it } from 'vitest';
import { act, screen, waitFor } from '@testing-library/react';
import { http, HttpResponse } from 'msw';
import { server } from '../../../test/handlers';
import { emitTask, installTasks, mountTasks, page, task, TaskEventSource, visibleTask } from './task-test-support';

describe('TC-506 用户继续和空闲进度通道', () => {
  async function confirmResume(user: ReturnType<typeof mountTasks>) {
    await user.click(await screen.findByRole('button', { name: '继续' }));
    await user.click(await screen.findByRole('button', { name: /确.*认|确.*定/ }));
  }
  it('空闲页确认继续后重新开SSE，并合并回退进度、重建提示和最终100%', async () => {
    const paused = { ...task, status: 'PAUSED', controlVersion: '3', allowedActions: ['RESUME'] };
    const resumed = { ...paused, status: 'PENDING', controlVersion: '4', version: '9007199254740994',
      allowedActions: ['PAUSE'], executionPhase: 'CHECKING' };
    installTasks(page([paused], { hasActiveTasks: false }));
    let active = false; let payload: unknown;
    server.use(http.get('*/api/v1/export-tasks', () => HttpResponse.json(
      page([active ? resumed : paused], { hasActiveTasks: active, activityVersion: active ? '12' : '10' }))),
    http.post('*/api/v1/export-tasks/:id/resume', async ({ request }) => {
      payload = await request.json(); active = true;
      return HttpResponse.json({ outcome: 'APPLIED', task: resumed });
    }));
    const user = mountTasks(); await visibleTask();
    expect(TaskEventSource.instances).toHaveLength(0);
    expect(screen.queryByRole('link', { name: '下载' })).not.toBeInTheDocument();
    await confirmResume(user);
    await waitFor(() => expect(TaskEventSource.instances.some(item => item.readyState !== 2)).toBe(true));
    expect(payload).toEqual({ operationId: expect.stringMatching(/^[0-9a-f-]{36}$/), expectedControlVersion: '3' });
    act(() => emitTask({ status: 'RUNNING', version: '9007199254740995', controlVersion: '4',
      processedCount: 5000, progressPercent: 50, repairState: 'REBUILDING', executionPhase: 'REBUILDING' }, '13'));
    expect(await screen.findByText('50%')).toBeVisible();
    expect(screen.getByRole('button', { name: '正在重建损坏文件' })).toBeVisible();
    act(() => emitTask({ status: 'SUCCEEDED', version: '9007199254740996', controlVersion: '4',
      processedCount: 10000, progressPercent: 100, repairState: 'NONE', executionPhase: 'COMPLETE',
      allowedActions: ['DOWNLOAD'], fileSize: '1234', finishedAt: '2026-10-17T01:00:00Z' }, '14'));
    expect(await screen.findByText('100%')).toBeVisible();
    expect(screen.getByRole('link', { name: '下载' })).toBeVisible();
    expect(screen.queryByRole('button', { name: '正在重建损坏文件' })).not.toBeInTheDocument();
  });
  it('继续结果不明后用同一动作、ID和控制版本找回', async () => {
    const paused = { ...task, status: 'PAUSED', controlVersion: '3', allowedActions: ['RESUME'] };
    installTasks(page([paused], { hasActiveTasks: false }));
    const payloads: unknown[] = [];
    server.use(http.post('*/api/v1/export-tasks/:id/resume', async ({ request }) => {
      payloads.push(await request.json());
      if (payloads.length === 1) return HttpResponse.error();
      return HttpResponse.json({ outcome: 'REPLAY', task: { ...paused, status: 'PENDING',
        version: '9007199254740994', controlVersion: '4', allowedActions: ['PAUSE'] } });
    }));
    const user = mountTasks(); await visibleTask(); await confirmResume(user);
    expect(await screen.findByText('继续结果尚未确认，请找回结果')).toBeVisible();
    await user.click(screen.getByRole('button', { name: '找回结果' }));
    await waitFor(() => expect(payloads).toHaveLength(2));
    expect(payloads[1]).toEqual(payloads[0]);
    expect(await screen.findByText('等待中')).toBeVisible();
  });
  it('暂停中即使错误声明RESUME也不开放继续', async () => {
    installTasks(page([{ ...task, status: 'PAUSING', allowedActions: ['RESUME'] }]));
    mountTasks(); await visibleTask();
    expect(screen.queryByRole('button', { name: '继续' })).not.toBeInTheDocument();
  });
});
