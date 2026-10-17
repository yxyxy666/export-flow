// [规格与进度](../../../../../specs/004-task-management/tasks.md#测试进度与证据)
// [AC-090](../../../../../docs/product/acceptance-criteria.md#ac-090)
// [AC-091](../../../../../docs/product/acceptance-criteria.md#ac-091)
import { beforeEach, describe, expect, it } from 'vitest';
import { act, screen, waitFor } from '@testing-library/react';
import { http, HttpResponse } from 'msw';
import { server } from '../../../test/handlers';
import { emitTask, installTasks, mountTasks, page, task, taskRequests, visibleTask } from './task-test-support';

const repaired = { ...task, version: '9007199254740994', processedCount: 6000, progressPercent: 60,
  executionPhase: 'REBUILDING', repairState: 'REBUILDING', repairEpoch: '1', repairAttempt: 1,
  repairReasonCode: 'DATA_BLOCK_CORRUPT' };

describe('TC-416/417 校验进度与重建提示', () => {
  beforeEach(() => installTasks());
  it('接受新版本较低进度，旧高水位事件不能恢复90%，不额外查询', async () => {
    mountTasks(); await visibleTask(); const before = taskRequests.length;
    act(() => emitTask(repaired)); expect(await screen.findByText('60%')).toBeVisible();
    act(() => emitTask({ version: task.version, processedCount: 9000, progressPercent: 90 }));
    expect(screen.getByText('60%')).toBeVisible(); expect(screen.queryByText('90%')).not.toBeInTheDocument();
    expect(taskRequests).toHaveLength(before);
  });
  it('黄色信息图标支持hover和键盘focus，同样提示正在重建损坏文件', async () => {
    installTasks(page([repaired])); const user = mountTasks(); await visibleTask();
    const icon = screen.getByLabelText('正在重建损坏文件');
    await user.hover(icon); expect(await screen.findByRole('tooltip')).toHaveTextContent('正在重建损坏文件');
    await user.unhover(icon); act(() => icon.focus());
    expect(icon).toHaveFocus();
    expect(await screen.findByRole('tooltip')).toHaveTextContent('正在重建损坏文件');
  });
  it('数据块修复后重新装配仍保留提示，不能提前消失', async () => {
    installTasks(page([repaired])); mountTasks(); await visibleTask();
    act(() => emitTask({ ...repaired, version: '9007199254740995', executionPhase: 'ASSEMBLING',
      processedCount: 10000, progressPercent: 99 }));
    expect(await screen.findByText('99%')).toBeVisible();
    expect(screen.getByLabelText('正在重建损坏文件')).toBeVisible();
  });
  it('重建中的再次校验仍显示重建提示，暂停中使用安全停止文案', async () => {
    installTasks(page([{ ...repaired, executionPhase: 'CHECKING' }])); mountTasks(); await visibleTask();
    expect(screen.getByLabelText('正在重建损坏文件')).toBeVisible();
    act(() => emitTask({ ...repaired, version: '9007199254740995', status: 'PAUSING',
      executionPhase: 'CHECKING', repairState: 'PAUSING', allowedActions: [] }));
    expect(await screen.findByLabelText('正在暂停损坏文件重建')).toBeVisible();
  });
  it('暂停时提示重建已暂停，不再显示正在重建', async () => {
    installTasks(page([repaired])); mountTasks(); await visibleTask();
    act(() => emitTask({ ...repaired, version: '9007199254740995', status: 'PAUSED',
      repairState: 'PAUSED', allowedActions: [] }));
    expect(await screen.findByLabelText('损坏文件重建已暂停')).toBeVisible();
    expect(screen.queryByLabelText('正在重建损坏文件')).not.toBeInTheDocument();
  });
  it('成功采用后才显示100%，终态移除重建提示', async () => {
    installTasks(page([repaired])); mountTasks(); await visibleTask();
    act(() => emitTask({ ...repaired, version: '9007199254740995', status: 'SUCCEEDED',
      repairState: 'NONE', executionPhase: 'COMPLETE', processedCount: 10000,
      progressPercent: 100, allowedActions: ['DOWNLOAD'], fileSize: '12345',
      finishedAt: '2026-10-17T00:02:00Z', repairReasonCode: null }));
    expect(await screen.findByText('100%')).toBeVisible();
    expect(screen.queryByLabelText('正在重建损坏文件')).not.toBeInTheDocument();
  });
  it('最终失败不再声称正在重建，不显示物理路径', async () => {
    installTasks(page([{ ...repaired, status: 'FAILED', repairState: 'FAILED',
      executionPhase: 'COMPLETE', errorCode: 'DATA_REPAIR_EXHAUSTED', errorSummary: '文件重建失败，请联系处理',
      finishedAt: '2026-10-17T00:02:00Z', allowedActions: [] }], { hasActiveTasks: false }));
    mountTasks(); await visibleTask();
    expect(screen.getByText('文件重建失败，请联系处理')).toBeVisible();
    expect(screen.queryByLabelText('正在重建损坏文件')).not.toBeInTheDocument();
    expect(document.body.textContent).not.toMatch(/[A-Z]:\\|tasks\/.*blocks|SQLException/);
  });
  it('刷新恢复持久重建状态，旧HTTP不能覆盖更新的低计数', async () => {
    const user = mountTasks(); await visibleTask(); act(() => emitTask(repaired));
    let served = false;
    server.use(http.get('*/api/v1/export-tasks', () => { served = true; return HttpResponse.json(page([task])); }));
    await user.click(screen.getByRole('button', { name: '刷新' }));
    await waitFor(() => expect(served).toBe(true));
    await waitFor(() => expect(screen.getByRole('button', { name: '刷新' })).toBeEnabled());
    expect(screen.getByText('60%')).toBeVisible();
    expect(screen.getByLabelText('正在重建损坏文件')).toBeVisible();
  });
});
