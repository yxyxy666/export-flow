// 测试专用夹具：[计划](../../../../../specs/004-task-management/plan.md)、[进度](../../../../../specs/004-task-management/tasks.md)。
import { afterEach, vi } from 'vitest';
import { render, screen } from '@testing-library/react';
import userEvent from '@testing-library/user-event';
import { RouterProvider } from 'react-router-dom';
import { http, HttpResponse } from 'msw';
import { createAppRouter } from '../../../app/router';
import { AppProviders } from '../../../app/app-providers';
import { server } from '../../../test/handlers';

export const taskId = '11111111-1111-4111-8111-111111111111';
export const connectionId = '22222222-2222-4222-8222-222222222222';
export const task = {
  taskId, taskNo: 'EXP_004_001', scopeType: 'FILTERED', totalCount: 10000,
  processedCount: 9000, progressPercent: 90, status: 'RUNNING', fields: ['orderNo'],
  fileName: '订单导出_20261017-080000_EXP_004_001.xlsx', fileSize: null,
  createdAt: '2026-10-17T00:00:00Z', finishedAt: null, errorCode: null, errorSummary: null,
  version: '9007199254740993', controlVersion: '2', allowedActions: ['PAUSE'],
  executionPhase: 'COLLECTING', repairState: 'NONE', repairEpoch: '0',
  repairAttempt: 0, repairReasonCode: null,
};
export type TestTask = Omit<typeof task, 'fileSize' | 'finishedAt' | 'errorCode' | 'errorSummary' | 'repairReasonCode'> & {
  fileSize: string | null; finishedAt: string | null; errorCode: string | null;
  errorSummary: string | null; repairReasonCode: string | null;
};
export function page(items: TestTask[] = [{ ...task }], extra: Record<string, unknown> = {}) {
  return { items, page: 1, pageSize: 20, total: String(items.length),
    activityVersion: '10', hasActiveTasks: true, ...extra };
}
export const taskRequests: { method: string; url: URL; body?: unknown }[] = [];
let router: ReturnType<typeof createAppRouter> | undefined;

/** 仅替换浏览器传输；不实现生产的连接决策、版本合并或恢复算法。 */
export class TaskEventSource extends EventTarget {
  static instances: TaskEventSource[] = [];
  static CONNECTING = 0; static OPEN = 1; static CLOSED = 2;
  readyState = 1; withCredentials = false;
  onopen: ((event: Event) => void) | null = null;
  onerror: ((event: Event) => void) | null = null;
  onmessage: ((event: MessageEvent) => void) | null = null;
  readonly url: string;
  constructor(url: string | URL) {
    super(); this.url = String(url); TaskEventSource.instances.push(this);
  }
  close() { this.readyState = 2; }
  emit(type: string, data: unknown) {
    this.dispatchEvent(new MessageEvent(type, { data: typeof data === 'string' ? data : JSON.stringify(data) }));
  }
}

export function installTasks(snapshot = page()) {
  taskRequests.length = 0; TaskEventSource.instances.length = 0;
  vi.stubGlobal('EventSource', TaskEventSource);
  server.use(http.get('*/api/v1/export-tasks', ({ request }) => {
    taskRequests.push({ method: 'GET', url: new URL(request.url) });
    return HttpResponse.json(snapshot);
  }));
}
export function mountTasks(search = '') {
  window.history.replaceState(null, '', `/export-tasks${search}`);
  router = createAppRouter();
  render(<AppProviders><RouterProvider router={router} useTransitions={false} /></AppProviders>);
  return userEvent.setup();
}
export async function visibleTask() { return screen.findByText(task.taskNo); }
export function stream() {
  const active = TaskEventSource.instances.filter(item => item.readyState !== 2);
  if (active.length !== 1) throw new Error(`预期唯一SSE连接，实际${active.length}`);
  return active[0];
}
export function emitTask(update: Partial<TestTask>, activityVersion = '11') {
  stream().emit('task-updated', { activityVersion, task: { ...task, ...update } });
}
afterEach(() => { router?.dispose(); router = undefined; vi.unstubAllGlobals(); });
