// 本期实施与验证：[任务记录](../../../../../specs/004-task-management/tasks.md)。
// 增量实施：[计划](../../../../../specs/003-select-all-filtered/plan.md)、[任务](../../../../../specs/003-select-all-filtered/tasks.md)；[测试](../../orders/__tests__/order-management.test.tsx)。
// 设计：[HTTP契约](../../../../../specs/002-order-management/contracts/http-api.md)。
// 验证：[API测试](../../orders/__tests__/order-api.test.ts)，TC-210/211。
import { z } from 'zod';
import { EXPORT_FIELDS, TASK_STATUSES } from '../types/export-task.types';
import { orderFilterSchema, orderIdSchema, utcInstantSchema } from '../../orders/models/order-schema';

export const exportFieldsSchema = z.array(z.enum(EXPORT_FIELDS)).min(1).max(9)
  .refine(fields => new Set(fields).size === fields.length);
export const createExportRequestSchema = z.strictObject({
  scope: z.discriminatedUnion('type', [
    z.strictObject({ type: z.literal('SELECTED'), orderIds: z.array(orderIdSchema).min(1).max(200000) }),
    z.strictObject({ type: z.literal('FILTERED'), filter: orderFilterSchema,
      excludedOrderIds: z.array(orderIdSchema).max(200000).optional() }),
  ]),
  fields: exportFieldsSchema,
});
export const createExportResponseSchema = z.strictObject({
  taskId: z.uuid(), taskNo: z.string().min(1),
  scopeType: z.enum(['SELECTED', 'FILTERED']),
  totalCount: z.number().int().min(1).max(200000),
  fields: exportFieldsSchema, createdAt: utcInstantSchema,
});

/** 数据库long始终以十进制字符串传输，避免JS数字丢失版本精度。 */
export const taskVersionSchema = z.string().regex(/^(0|[1-9]\d*)$/)
  .refine(value => BigInt(value) <= 9223372036854775807n);
export const taskQuerySchema = z.strictObject({
  keyword: z.string().trim().refine(value => [...value].length <= 100).optional(),
  status: z.enum(TASK_STATUSES).optional(), page: z.number().int().min(1).max(2147483647),
  pageSize: z.union([z.literal(20), z.literal(50), z.literal(100)]),
});
export const taskListItemSchema = z.strictObject({
  taskId: z.uuid(), taskNo: z.string().min(1).max(128), scopeType: z.enum(['SELECTED', 'FILTERED']),
  totalCount: z.number().int().min(1).max(200000), processedCount: z.number().int().min(0).max(200000),
  progressPercent: z.number().int().min(0).max(100), status: z.string().min(1).max(64), fields: exportFieldsSchema,
  fileName: z.string().min(1).max(255), fileSize: taskVersionSchema.nullable(), createdAt: utcInstantSchema,
  finishedAt: utcInstantSchema.nullable(), errorCode: z.string().max(128).nullable(), errorSummary: z.string().max(255).nullable(),
  version: taskVersionSchema, controlVersion: taskVersionSchema, allowedActions: z.array(z.enum(['PAUSE', 'DOWNLOAD'])),
  executionPhase: z.enum(['NOT_STARTED', 'COLLECTING', 'CHECKING', 'REBUILDING', 'ASSEMBLING', 'COMPLETE']),
  repairState: z.enum(['NONE', 'CHECKING', 'REBUILDING', 'PAUSING', 'PAUSED', 'FAILED']),
  repairEpoch: taskVersionSchema, repairAttempt: z.number().int().min(0).max(3), repairReasonCode: z.string().max(128).nullable(),
}).refine(value => value.processedCount <= value.totalCount && (value.status === 'SUCCEEDED'
  ? value.progressPercent === 100 : value.progressPercent <= 99));
export const taskPageSchema = z.strictObject({
  items: z.array(taskListItemSchema).max(100), page: z.number().int().positive(), pageSize: z.union([z.literal(20), z.literal(50), z.literal(100)]),
  total: taskVersionSchema, activityVersion: taskVersionSchema, hasActiveTasks: z.boolean(),
});
export const taskUpdatedSchema = z.strictObject({ activityVersion: taskVersionSchema, task: taskListItemSchema });
export const taskStreamMarkerSchema = z.strictObject({ connectionId: z.uuid(), activityVersion: taskVersionSchema });
export const pauseRequestSchema = z.strictObject({ operationId: z.uuid(), expectedControlVersion: taskVersionSchema });
export const pauseResultSchema = z.strictObject({ outcome: z.enum(['APPLIED', 'REPLAY']), task: taskListItemSchema });
