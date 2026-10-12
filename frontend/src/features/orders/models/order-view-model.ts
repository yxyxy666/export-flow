// 设计：[函数清单](../../../../../specs/002-order-management/function-design-reference.md)。测试：TC-204。
import type { OrderDto, OrderViewModel } from '../types/order.types';
import { formatMoney } from '../../../shared/formatters/money';
import { formatHongKongDateTime } from '../../../shared/formatters/date-time';
export const STATUS_LABELS: Record<string, string> = {
  PENDING: '待支付', PAID: '已支付', SHIPPED: '已发货', COMPLETED: '已完成', CANCELED: '已取消',
};
export const CHANNEL_LABELS: Record<string, string> = {
  WEB: 'Web 商城', APP: '移动 App', STORE: '线下门店', PARTNER: '合作渠道',
};
/** 单一展示映射，未知枚举回退，不改变客户原值和大ID。 */
export function toOrderViewModel(dto: OrderDto): OrderViewModel {
  const { id, ...values } = dto;
  return { ...values, key: id,
    status: Object.hasOwn(STATUS_LABELS, dto.status) ? STATUS_LABELS[dto.status] : '未知',
    channel: Object.hasOwn(CHANNEL_LABELS, dto.channel) ? CHANNEL_LABELS[dto.channel] : '未知',
    amount: formatMoney(dto.amount), createdAt: formatHongKongDateTime(dto.createdAt) };
}
