// 本次增量：[计划](../../../../../specs/005-task-resume-and-live-progress/plan.md)。
// 设计与验证：[任务规格](../../../../../specs/004-task-management/function-design.md)。
import { TASK_STATUSES, type TaskListItem, type TaskQuery } from '../types/export-task.types';

export const TASK_STATUS_LABELS = { PENDING: '等待中', RUNNING: '生成中', PAUSING: '暂停中',
  PAUSED: '已暂停', SUCCEEDED: '已完成', FAILED: '失败' };
/** 版本裁决允许可信进度下降，绝不以历史计数取max。 */
export function mergeTaskEvent(current: TaskListItem | undefined, next: TaskListItem): TaskListItem {
  return current && BigInt(current.version) >= BigInt(next.version) ? current : next;
}
/** 未知状态不开放任何操作，服务端动作还需与已知状态交集。 */
export function getTaskActions(task: TaskListItem) {
  return { canPause: ['PENDING', 'RUNNING'].includes(task.status) && task.allowedActions.includes('PAUSE'),
    canResume: task.status === 'PAUSED' && task.allowedActions.includes('RESUME'),
    canDownload: task.status === 'SUCCEEDED' && task.allowedActions.includes('DOWNLOAD'),
    canDelete: task.allowedActions.includes('DELETE'),
    statusLabel: TASK_STATUSES.includes(task.status as typeof TASK_STATUSES[number])
      ? TASK_STATUS_LABELS[task.status as keyof typeof TASK_STATUS_LABELS] : '未知状态',
    unavailableReason: task.status === 'PAUSED' ? '已暂停，文件尚未完成'
      : task.status === 'PAUSING' ? '正在确认安全停止'
      : task.status === 'FAILED' ? '任务失败，暂无可下载文件'
      : task.status === 'SUCCEEDED' ? '文件暂不可下载'
      : ['PENDING', 'RUNNING'].includes(task.status) ? '当前操作不可用' : '状态未知，操作不可用' };
}
/** 成员变化仅用于安排权威查询，不在本地模拟数据库排序规则。 */
export function affectsTaskMembership(previous: TaskListItem | undefined, next: TaskListItem, query: TaskQuery): boolean {
  return !previous || (!!query.status && previous.status !== next.status) || previous.fileName !== next.fileName;
}
