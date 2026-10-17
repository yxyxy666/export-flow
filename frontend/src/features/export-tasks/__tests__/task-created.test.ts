// [规格](../../../../../specs/004-task-management/function-design.md)
// [AC-044](../../../../../docs/product/acceptance-criteria.md#ac-044)
// [AC-072](../../../../../docs/product/acceptance-criteria.md#ac-072)
import { afterEach, expect, it, vi } from 'vitest';
import { task } from './task-test-support';

class Channel extends EventTarget {
  static instances: Channel[] = [];
  messages: unknown[] = [];
  onmessage: ((event: MessageEvent) => void) | null = null;
  closed = false;
  constructor(readonly name: string) { super(); Channel.instances.push(this); }
  postMessage(data: unknown) { this.messages.push(data); }
  close() { this.closed = true; }
  receive(data: unknown) { const event = new MessageEvent('message', { data }); this.dispatchEvent(event); this.onmessage?.(event); }
}
afterEach(() => { vi.unstubAllGlobals(); vi.resetModules(); Channel.instances.length = 0; });
it('TC-403 创建通知本标签一次、跨标签校验、退订释放，不包含客户数据', async () => {
  vi.stubGlobal('BroadcastChannel', Channel);
  const path = '../../../shared/events/task-created';
  const notices = await import(/* @vite-ignore */ path);
  const received = vi.fn(); const unsubscribe = notices.subscribeTaskCreated(received);
  notices.notifyTaskCreated({ taskId: task.taskId, taskNo: task.taskNo, customerName: '不得发送' });
  expect(received).toHaveBeenCalledTimes(1);
  const channel = Channel.instances.find(item => item.messages.length)!;
  expect(channel.messages[0]).toEqual({ taskId: task.taskId, taskNo: task.taskNo });
  channel.receive({ taskId: 'invalid', taskNo: 'invalid' }); expect(received).toHaveBeenCalledTimes(1);
  channel.receive({ taskId: task.taskId, taskNo: task.taskNo }); expect(received).toHaveBeenCalledTimes(2);
  unsubscribe(); channel.receive({ taskId: task.taskId, taskNo: task.taskNo });
  expect(received).toHaveBeenCalledTimes(2); expect(channel.closed).toBe(true);
});
