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

// 任务管理：[规格](../../../../../specs/004-task-management/plan.md)。
export const TASK_STATUSES = ['PENDING', 'RUNNING', 'PAUSING', 'PAUSED', 'SUCCEEDED', 'FAILED'] as const;
export type TaskStatus = typeof TASK_STATUSES[number];
export interface TaskQuery { keyword?: string; status?: TaskStatus; page: number; pageSize: 20 | 50 | 100 }
export interface TaskListItem {
  taskId: string; taskNo: string; scopeType: 'SELECTED' | 'FILTERED'; totalCount: number;
  processedCount: number; progressPercent: number; status: string; fields: ExportField[];
  fileName: string; fileSize: string | null; createdAt: string; finishedAt: string | null;
  errorCode: string | null; errorSummary: string | null; version: string; controlVersion: string;
  allowedActions: ('PAUSE' | 'RESUME' | 'DOWNLOAD')[];
  executionPhase: 'NOT_STARTED' | 'COLLECTING' | 'CHECKING' | 'REBUILDING' | 'ASSEMBLING' | 'COMPLETE';
  repairState: 'NONE' | 'CHECKING' | 'REBUILDING' | 'PAUSING' | 'PAUSED' | 'FAILED';
  repairEpoch: string; repairAttempt: number; repairReasonCode: string | null;
}
export interface TaskPage {
  items: TaskListItem[]; page: number; pageSize: number; total: string;
  activityVersion: string; hasActiveTasks: boolean;
}
export interface TaskUpdatedEvent { activityVersion: string; task: TaskListItem }
export interface PauseRequest { operationId: string; expectedControlVersion: string }
export interface PauseResult { outcome: 'APPLIED' | 'REPLAY'; task: TaskListItem }
