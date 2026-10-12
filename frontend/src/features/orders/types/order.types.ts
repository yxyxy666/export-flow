// 增量实施：[计划](../../../../../specs/003-select-all-filtered/plan.md)、[任务](../../../../../specs/003-select-all-filtered/tasks.md)；[测试](../__tests__/order-management.test.tsx)。
// 设计与测试：[任务记录](../../../../../specs/002-order-management/tasks.md)。
export type OrderId = string;
export type UtcInstant = string;
export type MoneyDecimal = string;
export type OrderStatus = 'PENDING' | 'PAID' | 'SHIPPED' | 'COMPLETED' | 'CANCELED';
export type SalesChannel = 'WEB' | 'APP' | 'STORE' | 'PARTNER';
export type PageSize = 20 | 50 | 100;
export type SortDirection = 'ASC' | 'DESC';

export interface OrderFilter {
  keyword?: string;
  orderNo?: string;
  status?: OrderStatus;
  channel?: SalesChannel;
  createdFrom?: UtcInstant;
  createdTo?: UtcInstant;
  minAmount?: MoneyDecimal;
  maxAmount?: MoneyDecimal;
}

export interface OrderQuery {
  filter: OrderFilter;
  page: number;
  pageSize: PageSize;
  sortDirection: SortDirection;
}

/** 输出枚举保留原值，由展示模型回退为“未知”。 */
export interface OrderDto {
  id: OrderId;
  orderNo: string;
  customerName: string;
  customerPhone: string;
  status: string;
  channel: string;
  amount: MoneyDecimal;
  currency: 'CNY';
  region: string;
  createdAt: UtcInstant;
}

export interface OrderFilterFormValues extends OrderFilter {
  createdFromLocal?: string;
  createdToLocal?: string;
}
export interface OrderViewModel extends Omit<OrderDto, 'id'> { key: OrderId }
export interface OrderSelectionState { mode: 'INCLUDE' | 'EXCLUDE'; ids: ReadonlySet<OrderId>; notice?: string }
export type SelectionAction =
  | { type: 'TOGGLE'; id: OrderId; isSelected: boolean }
  | { type: 'SELECT_PAGE' | 'DESELECT_PAGE' | 'INVERT_PAGE'; ids: readonly OrderId[] }
  | { type: 'CLEAR' } | { type: 'SELECT_ALL' } | { type: 'INVERT_ALL' };
