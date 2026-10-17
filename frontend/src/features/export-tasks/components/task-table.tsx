// 设计与验证：[修复展示](../../../../../specs/004-task-management/contracts/assembly-repair.md)。
import { Button, Progress, Space, Table, Tooltip, theme } from 'antd';
import { getTaskActions } from '../models/task-list-model';
import { formatHongKongDateTime } from '../../../shared/formatters/date-time';
import type { TaskListItem } from '../types/export-task.types';
import { exportTaskFileUrl } from '../../../shared/api/api';

/** 提示仅由持久摘要决定；阶段完成前不因计数回满提前移除。 */
function repairHint(task: TaskListItem): string | undefined {
  if (task.status === 'SUCCEEDED' || task.status === 'FAILED') return;
  if (task.repairState === 'PAUSED') return '损坏文件重建已暂停';
  if (task.repairState === 'PAUSING') return '正在暂停损坏文件重建';
  if (task.repairState === 'REBUILDING') return '正在重建损坏文件';
  if (task.repairState === 'CHECKING' || task.executionPhase === 'CHECKING') return '正在校验文件';
}
/** 表格只呈现ViewModel与已校验动作，下载路径使用既有同源端点。 */
export function TaskTable({ rows, isBusy, onPause }: { rows: TaskListItem[]; isBusy: boolean; onPause(task: TaskListItem): void }) {
  const { token } = theme.useToken();
  return <Table<TaskListItem> rowKey="taskId" dataSource={rows} pagination={false}
    locale={{ emptyText: '暂无匹配任务' }} scroll={{ x: 1350 }} columns={[
      { title: '任务编号', dataIndex: 'taskNo', width: 180, fixed: 'left' },
      { title: '文件名', dataIndex: 'fileName', width: 310, ellipsis: true },
      { title: '状态', width: 100, render: (_, task) => getTaskActions(task).statusLabel },
      { title: '进度', width: 200, render: (_, task) => {
        const hint = repairHint(task);
        return <Space><div style={{ width: 135 }}><Progress percent={task.progressPercent} size="small"
          status={task.status === 'FAILED' ? 'exception' : task.status === 'SUCCEEDED' ? 'success' : 'normal'}
          format={() => `${task.progressPercent}%`} /></div>
          {hint && <Tooltip title={hint} trigger={['hover', 'focus']}><button type="button" aria-label={hint}
            className="task-repair-info" style={{ color: token.colorWarning, border: 0, background: 'none', padding: 3 }}>
            <svg width="16" height="16" viewBox="0 0 16 16" aria-hidden="true" fill="none" stroke="currentColor">
              <circle cx="8" cy="8" r="6.5" /><path d="M8 7v5M8 4v1" strokeWidth="1.5" /></svg></button></Tooltip>}</Space>;
      } },
      { title: '已处理/总数', width: 130, render: (_, task) => `${task.processedCount} / ${task.totalCount}` },
      { title: '创建时间（香港）', width: 180, render: (_, task) => formatHongKongDateTime(task.createdAt) },
      { title: '结束时间（香港）', width: 180, render: (_, task) => task.finishedAt ? formatHongKongDateTime(task.finishedAt) : '—' },
      { title: '文件大小', width: 120, render: (_, task) => task.fileSize === null ? '—' : `${task.fileSize} 字节` },
      { title: '失败说明', width: 200, render: (_, task) => task.status === 'FAILED' ? task.errorSummary ?? '任务执行失败' : '—' },
      { title: '操作', width: 100, fixed: 'right', render: (_, task) => {
        const actions = getTaskActions(task);
        return actions.canPause ? <Button size="small" disabled={isBusy} onClick={() => onPause(task)}>暂停</Button>
          : actions.canDownload ? <a href={exportTaskFileUrl(task.taskId)}>下载</a> : <span>{actions.unavailableReason}</span>;
      } },
    ]} />;
}
