// 设计：[函数清单](../../../../../specs/002-order-management/function-design-reference.md)。测试：TC-208/209/210。
import { Alert, Button, Checkbox, Modal } from 'antd';
import { useCallback } from 'react';
import { EXPORT_FIELDS } from '../../export-tasks/types/export-task.types';
import type { ExportField, ExportScope, FrozenExportSubmission } from '../../export-tasks/types/export-task.types';
import { FIELD_LABELS } from './order-table';
export interface ExportConfigDialogProps {
  state: { isOpen: boolean; scope?: ExportScope; fields: ExportField[] };
  submission?: FrozenExportSubmission; count: number;
  onFieldsChange(fields: readonly ExportField[]): void; onConfirm(): void; onRetry(): void; onClose(): void;
}
/** 提交中/未知结果禁用破坏性动作，最后一个字段不能取消。 */
export function ExportConfigDialog(props: ExportConfigDialogProps) {
  const state = props.submission?.localState;
  const isFrozen = state === 'SUBMITTING' || state === 'UNKNOWN';
  const focusFields = useCallback((element: HTMLDivElement | null) => {
    if (props.state.isOpen) element?.querySelector('input')?.focus();
  }, [props.state.isOpen]);
  return <Modal title="导出配置" open={props.state.isOpen} onCancel={props.onClose} closable={!isFrozen}
    mask={{ closable: !isFrozen }} keyboard={!isFrozen} focusable={{ focusTriggerAfterClose: true }}
    footer={<><Button onClick={props.onClose} disabled={isFrozen}>取消</Button>
      {state === 'UNKNOWN' ? <Button type="primary" onClick={props.onRetry}>重试确认</Button> :
        <Button type="primary" onClick={props.onConfirm} disabled={isFrozen}>确认</Button>}</>}>
    <p>导出范围：{props.state.scope?.type === 'SELECTED' ? '已选订单' : '已提交筛选结果'}，共 {props.count} 条。单次上限 200000 条。</p>
    <div className="export-fields" ref={focusFields}>{EXPORT_FIELDS.map((field, index) => <Checkbox key={field}
      autoFocus={index === 0} checked={props.state.fields.includes(field)}
      disabled={isFrozen} aria-disabled={isFrozen || (props.state.fields.length === 1 && props.state.fields.includes(field))}
      onChange={event => {
        if (!event.target.checked && props.state.fields.length === 1) return;
        props.onFieldsChange(event.target.checked ? [...props.state.fields, field] : props.state.fields.filter(value => value !== field));
      }}>
      {FIELD_LABELS[field]}</Checkbox>)}</div>
    {state === 'SUBMITTING' && <p role="status">正在提交，等待创建结果</p>}
    {state === 'UNKNOWN' ? <Alert type="warning" title="结果尚未确认，请用原请求重试确认"
      description="离开页面不会撤销后台任务；重新进入后可在后续完整任务功能中核对。" /> :
      props.submission?.error && <Alert type="error" title={props.submission.error.code === 'EXPORT_LIMIT_EXCEEDED'
        ? '请缩小范围' : props.submission.error.message} description={props.submission.error.details
          ? `当前${props.submission.error.details.currentCount}条，单次上限200000条` : undefined} />}
  </Modal>;
}
