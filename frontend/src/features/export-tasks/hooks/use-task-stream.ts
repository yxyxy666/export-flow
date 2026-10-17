// 设计与验证：[SSE契约](../../../../../specs/004-task-management/contracts/http-sse.md)。
import { useEffect, useRef, useState } from 'react';
import { openTaskEventStream } from '../../../shared/api/api';
import { subscribeTaskCreated } from '../../../shared/events/task-created';
import { taskStreamMarkerSchema, taskUpdatedSchema } from '../models/export-task-schema';
import type { TaskListItem, TaskPage } from '../types/export-task.types';

/** 单连接事件驱动；连接身份和已释放标志阻止迟到回调触发业务。 */
export function useTaskStream(options: { identity: string; hasActiveTasks: boolean; activityVersion: string;
  refresh(): Promise<TaskPage | undefined>; accept(task: TaskListItem): void }) {
  const latest = useRef(options); latest.current = options;
  const wakeRef = useRef<() => Promise<void>>(async () => {});
  const [notice, setNotice] = useState('');
  useEffect(() => {
    let source: EventSource | undefined; let disposed = false; let timer: ReturnType<typeof setTimeout> | undefined;
    let highest = BigInt(latest.current.activityVersion); let attempts = 0; let isIdle = false;
    let isProtocolFailed = false;
    const isAvailable = () => document.visibilityState !== 'hidden' && navigator.onLine;
    function close() { source?.close(); source = undefined; if (timer) clearTimeout(timer); timer = undefined; }
    function observe(version: string) { highest = BigInt(version) > highest ? BigInt(version) : highest; }
    async function reconcile(): Promise<TaskPage | undefined> {
      const snapshot = await latest.current.refresh();
      if (disposed) return undefined;
      if (!snapshot) { close(); setNotice('进度快照未更新，请手动刷新'); return undefined; }
      observe(snapshot.activityVersion);
      return snapshot;
    }
    function connect(snapshot?: TaskPage) {
      if (disposed || source || !isAvailable() || !(snapshot?.hasActiveTasks ?? latest.current.hasActiveTasks)
        || isIdle || isProtocolFailed) return;
      observe(snapshot?.activityVersion ?? latest.current.activityVersion);
      const current = openTaskEventStream(); source = current; let connectionId: string | undefined;
      const valid = () => !disposed && source === current;
      function failProtocol() { close(); isProtocolFailed = true; setNotice('任务事件协议错误，请手动刷新'); }
      current.addEventListener('stream-ready', event => {
        if (!valid() || connectionId) return;
        try {
          const marker = taskStreamMarkerSchema.parse(JSON.parse((event as MessageEvent<string>).data));
          connectionId = marker.connectionId; observe(marker.activityVersion); attempts = 0;
          void reconcile();
        } catch { failProtocol(); }
      });
      current.addEventListener('task-updated', event => {
        if (!valid()) return;
        try {
          const update = taskUpdatedSchema.parse(JSON.parse((event as MessageEvent<string>).data));
          observe(update.activityVersion); latest.current.accept(update.task);
        } catch { failProtocol(); }
      });
      current.addEventListener('stream-idle', event => {
        if (!valid()) return;
        try {
          const marker = taskStreamMarkerSchema.parse(JSON.parse((event as MessageEvent<string>).data));
          observe(latest.current.activityVersion);
          if (marker.connectionId !== connectionId || BigInt(marker.activityVersion) < highest) return;
          close(); isIdle = true;
          void reconcile().then(snapshot => {
            if (!snapshot || disposed) return;
            isIdle = false; connect(snapshot);
          });
        } catch { failProtocol(); }
      });
      current.addEventListener('error', () => {
        if (!valid()) return;
        close(); setNotice('进度连接已中断，正在重新确认状态');
        timer = setTimeout(() => { void reconcile().then(snapshot => { if (snapshot) connect(snapshot); }); },
          Math.random() * Math.min(30000, 1000 * 2 ** attempts++));
      });
    }
    async function wake() {
      if (!isAvailable()) return;
      const snapshot = await reconcile();
      if (!snapshot || disposed) return;
      isIdle = false; isProtocolFailed = false; setNotice('');
      if (!snapshot.hasActiveTasks) close(); else connect(snapshot);
    }
    wakeRef.current = wake;
    function visibility() { if (isAvailable()) void wake(); else close(); }
    const online = () => { void wake(); };
    connect();
    document.addEventListener('visibilitychange', visibility); window.addEventListener('offline', close); window.addEventListener('online', online);
    const unsubscribe = subscribeTaskCreated(online);
    return () => { disposed = true; close(); unsubscribe(); document.removeEventListener('visibilitychange', visibility);
      window.removeEventListener('offline', close); window.removeEventListener('online', online); };
  }, [options.identity, options.hasActiveTasks]);
  return { notice, refresh: () => wakeRef.current() };
}
