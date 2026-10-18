// 设计：[HTTP边界](../../../../specs/002-order-management/contracts/http-api.md)。
// 验证：[API测试](../../features/orders/__tests__/order-api.test.ts)，TC-210/211。
import { ApiRequestError, normalizeApiError } from './api-error';

export interface RequestOptions {
  method?: 'GET' | 'POST' | 'DELETE';
  body?: unknown;
  headers?: Record<string, string>;
  signal?: AbortSignal;
  timeoutMs?: number;
}

/** 同源JSON请求；不自动重试，任何超时或取消均不能推断后台事务回滚。 */
export async function requestJson<T = unknown>(path: string, options: RequestOptions = {}): Promise<T> {
  const pathname = path.split('?')[0];
  if (!/^\/api\/v1\/[A-Za-z0-9/_-]+$/.test(pathname) || path.includes('#') || path.includes('\\')) {
    throw new ApiRequestError('protocol', {
      code: 'INVALID_API_PATH', message: '请求地址无效', traceId: '', outcome: 'REJECTED',
    });
  }
  const timeoutMs = options.timeoutMs ?? 10000;
  if (!Number.isFinite(timeoutMs) || timeoutMs <= 0) {
    throw new ApiRequestError('protocol', {
      code: 'INVALID_TIMEOUT', message: '请求配置无效', traceId: '', outcome: 'REJECTED',
    });
  }
  const controller = new AbortController();
  let isTimeout = false;
  const abort = () => controller.abort();
  let timer: ReturnType<typeof setTimeout> | undefined;
  try {
    if (options.signal?.aborted) {
      controller.abort();
      throw new DOMException('请求取消', 'AbortError');
    }
    options.signal?.addEventListener('abort', abort, { once: true });
    timer = setTimeout(() => { isTimeout = true; controller.abort(); }, timeoutMs);
    const headers = new Headers(options.headers);
    headers.set('Accept', 'application/json');
    if (options.body !== undefined) headers.set('Content-Type', 'application/json');
    const response = await fetch(path, {
      method: options.method ?? 'GET', headers,
      body: options.body === undefined ? undefined : JSON.stringify(options.body),
      signal: controller.signal, credentials: 'same-origin', cache: 'no-store', redirect: 'error',
    });
    let body: unknown;
    try { body = await response.json(); }
    catch {
      if (controller.signal.aborted) throw new DOMException('请求取消', 'AbortError');
      throw new ApiRequestError('protocol', {
        code: 'INVALID_RESPONSE', message: '响应无效，结果尚未确认', traceId: '', outcome: 'UNKNOWN',
      }, response.status);
    }
    if (!response.ok) throw normalizeApiError(body, response.status);
    return body as T;
  } catch (error) {
    if (controller.signal.aborted) {
      throw new ApiRequestError(isTimeout ? 'timeout' : 'aborted', {
        code: isTimeout ? 'REQUEST_TIMEOUT' : 'REQUEST_ABORTED',
        message: isTimeout ? '请求超时，结果尚未确认' : '请求已停止，后台结果尚未确认',
        traceId: '', outcome: 'UNKNOWN',
      });
    }
    throw normalizeApiError(error);
  } finally {
    if (timer !== undefined) clearTimeout(timer);
    options.signal?.removeEventListener('abort', abort);
  }
}
