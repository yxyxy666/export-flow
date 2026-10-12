// 增量实施：[计划](../../../../../specs/003-select-all-filtered/plan.md)、[任务](../../../../../specs/003-select-all-filtered/tasks.md)；[测试](../../orders/__tests__/order-management.test.tsx)。
// 设计：[HTTP契约](../../../../../specs/002-order-management/contracts/http-api.md)。
// 验证：[API测试](../../orders/__tests__/order-api.test.ts)，TC-210/211。
import { z } from 'zod';
import { EXPORT_FIELDS } from '../types/export-task.types';
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
