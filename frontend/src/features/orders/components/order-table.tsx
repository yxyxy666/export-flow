// 增量实施：[计划](../../../../../specs/003-select-all-filtered/plan.md)、[任务](../../../../../specs/003-select-all-filtered/tasks.md)；[测试](../__tests__/order-management.test.tsx)。
// 设计：[函数清单](../../../../../specs/002-order-management/function-design-reference.md)。测试：TC-203/207。
import { Table } from 'antd';
import type { OrderViewModel, SortDirection } from '../types/order.types';
export const FIELD_LABELS = { orderNo: '订单号', customerName: '客户姓名', customerPhone: '客户手机号',
  status: '状态', channel: '渠道', amount: '金额', currency: '币种', region: '地区', createdAt: '下单时间' };
export interface OrderTableProps {
  rows: OrderViewModel[]; isDisabled: boolean; isSelectionDisabled: boolean; selectedIds: ReadonlySet<string>; sortDirection: SortDirection;
  onToggle(id: string, isSelected: boolean): void;
  onTogglePage(ids: readonly string[], isSelected: boolean): void; onSort(direction: SortDirection): void;
}
/** 受控选择不复制订单对象；服务端排序方向同时作用于时间和ID。 */
export function OrderTable(props: OrderTableProps) {
  return <Table<OrderViewModel> rowKey="key" dataSource={props.rows} pagination={false} size="small"
    scroll={{ x: 1000 }} locale={{ emptyText: '暂无数据' }}
    rowSelection={{ selectedRowKeys: [...props.selectedIds], preserveSelectedRowKeys: true,
      getCheckboxProps: () => ({ disabled: props.isDisabled || props.isSelectionDisabled }),
      onSelect: (row, isSelected) => props.onToggle(row.key, isSelected),
      onSelectAll: (isSelected, _rows, changedRows) => props.onTogglePage(changedRows.map(row => row.key), isSelected),
    }}
    columns={Object.entries(FIELD_LABELS).map(([name, title]) => ({ title, dataIndex: name, key: name,
      ...(name === 'createdAt' ? { sorter: !props.isDisabled, sortDirections: ['ascend','descend'] as ('ascend'|'descend')[],
        sortOrder: props.sortDirection === 'ASC' ? 'ascend' as const : 'descend' as const } : {}) }))}
    onChange={(_pagination, _filters, _sorter, extra) => {
      if (!props.isDisabled && extra.action === 'sort') props.onSort(props.sortDirection === 'ASC' ? 'DESC' : 'ASC');
    }} />;
}
