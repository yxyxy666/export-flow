// 设计与验证：[任务规格](../../../../../specs/004-task-management/function-design.md)。
import { TASK_STATUSES, type TaskQuery, type TaskStatus } from '../types/export-task.types';

/** URL仅恢复合法的已提交条件，非法输入回到安全默认值。 */
export function parseTaskQuery(search: string): TaskQuery {
  const params = new URLSearchParams(search);
  const keyword = params.get('keyword')?.trim();
  const status = params.get('status');
  const page = params.get('page') ?? '1';
  const size = Number(params.get('pageSize') ?? 20);
  return { ...(keyword && [...keyword].length <= 100 ? { keyword } : {}),
    ...(TASK_STATUSES.includes(status as TaskStatus) ? { status: status as TaskStatus } : {}),
    page: /^[1-9]\d*$/.test(page) && Number(page) <= 2147483647 ? Number(page) : 1,
    pageSize: size === 50 || size === 100 ? size : 20 };
}
/** 只序列化提交后的非敏感筛选与分页。 */
export function writeTaskQuery(query: TaskQuery): string {
  const params = new URLSearchParams();
  if (query.keyword) params.set('keyword', query.keyword);
  if (query.status) params.set('status', query.status);
  params.set('page', String(query.page)); params.set('pageSize', String(query.pageSize));
  return params.toString();
}
