// 设计：[函数清单](../../../../specs/002-order-management/function-design-reference.md)。测试：TC-204。
/** 香港本地秒级输入严格转换为UTC，不依赖机器时区。 */
export function toUtcInstant(local: string): string {
  if (!/^\d{4}-\d{2}-\d{2} \d{2}:\d{2}:\d{2}$/.test(local)) throw new Error('时间格式无效');
  const milliseconds = Date.parse(`${local.replace(' ', 'T')}+08:00`);
  if (!Number.isFinite(milliseconds)) throw new Error('时间无效');
  const value = new Date(milliseconds).toISOString().replace('.000Z', 'Z');
  if (formatHongKongDateTime(value) !== local) throw new Error('日历时间不存在');
  return value;
}
/** 按固定香港时区展示UTC协议时间。 */
export function formatHongKongDateTime(utc: string): string {
  if (!/^\d{4}-\d{2}-\d{2}T\d{2}:\d{2}:\d{2}Z$/.test(utc)) throw new Error('协议时间无效');
  const date = new Date(utc);
  if (!Number.isFinite(date.getTime()) || date.toISOString().replace('.000Z', 'Z') !== utc) {
    throw new Error('协议时间无效');
  }
  return new Date(date.getTime() + 8 * 3600000).toISOString().slice(0, 19).replace('T', ' ');
}
