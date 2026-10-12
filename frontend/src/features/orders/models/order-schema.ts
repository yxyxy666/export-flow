// 设计：[数据模型](../../../../../specs/002-order-management/data-model.md)。
// 验证：[API测试](../__tests__/order-api.test.ts)，TC-210/211。
import { z } from 'zod';

export const orderIdSchema = z.string().regex(/^[1-9]\d{0,18}$/)
  .pipe(z.string().refine(value => BigInt(value) <= 9223372036854775807n));
export const moneySchema = z.string().regex(/^(0|[1-9]\d{0,15})\.\d{2}$/);
export const utcInstantSchema = z.string().regex(/^\d{4}-\d{2}-\d{2}T\d{2}:\d{2}:\d{2}Z$/)
  .refine(value => {
    const timestamp = Date.parse(value);
    return Number.isFinite(timestamp) && new Date(timestamp).toISOString() === value.replace('Z', '.000Z');
  });
export const pageSizeSchema = z.union([z.literal(20), z.literal(50), z.literal(100)]);

export const orderFilterSchema = z.strictObject({
  keyword: z.string().max(100).optional(),
  orderNo: z.string().max(64).optional(),
  status: z.enum(['PENDING', 'PAID', 'SHIPPED', 'COMPLETED', 'CANCELED']).optional(),
  channel: z.enum(['WEB', 'APP', 'STORE', 'PARTNER']).optional(),
  createdFrom: utcInstantSchema.optional(),
  createdTo: utcInstantSchema.optional(),
  minAmount: moneySchema.optional(),
  maxAmount: moneySchema.optional(),
}).refine(value => !value.createdFrom || !value.createdTo || value.createdFrom <= value.createdTo,
  { message: '开始时间不得晚于结束时间', path: ['createdTo'] })
  .refine(value => !value.minAmount || !value.maxAmount ||
    !moneySchema.safeParse(value.minAmount).success || !moneySchema.safeParse(value.maxAmount).success ||
    BigInt(value.minAmount.replace('.', '')) <= BigInt(value.maxAmount.replace('.', '')),
  { message: '最小金额不得大于最大金额', path: ['maxAmount'] });

export const orderQuerySchema = z.strictObject({
  filter: orderFilterSchema,
  page: z.number().int().min(1).max(1000000),
  pageSize: pageSizeSchema,
  sortDirection: z.enum(['ASC', 'DESC']),
});
export const orderPageSchema = z.strictObject({
  items: z.array(z.strictObject({
    id: orderIdSchema,
    orderNo: z.string(), customerName: z.string(), customerPhone: z.string(),
    status: z.string(), channel: z.string(), amount: moneySchema,
    currency: z.literal('CNY'), region: z.string(), createdAt: utcInstantSchema,
  })).max(100),
  page: z.number().int().min(1).max(1000000),
  pageSize: pageSizeSchema,
  total: z.number().int().min(0).max(Number.MAX_SAFE_INTEGER),
}).refine(value => value.items.length <= value.pageSize);
