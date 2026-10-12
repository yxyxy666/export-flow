// 设计：[函数与错误边界](../../../../specs/002-order-management/function-design-reference.md#frontendsrcsharedapiapi-errorts)。
// 验证：[API测试](../../features/orders/__tests__/order-api.test.ts)，TC-210/211。
export interface ApiError {
  code: string;
  message: string;
  traceId: string;
  outcome: 'REJECTED' | 'UNKNOWN';
  details?: { currentCount?: number; limit?: number };
}
export type ApiErrorKind = 'http' | 'network' | 'timeout' | 'aborted' | 'protocol';

/** 仅保存安全错误，禁止携带原始响应、请求、幂等键或底层堆栈。 */
export class ApiRequestError extends Error {
  constructor(public readonly kind: ApiErrorKind, public readonly error: ApiError,
    public readonly status?: number) {
    super(error.message);
    this.name = 'ApiRequestError';
  }
}

const safeErrors: Record<string, { statuses: number[]; message: string }> = {
  VALIDATION_ERROR: { statuses: [400], message: '输入有误，请检查筛选或导出配置' },
  EMPTY_EXPORT_SCOPE: { statuses: [400], message: '导出范围为空' },
  INVALID_EXPORT_FIELDS: { statuses: [400], message: '请至少选择一个有效字段' },
  IDEMPOTENCY_KEY_REUSED: { statuses: [409], message: '提交标识与请求不一致，请重新配置' },
  REQUEST_TOO_LARGE: { statuses: [413], message: '请求过大，请缩小导出范围' },
  EXPORT_LIMIT_EXCEEDED: { statuses: [422], message: '导出数量超过上限，请缩小范围' },
  ORDER_SELECTION_INVALID: { statuses: [422], message: '已选订单无效，请重新选择' },
  DEPENDENCY_UNAVAILABLE: { statuses: [503], message: '服务暂时不可用，请稍后重试' },
  CREATE_OUTCOME_UNKNOWN: { statuses: [503], message: '创建结果尚未确认，请用原请求重试确认' },
  INTERNAL_ERROR: { statuses: [500], message: '服务暂时异常，请稍后重试' },
};

/** 仅可信协议和状态组合可以宣告明确拒绝；错误文案使用本地安全映射。 */
export function normalizeApiError(error: unknown, status?: number): ApiRequestError {
  if (error instanceof ApiRequestError) return error;
  const body = error !== null && typeof error === 'object' ? error as Record<string, unknown> : {};
  const code = typeof body.code === 'string' ? body.code : '';
  const known = Object.hasOwn(safeErrors, code) ? safeErrors[code] : undefined;
  const isTrusted = !!known && status !== undefined && known.statuses.includes(status) &&
    typeof body.message === 'string' && typeof body.traceId === 'string' &&
    /^[A-Za-z0-9_-]{1,128}$/.test(body.traceId) &&
    (body.outcome === 'REJECTED' || body.outcome === 'UNKNOWN');
  const outcome = isTrusted && body.outcome === 'REJECTED' && code !== 'CREATE_OUTCOME_UNKNOWN'
    ? 'REJECTED' : 'UNKNOWN';
  const result: ApiError = {
    code: isTrusted ? code : 'REQUEST_OUTCOME_UNKNOWN',
    message: isTrusted ? known.message : '请求结果尚未确认，请稍后重试',
    traceId: isTrusted ? body.traceId as string : '', outcome,
  };
  if (isTrusted && body.details && typeof body.details === 'object') {
    const details = body.details as Record<string, unknown>;
    if (typeof details.currentCount === 'number' && Number.isSafeInteger(details.currentCount) &&
      details.currentCount >= 0 && details.limit === 200000) {
      result.details = { currentCount: details.currentCount, limit: 200000 };
    }
  }
  return new ApiRequestError(status === undefined ? 'network' : 'http', result, status);
}
