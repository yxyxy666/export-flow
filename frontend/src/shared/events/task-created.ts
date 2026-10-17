// 设计：[任务创建通知](../../../../specs/004-task-management/function-design.md)。
import { z } from 'zod';
const noticeSchema = z.object({ taskId: z.uuid(), taskNo: z.string().min(1).max(128) });
type TaskCreated = z.infer<typeof noticeSchema>;
const listeners = new Set<(notice: TaskCreated) => void>();
let channel: BroadcastChannel | undefined;
/** 仅广播任务标识，既不持久化请求，也不包含订单或客户信息。 */
export function notifyTaskCreated(input: TaskCreated): void {
  const parsed = noticeSchema.safeParse(input);
  if (!parsed.success) return;
  listeners.forEach(listener => listener(parsed.data));
  if (channel) channel.postMessage(parsed.data);
  else if (typeof BroadcastChannel !== 'undefined') {
    const publisher = new BroadcastChannel('export-flow:task-created');
    publisher.postMessage(parsed.data); publisher.close();
  }
}
/** 首个订阅创建跨标签通道，末个退订释放监听与通道。 */
export function subscribeTaskCreated(listener: (notice: TaskCreated) => void): () => void {
  listeners.add(listener);
  if (!channel && typeof BroadcastChannel !== 'undefined') {
    channel = new BroadcastChannel('export-flow:task-created');
    channel.onmessage = event => {
      const parsed = noticeSchema.safeParse(event.data);
      if (parsed.success) listeners.forEach(item => item(parsed.data));
    };
  }
  return () => {
    listeners.delete(listener);
    if (!listeners.size && channel) { channel.onmessage = null; channel.close(); channel = undefined; }
  };
}
