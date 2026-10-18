import { useEffect, useRef, useState } from 'react';
import { deleteExportTask } from '../../../shared/api/api';
import { normalizeApiError } from '../../../shared/api/api-error';
import type { DeleteTaskRequest, TaskListItem } from '../types/export-task.types';

interface DeleteOperation {
  taskId: string;
  request: DeleteTaskRequest;
  state: 'SUBMITTING' | 'UNKNOWN';
  message?: string;
}

/** 统一删除提交；结果不明时保留原操作ID和版本，不自动生成新请求。 */
export function useTaskDeletion(onAccepted?: () => void) {
  const [operation, setOperation] = useState<DeleteOperation>();
  const [error, setError] = useState('');
  const controller = useRef<AbortController | undefined>(undefined);
  const mounted = useRef(true);
  useEffect(() => { mounted.current = true; return () => { mounted.current = false; controller.current?.abort(); }; }, []);

  async function submit(value: DeleteOperation) {
    controller.current = new AbortController();
    setError(''); setOperation({ ...value, state: 'SUBMITTING' });
    try {
      await deleteExportTask(value.taskId, value.request, controller.current.signal);
      if (mounted.current) { setOperation(undefined); onAccepted?.(); }
    } catch (cause) {
      if (!mounted.current) return;
      const failure = normalizeApiError(cause).error;
      if (failure.outcome === 'UNKNOWN') {
        setOperation({ ...value, state: 'UNKNOWN', message: '删除结果尚未确认，请找回结果' });
      } else { setOperation(undefined); setError(failure.message); }
    }
  }

  return {
    operation, error,
    deleteTask(task: TaskListItem) {
      return submit({ taskId: task.taskId, request: { operationId: crypto.randomUUID(), expectedVersion: task.version }, state: 'SUBMITTING' });
    },
    retry() { if (operation?.state === 'UNKNOWN') return submit(operation); },
  };
}
