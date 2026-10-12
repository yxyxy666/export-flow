// 设计：[创建流程](../../../../../specs/002-order-management/function-design.md)。测试：TC-208/209/210。
import { useEffect, useRef, useState } from 'react';
import { createSubmission, submitOrderExport } from '../application/order-export-use-case';
import { createExportRequestSchema, exportFieldsSchema } from '../../export-tasks/models/export-task-schema';
import { EXPORT_FIELDS } from '../../export-tasks/types/export-task.types';
import type { CreateExportResponse, CreateExportTaskApi, ExportField, ExportScope, FrozenExportSubmission } from '../../export-tasks/types/export-task.types';

export interface OrderExportDependencies {
  createTask: CreateExportTaskApi; newKey(): string; clearSelection(): void;
  notifyCreated(result: CreateExportResponse): void;
}
/** 引用锁防止双击并发；已确认结果先保存，页面回调异常不会反向改写结果。 */
export function useOrderExport(deps: OrderExportDependencies) {
  const [dialog, setDialog] = useState<{ isOpen: boolean; scope?: ExportScope; fields: ExportField[] }>(
    { isOpen: false, fields: [...EXPORT_FIELDS] });
  const [submission, setSubmission] = useState<FrozenExportSubmission>();
  const current = useRef(submission);
  const busy = useRef(false);
  const mounted = useRef(true);
  const controller = useRef<AbortController | undefined>(undefined);
  const handled = useRef(new Set<string>());
  useEffect(() => { mounted.current = true; return () => { mounted.current = false; controller.current?.abort(); }; }, []);
  const isFrozen = () => busy.current || current.current?.localState === 'UNKNOWN';
  function applySubmissionResult(next: FrozenExportSubmission) {
    current.current = next;
    if (!mounted.current) return;
    setSubmission(next);
    if (next.localState === 'CONFIRMED' && next.result && !handled.current.has(next.idempotencyKey)) {
      handled.current.add(next.idempotencyKey);
      setDialog(previous => ({ ...previous, isOpen: false }));
      for (const operation of [() => deps.clearSelection(), () => deps.notifyCreated(next.result!)]) {
        try { operation(); } catch { /* 页面操作异常不重发已确认提交，不泄漏载荷。 */ }
      }
    }
  }
  async function send(snapshot: FrozenExportSubmission) {
    busy.current = true; current.current = { ...snapshot, localState: 'SUBMITTING' };
    setSubmission(current.current); controller.current = new AbortController();
    try { applySubmissionResult(await submitOrderExport(current.current, deps.createTask, controller.current.signal)); }
    finally { busy.current = false; }
  }
  return { dialog, submission,
    open(scope: ExportScope) {
      if (isFrozen()) return;
      const fields = current.current?.localState === 'REJECTED' ? dialog.fields : [...EXPORT_FIELDS];
      createExportRequestSchema.parse({ scope, fields });
      if (current.current?.localState !== 'REJECTED') { current.current = undefined; setSubmission(undefined); }
      setDialog({ isOpen: true, scope, fields });
    },
    setFields(fields: readonly ExportField[]) {
      if (isFrozen()) return;
      const parsed = exportFieldsSchema.parse(fields);
      setDialog(previous => ({ ...previous, fields: EXPORT_FIELDS.filter(field => parsed.includes(field)) }));
    },
    close() {
      if (isFrozen()) return;
      setDialog(previous => ({ ...previous, isOpen: false }));
      if (current.current?.localState !== 'REJECTED') { current.current = undefined; setSubmission(undefined); }
    },
    resetDraft() {
      if (isFrozen()) return;
      current.current = undefined; setSubmission(undefined); setDialog({ isOpen: false, fields: [...EXPORT_FIELDS] });
    },
    async confirm() {
      if (isFrozen() || !dialog.isOpen || !dialog.scope || current.current?.localState === 'CONFIRMED') return;
      await send(createSubmission(dialog.scope, dialog.fields, deps.newKey));
    },
    async retryUnknown() {
      if (busy.current || current.current?.localState !== 'UNKNOWN') return;
      await send(current.current);
    },
  };
}

