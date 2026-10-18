import { describe, expect, it } from 'vitest';
import { getTaskActions } from '../models/task-list-model';
import type { TaskListItem } from '../types/export-task.types';

function task(status: TaskListItem['status']): TaskListItem {
  return {
    taskId: '00000000-0000-0000-0000-000000000001', taskNo: 'TASK-1',
    scopeType: 'SELECTED', totalCount: 1, processedCount: status === 'SUCCEEDED' ? 1 : 0,
    progressPercent: status === 'SUCCEEDED' ? 100 : 0, status, fields: ['orderNo'],
    fileName: 'file.xlsx', fileSize: status === 'SUCCEEDED' ? '10' : null,
    createdAt: '2026-10-18T00:00:00Z', finishedAt: status === 'SUCCEEDED' ? '2026-10-18T00:01:00Z' : null,
    errorCode: null, errorSummary: null, version: '1', controlVersion: '0',
    allowedActions: ['DELETE'], executionPhase: 'NOT_STARTED', repairState: 'NONE',
    repairEpoch: '0', repairAttempt: 0, repairReasonCode: null,
  } as TaskListItem;
}

describe('任务统一删除动作（AC-096、AC-101）', () => {
  it('服务端允许删除时，所有任务状态共用同一个删除动作', () => {
    for (const status of ['PENDING', 'RUNNING', 'PAUSING', 'PAUSED', 'SUCCEEDED', 'FAILED']) {
      expect(getTaskActions(task(status)).canDelete).toBe(true);
    }
  });
});
