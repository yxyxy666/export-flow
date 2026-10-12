// 增量实施：[计划](../../../../../specs/003-select-all-filtered/plan.md)、[任务](../../../../../specs/003-select-all-filtered/tasks.md)；[测试](../__tests__/order-management.test.tsx)。
// 设计：[创建流程](../../../../../specs/002-order-management/function-design.md)。测试：TC-209/210。
import type { CreateExportTaskApi, ExportField, ExportScope, FrozenExportSubmission } from '../../export-tasks/types/export-task.types';
import { createExportRequestSchema } from '../../export-tasks/models/export-task-schema';
import { ApiRequestError, normalizeApiError } from '../../../shared/api/api-error';
/** 固定深度不可变的范围/字段快照和单个幂等键。 */
export function createSubmission(scope: ExportScope, fields: readonly ExportField[], newKey: () => string): FrozenExportSubmission {
  const request = createExportRequestSchema.parse({ scope, fields });
  if (request.scope.type === 'SELECTED') Object.freeze(request.scope.orderIds);
  else {
    Object.freeze(request.scope.filter);
    if (request.scope.excludedOrderIds) Object.freeze(request.scope.excludedOrderIds);
  }
  Object.freeze(request.scope); Object.freeze(request.fields); Object.freeze(request);
  return { request, idempotencyKey: newKey(), localState: 'SUBMITTING' };
}
/** 仅分类API结果，不执行页面回调；未知结果保留原键及原请求。 */
export async function submitOrderExport(submission: FrozenExportSubmission, createTask: CreateExportTaskApi,
  signal?: AbortSignal): Promise<FrozenExportSubmission> {
  try {
    const result = await createTask(submission.request, submission.idempotencyKey, signal);
    return { ...submission, localState: 'CONFIRMED', result, error: undefined };
  } catch (error) {
    const wrapped = error && typeof error === 'object' ? error as { status?: number; error?: unknown } : {};
    const safe = error instanceof ApiRequestError ? error : normalizeApiError(wrapped.error ?? error, wrapped.status);
    return { ...submission, localState: safe.error.outcome === 'REJECTED' ? 'REJECTED' : 'UNKNOWN', error: safe.error };
  }
}
