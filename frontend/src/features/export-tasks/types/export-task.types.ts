// 增量实施：[计划](../../../../../specs/003-select-all-filtered/plan.md)、[任务](../../../../../specs/003-select-all-filtered/tasks.md)；[测试](../../orders/__tests__/order-management.test.tsx)。
// 设计与测试：[任务记录](../../../../../specs/002-order-management/tasks.md)。
import type { OrderFilter, OrderId, UtcInstant } from '../../orders/types/order.types';
import type { ApiError } from '../../../shared/api/api-error';

export const EXPORT_FIELDS = ['orderNo', 'customerName', 'customerPhone', 'status', 'channel',
  'amount', 'currency', 'region', 'createdAt'] as const;
export type ExportField = typeof EXPORT_FIELDS[number];
export type ExportScope =
  | { type: 'SELECTED'; orderIds: readonly OrderId[] }
  | { type: 'FILTERED'; filter: OrderFilter; excludedOrderIds?: readonly OrderId[] };
export interface CreateExportRequest {
  scope: ExportScope;
  fields: readonly ExportField[];
}
export interface CreateExportResponse {
  taskId: string;
  taskNo: string;
  scopeType: 'SELECTED' | 'FILTERED';
  totalCount: number;
  fields: ExportField[];
  createdAt: UtcInstant;
}
export type CreateExportTaskApi = (request: CreateExportRequest, key: string,
  signal?: AbortSignal) => Promise<CreateExportResponse>;
export interface FrozenExportSubmission {
  request: CreateExportRequest;
  idempotencyKey: string;
  localState: 'SUBMITTING' | 'UNKNOWN' | 'CONFIRMED' | 'REJECTED';
  result?: CreateExportResponse;
  error?: ApiError;
}
