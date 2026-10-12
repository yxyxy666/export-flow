// 设计：[函数清单](../../../../specs/002-order-management/function-design-reference.md)。测试：TC-204。
/** 字符串规范化非负定点金额，保留最大16位整数精度。 */
export function normalizeMoney(input: string): string {
  const match = /^(\d+)(?:\.(\d{1,2}))?$/.exec(input.trim());
  if (!match) throw new Error('金额格式无效');
  const whole = match[1].replace(/^0+(?=\d)/, '');
  if (whole.length > 16) throw new Error('金额超出范围');
  return `${whole}.${(match[2] ?? '').padEnd(2, '0')}`;
}
/** 金额展示不经过Number转换。 */
export function formatMoney(amount: string, currency: 'CNY' = 'CNY'): string {
  const [whole, fraction] = normalizeMoney(amount).split('.');
  return `${currency} ${whole.replace(/\B(?=(\d{3})+(?!\d))/g, ',')}.${fraction}`;
}
