// 增量实施：[计划](../../../../../specs/003-select-all-filtered/plan.md)、[任务](../../../../../specs/003-select-all-filtered/tasks.md)；[测试](../__tests__/order-management.test.tsx)。
// 设计：[计划](../../../../../specs/002-order-management/plan.md)。测试：TC-201至TC-212。
import { useEffect, useMemo } from 'react';
import { Alert, App, Button, Card, Pagination, Select, Space } from 'antd';
import { useOrders } from '../hooks/use-orders';
import { useOrderSelection } from '../hooks/use-order-selection';
import { useOrderExport } from '../hooks/use-order-export';
import { OrderFilterForm } from '../components/order-filter-form';
import { OrderTable } from '../components/order-table';
import { ExportConfigDialog } from '../components/export-config-dialog';
import { formatHongKongDateTime } from '../../../shared/formatters/date-time';
import { createExportTask } from '../../../shared/api/api';
import { isOrderSelected, toSelectionExportScope } from '../models/order-selection';
import type { CreateExportResponse } from '../../export-tasks/types/export-task.types';

/** 页面组合查询/选择/提交，成功后只展示创建结果并保留查询。 */
export function OrderManagementPage() {
  const orders = useOrders(); const { message } = App.useApp();
  const total = orders.pageResult?.total ?? 0;
  const selection = useOrderSelection(`${orders.historyRevision}:${JSON.stringify(orders.query.filter)}`, total);
  function notifyCreated(result: CreateExportResponse) {
    void message.success(<><span>导出任务已创建</span> <span>{result.taskNo}</span></>);
  }
  const exporter = useOrderExport({ createTask: createExportTask, newKey: () => crypto.randomUUID(),
    clearSelection: () => selection.dispatch({ type: 'CLEAR' }), notifyCreated });
  const isFrozen = exporter.submission?.localState === 'SUBMITTING' || exporter.submission?.localState === 'UNKNOWN';
  const isSelectionDisabled = isFrozen || !orders.isValidResult;
  useEffect(() => { exporter.resetDraft(); }, [orders.historyRevision]);
  const values = useMemo(() => {
    const { createdFrom, createdTo, ...filter } = orders.query.filter;
    return { ...filter, ...(createdFrom ? { createdFromLocal: formatHongKongDateTime(createdFrom) } : {}),
      ...(createdTo ? { createdToLocal: formatHongKongDateTime(createdTo) } : {}) };
  }, [orders.query.filter]);
  function handleReset() {
    if (isFrozen) return;
    orders.reset(); selection.dispatch({ type: 'CLEAR' }); exporter.resetDraft();
  }
  const selectedReason = !selection.count ? '请先选择订单' : selection.count > 200000 ? `已选 ${selection.count} 条，超过200000条上限，请缩小范围` : '';
  const scopeReason = orders.isLoading ? '查询中，请等待结果' : orders.error ? '结果已失效，请先重试后导出' :
    !orders.isValidResult ? '请先查询订单' : total === 0 ? '没有匹配订单' : '';
  const filteredReason = orders.isLoading ? '查询中，请等待结果' : orders.error ? '结果已失效，请先重试后导出' :
    !orders.isValidResult ? '请先查询订单' : total === 0 ? '没有匹配订单' : total > 200000 ? '结果超过200000条，请缩小范围' : '';
  return <section className="order-management-page"><h1>订单管理</h1>
    {orders.notice && <Alert type="info" title={orders.notice} />}
    <Card size="small" title="订单查询"><OrderFilterForm values={values} isDisabled={isFrozen}
      onQuery={orders.submitFilter} onReset={handleReset} /></Card>
    <Card size="small" title="导出订单"><Space wrap>
      <Button type="primary" disabled={isFrozen || !orders.isValidResult || !!selectedReason}
        onClick={() => exporter.open(toSelectionExportScope(selection, orders.query.filter))}>导出已选</Button>
      <Button disabled={isFrozen || !!filteredReason} onClick={() => exporter.open({ type: 'FILTERED', filter: orders.query.filter })}>导出筛选结果</Button>
      <span>已选 {selection.count} 条</span>
      {selection.mode === 'EXCLUDE' && <span>已全选筛选结果，共{total}条</span>}
      <Button disabled={isFrozen || !!scopeReason} onClick={() => selection.dispatch({ type: 'SELECT_ALL' })}>全选筛选结果</Button>
      <Button disabled={isFrozen || !!scopeReason} onClick={() => selection.dispatch({ type: 'INVERT_ALL' })}>全部反选</Button>
      <Button disabled={isSelectionDisabled || !orders.rows.length} onClick={() => selection.dispatch({ type: 'SELECT_PAGE', ids: orders.rows.map(row => row.key) })}>全选当前页</Button>
      <Button disabled={isSelectionDisabled || !orders.rows.length} onClick={() => selection.dispatch({ type: 'INVERT_PAGE', ids: orders.rows.map(row => row.key) })}>反选当前页</Button>
      <Button disabled={isFrozen || (selection.mode === 'INCLUDE' && !selection.ids.size)} onClick={() => selection.dispatch({ type: 'CLEAR' })}>清空已选</Button>
    </Space>{selection.notice && <Alert type="warning" title={selection.notice} />}
      {selectedReason && <p>{selectedReason}</p>}{filteredReason && <p role="status">{filteredReason}</p>}</Card>
    {orders.error && <Alert type="error" title="查询失败，暂时无法查询，请重试"
      action={<Button disabled={isFrozen} onClick={() => void orders.refresh()}>重试查询</Button>} />}
    <Card size="small"><OrderTable rows={orders.rows} isDisabled={isFrozen} isSelectionDisabled={isSelectionDisabled}
      selectedIds={new Set(orders.rows.filter(row => isOrderSelected(selection, row.key)).map(row => row.key))}
      sortDirection={orders.query.sortDirection} onToggle={(id, isSelected) => selection.dispatch({ type: 'TOGGLE', id, isSelected })}
      onTogglePage={(ids, isSelected) => selection.dispatch({ type: isSelected ? 'SELECT_PAGE' : 'DESELECT_PAGE', ids })}
      onSort={orders.changeSort} />
      <div className="order-pagination"><span>共 {total} 条</span>
        <Pagination current={orders.query.page} pageSize={orders.query.pageSize} total={total} showSizeChanger={false}
          disabled={isFrozen} onChange={page => orders.changePage(page, orders.query.pageSize)} />
        <Select aria-label="每页条数" value={orders.query.pageSize} virtual={false} disabled={isFrozen}
          options={[20,50,100].map(value => ({ value, label: `${value} 条/页` }))}
          onChange={size => orders.changePage(1, size)} />
      </div>
    </Card>
    <ExportConfigDialog state={exporter.dialog} submission={exporter.submission}
      count={exporter.dialog.scope?.type === 'SELECTED' ? exporter.dialog.scope.orderIds.length : Math.max(0, total - (exporter.dialog.scope?.excludedOrderIds?.length ?? 0))}
      onFieldsChange={exporter.setFields} onConfirm={() => void exporter.confirm()} onRetry={() => void exporter.retryUnknown()} onClose={exporter.close} />
  </section>;
}
