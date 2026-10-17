// 本次增量：[计划](../../../../../specs/005-task-resume-and-live-progress/plan.md)。
// 设计与验证：[暂停契约](../../../../../specs/004-task-management/contracts/execution-control.md)。
import { useEffect, useRef, useState } from 'react';
import { pauseExportTask, resumeExportTask } from '../../../shared/api/api';
import { normalizeApiError } from '../../../shared/api/api-error';
import type { PauseRequest, TaskListItem } from '../types/export-task.types';
interface Operation { kind: 'PAUSE' | 'RESUME'; taskId: string; request: PauseRequest; state: 'SUBMITTING' | 'UNKNOWN'; message?: string }
/** 冻结控制请求；网络失败仅表示不明，找回沿用原操作。 */
export function useTaskActions(accept: (task: TaskListItem) => void, onResumed?: () => void) {
  const [operation, setOperation] = useState<Operation>();
  const [error, setError] = useState('');
  const controller = useRef<AbortController | undefined>(undefined);
  const mounted = useRef(true);
  useEffect(() => { mounted.current = true; return () => { mounted.current = false; controller.current?.abort(); }; }, []);
  async function submit(value: Operation) {
    controller.current = new AbortController(); setError(''); setOperation({ ...value, state: 'SUBMITTING' });
    try {
      const send = value.kind === 'RESUME' ? resumeExportTask : pauseExportTask;
      const result = await send(value.taskId, value.request, controller.current.signal);
      if (mounted.current) { accept(result.task); setOperation(undefined); if (value.kind === 'RESUME') onResumed?.(); }
    } catch (cause) {
      if (!mounted.current) return;
      const failure = normalizeApiError(cause).error;
      if (failure.outcome === 'UNKNOWN') setOperation({ ...value, state: 'UNKNOWN', message: `${value.kind === 'RESUME' ? '继续' : '暂停'}结果尚未确认，请找回结果` });
      else { setOperation(undefined); setError(failure.message); }
    }
  }
  return { operation, error,
    pause(task: TaskListItem) { return submit({ kind: 'PAUSE', taskId: task.taskId, request: { operationId: crypto.randomUUID(),
      expectedControlVersion: task.controlVersion }, state: 'SUBMITTING' }); },
    resume(task: TaskListItem) { return submit({ kind: 'RESUME', taskId: task.taskId, request: { operationId: crypto.randomUUID(),
      expectedControlVersion: task.controlVersion }, state: 'SUBMITTING' }); },
    retry() { if (operation?.state === 'UNKNOWN') return submit(operation); } };
}
