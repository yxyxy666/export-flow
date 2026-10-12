// 设计：[HTTP状态契约](../../../../../specs/002-order-management/contracts/http-api.md)。测试：TC-204/206。
import type { OrderFilter, OrderFilterFormValues, OrderQuery } from '../types/order.types';
import { orderFilterSchema, orderQuerySchema } from './order-schema';
import { normalizeMoney } from '../../../shared/formatters/money';
import { toUtcInstant } from '../../../shared/formatters/date-time';

/** 校验编辑草稿并消除空项；校验失败不改变已提交查询。 */
export function normalizeOrderFilter(values: OrderFilterFormValues): OrderFilter {
  const { createdFromLocal, createdToLocal, ...input } = values;
  const filter: Record<string, unknown> = {};
  for (const [key, value] of Object.entries(input)) {
    if (value !== undefined && value !== '') filter[key] = typeof value === 'string' ? value.trim() : value;
  }
  for (const key of ['keyword', 'orderNo']) if (filter[key] === '') delete filter[key];
  if (filter.minAmount) filter.minAmount = normalizeMoney(String(filter.minAmount));
  if (filter.maxAmount) filter.maxAmount = normalizeMoney(String(filter.maxAmount));
  if (createdFromLocal) filter.createdFrom = toUtcInstant(createdFromLocal);
  if (createdToLocal) filter.createdTo = toUtcInstant(createdToLocal);
  return orderFilterSchema.parse(filter);
}

/** 按项恢复合法的非敏感URL，移除keyword、重复值和非法项。 */
export function parseOrderQuery(params: URLSearchParams): OrderQuery {
  const filter: Record<string, string> = {};
  for (const name of ['orderNo', 'status', 'channel', 'createdFrom', 'createdTo', 'minAmount', 'maxAmount']) {
    const value = params.get(name);
    if (value && params.getAll(name).length === 1 && orderFilterSchema.safeParse({ [name]: value }).success) {
      filter[name] = value;
    }
  }
  if (!orderFilterSchema.safeParse(filter).success) {
    if (filter.createdFrom && filter.createdTo && filter.createdFrom > filter.createdTo) {
      delete filter.createdFrom; delete filter.createdTo;
    }
    if (filter.minAmount && filter.maxAmount &&
      BigInt(filter.minAmount.replace('.', '')) > BigInt(filter.maxAmount.replace('.', ''))) {
      delete filter.minAmount; delete filter.maxAmount;
    }
  }
  const single = (name: string) => params.getAll(name).length === 1 ? params.get(name) : null;
  const page = Number(single('page') ?? 1);
  const size = Number(single('pageSize') ?? 20);
  return { filter: orderFilterSchema.parse(filter),
    page: Number.isInteger(page) && page >= 1 && page <= 1000000 ? page : 1,
    pageSize: size === 20 || size === 50 || size === 100 ? size : 20,
    sortDirection: single('sortDirection') === 'ASC' ? 'ASC' : 'DESC' };
}

/** keyword只用于内存查询；URL不写敏感值。 */
export function serializeOrderQuery(query: OrderQuery): URLSearchParams {
  const validated = orderQuerySchema.parse(query);
  const params = new URLSearchParams();
  for (const name of ['orderNo', 'status', 'channel', 'createdFrom', 'createdTo', 'minAmount', 'maxAmount'] as const) {
    const value = validated.filter[name]; if (value) params.set(name, value);
  }
  if (query.page !== 1) params.set('page', String(query.page));
  if (query.pageSize !== 20) params.set('pageSize', String(query.pageSize));
  if (query.sortDirection !== 'DESC') params.set('sortDirection', query.sortDirection);
  return params;
}
