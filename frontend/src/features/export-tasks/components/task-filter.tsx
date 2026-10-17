// 设计与验证：[任务界面](../../../../../specs/004-task-management/function-design.md)。
import { useEffect } from 'react';
import { Button, Input, Select, Space } from 'antd';
import { Controller, useForm } from 'react-hook-form';
import { TASK_STATUS_LABELS } from '../models/task-list-model';
import type { TaskQuery, TaskStatus } from '../types/export-task.types';
type Filter = { keyword: string; status?: TaskStatus };
/** 编辑态由RHF独立管理，仅提交合法条件后改变查询。 */
export function TaskFilter({ query, onQuery, onReset }: { query: TaskQuery; onQuery(filter: Filter): void; onReset(): void }) {
  const { control, handleSubmit, reset, setError, formState: { errors } } = useForm<Filter>({
    defaultValues: { keyword: query.keyword ?? '', status: query.status },
  });
  useEffect(() => reset({ keyword: query.keyword ?? '', status: query.status }), [query, reset]);
  return <form onSubmit={handleSubmit(values => {
    const keyword = values.keyword.trim();
    if ([...keyword].length > 100) { setError('keyword', { message: '关键字最多100个字符' }); return; }
    onQuery({ keyword, status: values.status });
  })}><Space wrap align="end">
    <div className="task-filter-field"><label htmlFor="task-keyword">关键字</label><Controller name="keyword" control={control}
      render={({ field }) => <Input {...field} id="task-keyword" placeholder="任务编号或文件名" aria-invalid={!!errors.keyword}
        aria-describedby={errors.keyword ? 'task-keyword-error' : undefined} />} />
      {errors.keyword && <span role="alert" id="task-keyword-error">{errors.keyword.message}</span>}</div>
    <div className="task-filter-field"><label htmlFor="task-status">状态</label><Controller name="status" control={control}
      render={({ field }) => <Select {...field} id="task-status" aria-label="状态" allowClear virtual={false}
        style={{ minWidth: 130 }} options={Object.entries(TASK_STATUS_LABELS).map(([value, label]) => ({ value, label }))} />} /></div>
    <Button type="primary" htmlType="submit">查询</Button><Button onClick={onReset}>重置</Button>
  </Space></form>;
}
