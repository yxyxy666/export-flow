// [规格](../../../../../specs/004-task-management/plan.md)、[缺口](../../../../../docs/validation/004-task-management/coverage-gaps.md)
// [AC-082](../../../../../docs/product/acceptance-criteria.md#ac-082)
// [AC-084](../../../../../docs/product/acceptance-criteria.md#ac-084)
// [AC-086](../../../../../docs/product/acceptance-criteria.md#ac-086)
// [AC-091](../../../../../docs/product/acceptance-criteria.md#ac-091)
import { beforeEach, expect, it } from 'vitest';
import { act, cleanup, screen, waitFor } from '@testing-library/react';
import { http, HttpResponse } from 'msw';
import { server } from '../../../test/handlers';
import { connectionId, installTasks, mountTasks, page, stream, task, taskRequests, visibleTask } from './task-test-support';

beforeEach(() => installTasks());
it.each([['PENDING', '等待中'], ['RUNNING', '生成中'], ['PAUSING', '暂停中'],
  ['PAUSED', '已暂停'], ['SUCCEEDED', '已完成'], ['FAILED', '失败']])('TC-401 六态展示 %s', async (status, label) => {
  installTasks(page([{ ...task, status, allowedActions: [],
    ...(status === 'SUCCEEDED' ? { processedCount: 10000, progressPercent: 100, fileSize: '123', finishedAt: '2026-10-17T00:02:00Z' } : {}),
    ...(status === 'FAILED' ? { errorCode: 'EXPORT_FILE_INVALID', errorSummary: '文件生成失败', finishedAt: '2026-10-17T00:02:00Z' } : {}),
  }]));
  mountTasks(); await visibleTask(); expect(screen.getByText(label)).toBeVisible();
});
it.each([['CHECKING', 'CHECKING', '正在校验文件'], ['REBUILDING', 'PAUSING', '正在暂停损坏文件重建']])(
  'TC-417 显式阶段提示 %s/%s', async (executionPhase, repairState, label) => {
    installTasks(page([{ ...task, executionPhase, repairState, status: repairState === 'PAUSING' ? 'PAUSING' : 'RUNNING' }]));
    mountTasks(); await visibleTask(); expect(screen.getByLabelText(label)).toBeVisible();
  });
it('TC-403 卸载后连接释放，旧连接回调不再发起查询', async () => {
  mountTasks(); await visibleTask(); const old = stream(); const count = taskRequests.length;
  cleanup(); expect(old.readyState).toBe(2);
  act(() => old.emit('stream-ready', { connectionId, activityVersion: '10' }));
  expect(taskRequests).toHaveLength(count);
});
it('TC-401 成员变化导致空页时回退最后有效页', async () => {
  let calls = 0;
  server.use(http.get('*/api/v1/export-tasks', ({ request }) => {
    const url = new URL(request.url); calls++;
    return HttpResponse.json(url.searchParams.get('page') === '3' ? page([], { page: 3, total: '21' }) : page([task], { page: 2, total: '21' }));
  }));
  mountTasks('?page=3'); await visibleTask();
  await waitFor(() => expect(calls).toBe(2)); expect(window.location.search).toContain('page=2');
});
