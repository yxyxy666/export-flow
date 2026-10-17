// 本次增量：[计划](../../../../../specs/005-task-resume-and-live-progress/plan.md)。
// 设计：[任务界面](../../../../../specs/004-task-management/plan.md)。验证：TC-401至TC-408、TC-417。
import { Alert, App, Button, Card, Pagination, Select, Space } from 'antd';
import { useTaskList } from '../hooks/use-task-list';
import { useTaskStream } from '../hooks/use-task-stream';
import { useTaskActions } from '../hooks/use-task-actions';
import { TaskFilter } from '../components/task-filter';
import { TaskTable } from '../components/task-table';
import type { TaskListItem } from '../types/export-task.types';

/** 页面组合查询、单通道进度和确认式暂停，不承担后台执行控制。 */
export function TaskManagementPage() {
  const list = useTaskList(); const { modal } = App.useApp();
  const stream = useTaskStream({ identity: list.identity,
    hasActiveTasks: list.snapshot?.hasActiveTasks ?? false, activityVersion: list.snapshot?.activityVersion ?? '0',
    refresh: list.refresh, accept: list.accept });
  const actions = useTaskActions(list.accept, () => { void stream.refresh(); });
  function confirmPause(task: TaskListItem) {
    modal.confirm({ title: '确认暂停任务？', content: `暂停 ${task.taskNo}，已登记的数据会保留。`,
      okText: '确认', cancelText: '取消', onOk: () => actions.pause(task) });
  }
  function confirmResume(task: TaskListItem) {
    modal.confirm({ title: '确认继续任务？', content: `继续 ${task.taskNo}，将先检查已保存的数据，仅重建不匹配的部分并重新计算进度。`,
      okText: '确认', cancelText: '取消', onOk: () => actions.resume(task) });
  }
  return <section className="task-management-page"><h1>任务管理</h1>
    <Card size="small"><TaskFilter query={list.query} onQuery={filter => list.setQuery({ ...list.query,
      keyword: filter.keyword || undefined, status: filter.status, page: 1 })}
      onReset={() => list.setQuery({ page: 1, pageSize: 20 })} /></Card>
    {list.error && <Alert type="error" title={list.snapshot ? '刷新失败，列表尚未更新' : '任务查询失败，请刷新重试'} />}
    {stream.notice && <Alert type="warning" title={stream.notice} />}
    {actions.error && <Alert type="error" title={actions.error} />}
    {actions.operation?.state === 'UNKNOWN' && <Alert type="warning" title={actions.operation.message}
      action={<Button onClick={() => void actions.retry()}>找回结果</Button>} />}
    <Card size="small" title="导出任务" extra={<Button loading={list.isLoading} onClick={() => void stream.refresh()}>刷新</Button>}>
      <TaskTable rows={list.snapshot?.items ?? []} isBusy={!!actions.operation} onPause={confirmPause} onResume={confirmResume} />
      <Space wrap style={{ display: 'flex', justifyContent: 'flex-end', marginTop: 16 }}>
        <span>共 {list.snapshot?.total ?? '0'} 条</span>
        <Pagination current={list.query.page} pageSize={list.query.pageSize}
          total={Number(BigInt(list.snapshot?.total ?? '0') > BigInt(Number.MAX_SAFE_INTEGER)
            ? BigInt(Number.MAX_SAFE_INTEGER) : BigInt(list.snapshot?.total ?? '0'))} showSizeChanger={false}
          onChange={page => list.setQuery({ ...list.query, page })} />
        <Select aria-label="每页条数" value={list.query.pageSize} virtual={false}
          options={[20, 50, 100].map(value => ({ value, label: `${value} 条/页` }))}
          onChange={(pageSize: 20 | 50 | 100) => list.setQuery({ ...list.query, page: 1, pageSize })} />
      </Space>
    </Card>
  </section>;
}
