// 本次增量：[计划](../../../../specs/005-task-resume-and-live-progress/plan.md)。
// 本期实施与验证：[任务记录](../../../../specs/004-task-management/tasks.md)。
// 设计：[统一API函数](../../../../specs/002-order-management/function-design-reference.md#frontendsrcsharedapiapits)。
// 验证：[API测试](../../features/orders/__tests__/order-api.test.ts)，TC-210/211。
import type { OrderDto, OrderQuery } from '../../features/orders/types/order.types';
import type { CreateExportRequest, CreateExportResponse } from '../../features/export-tasks/types/export-task.types';
import type { PageResult } from '../types/page-result';
import { orderPageSchema, orderQuerySchema } from '../../features/orders/models/order-schema';
import { createExportRequestSchema, createExportResponseSchema } from '../../features/export-tasks/models/export-task-schema';
import { ApiRequestError } from './api-error';
import { requestJson } from './http-client';
import type { DeleteTaskRequest, DeleteTaskResponse, PauseRequest, PauseResult, TaskPage, TaskQuery } from '../../features/export-tasks/types/export-task.types';
import { deleteTaskRequestSchema, deleteTaskResponseSchema, pauseRequestSchema, pauseResultSchema, taskPageSchema, taskQuerySchema } from '../../features/export-tasks/models/export-task-schema';
import { writeTaskQuery } from '../../features/export-tasks/models/task-query';

/** 唯一订单查询入口：只编码已提交条件，校验协议后才交给查询缓存。 */
export async function queryOrders(query: OrderQuery, signal?: AbortSignal): Promise<PageResult<OrderDto>> {
  const parsed = orderQuerySchema.safeParse(query);
  if (!parsed.success) throw new ApiRequestError('protocol', {
    code: 'VALIDATION_ERROR', message: '查询条件无效', traceId: '', outcome: 'REJECTED',
  });
  const params = new URLSearchParams();
  for (const [key, value] of Object.entries(parsed.data.filter)) {
    if (value !== undefined && value !== '') params.set(key, value);
  }
  params.set('page', String(parsed.data.page));
  params.set('pageSize', String(parsed.data.pageSize));
  params.set('sortDirection', parsed.data.sortDirection);
  const response = orderPageSchema.safeParse(await requestJson(`/api/v1/orders?${params}`, { signal }));
  if (!response.success) throw new ApiRequestError('protocol', {
    code: 'INVALID_RESPONSE', message: '订单响应无效，请重试', traceId: '', outcome: 'UNKNOWN',
  });
  return response.data;
}

/** 唯一任务创建入口：保留原键及有序字段，只发送POST，不追加详情或状态查询。 */
export async function createExportTask(request: CreateExportRequest, idempotencyKey: string,
  signal?: AbortSignal): Promise<CreateExportResponse> {
  const parsed = createExportRequestSchema.safeParse(request);
  if (!parsed.success || !/^[A-Za-z0-9_-]{16,128}$/.test(idempotencyKey)) {
    throw new ApiRequestError('protocol', {
      code: 'VALIDATION_ERROR', message: '导出配置或提交标识无效', traceId: '', outcome: 'REJECTED',
    });
  }
  const response = createExportResponseSchema.safeParse(await requestJson('/api/v1/export-tasks', {
    method: 'POST', body: parsed.data, headers: { 'Idempotency-Key': idempotencyKey }, signal,
  }));
  if (!response.success) throw new ApiRequestError('protocol', {
    code: 'INVALID_RESPONSE', message: '创建结果尚未确认，请用原请求重试确认', traceId: '', outcome: 'UNKNOWN',
  });
  return response.data;
}

/** 权威分页查询统一入口；不从浏览器缓存推断任务控制状态。 */
export async function queryExportTasks(query: TaskQuery, signal?: AbortSignal): Promise<TaskPage> {
  const input = taskQuerySchema.parse(query);
  const result = taskPageSchema.safeParse(await requestJson(`/api/v1/export-tasks?${writeTaskQuery(input)}`, { signal }));
  if (!result.success) throw new ApiRequestError('protocol', {
    code: 'INVALID_RESPONSE', message: '任务响应无效，请刷新', traceId: '', outcome: 'UNKNOWN',
  });
  return result.data;
}
/** 保留调用方冻结的操作ID与控制版本，结果不明时不生成新操作。 */
export async function pauseExportTask(taskId: string, request: PauseRequest, signal?: AbortSignal): Promise<PauseResult> {
  const input = pauseRequestSchema.parse(request);
  if (!/^[0-9a-f-]{36}$/i.test(taskId)) throw new Error('任务标识无效');
  const result = pauseResultSchema.safeParse(await requestJson(`/api/v1/export-tasks/${taskId}/pause`, {
    method: 'POST', body: input, signal,
  }));
  if (!result.success) throw new ApiRequestError('protocol', {
    code: 'INVALID_RESPONSE', message: '暂停结果尚未确认，请找回结果', traceId: '', outcome: 'UNKNOWN',
  });
  return result.data;
}
/** 继续使用相同控制协议，结果不明仍保留调用方原动作和操作ID。 */
export async function resumeExportTask(taskId: string, request: PauseRequest, signal?: AbortSignal): Promise<PauseResult> {
  const input = pauseRequestSchema.parse(request);
  if (!/^[0-9a-f-]{36}$/i.test(taskId)) throw new Error('任务标识无效');
  const result = pauseResultSchema.safeParse(await requestJson(`/api/v1/export-tasks/${taskId}/resume`, {
    method: 'POST', body: input, signal,
  }));
  if (!result.success) throw new ApiRequestError('protocol', {
    code: 'INVALID_RESPONSE', message: '继续结果尚未确认，请找回结果', traceId: '', outcome: 'UNKNOWN',
  });
  return result.data;
}
/** 统一删除入口；调用方冻结操作ID和任务摘要版本，结果不明时复用原请求。 */
export async function deleteExportTask(taskId: string, request: DeleteTaskRequest, signal?: AbortSignal): Promise<DeleteTaskResponse> {
  const input = deleteTaskRequestSchema.parse(request);
  if (!/^[0-9a-f-]{36}$/i.test(taskId)) throw new Error('任务标识无效');
  const result = deleteTaskResponseSchema.safeParse(await requestJson(`/api/v1/export-tasks/${taskId}`, {
    method: 'DELETE', body: input, signal,
  }));
  if (!result.success) throw new ApiRequestError('protocol', {
    code: 'INVALID_RESPONSE', message: '删除结果尚未确认，请找回结果', traceId: '', outcome: 'UNKNOWN',
  });
  return result.data;
}
/** SSE入口集中于API层，调用方拥有连接释放与重连生命周期。 */
export function openTaskEventStream(): EventSource { return new EventSource('/api/v1/export-tasks/events'); }

/** 复用权威成功文件下载端点，页面不自行拼装协议路径。 */
export function exportTaskFileUrl(taskId: string): string {
  if (!/^[0-9a-f]{8}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{12}$/i.test(taskId)) throw new Error('任务标识无效');
  return `/api/v1/export-tasks/${taskId}/file`;
}
