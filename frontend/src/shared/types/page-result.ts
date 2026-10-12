// 设计与测试：[任务记录](../../../../specs/002-order-management/tasks.md)。
/** 服务端分页结果；total必须是安全整数，不截断64位计数。 */
export interface PageResult<T> {
  items: T[];
  page: number;
  pageSize: 20 | 50 | 100;
  total: number;
}
