// 设计：[统一API函数](../../../../specs/002-order-management/function-design-reference.md#frontendsrcsharedapiapits)。
// 验证：[API测试](../../features/orders/__tests__/order-api.test.ts)，TC-210/211。
import type { OrderDto, OrderQuery } from '../../features/orders/types/order.types';
import type { CreateExportRequest, CreateExportResponse } from '../../features/export-tasks/types/export-task.types';
import type { PageResult } from '../types/page-result';
import { orderPageSchema, orderQuerySchema } from '../../features/orders/models/order-schema';
import { createExportRequestSchema, createExportResponseSchema } from '../../features/export-tasks/models/export-task-schema';
import { ApiRequestError } from './api-error';
import { requestJson } from './http-client';

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
