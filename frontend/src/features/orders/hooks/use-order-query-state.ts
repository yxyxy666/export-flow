// 设计：[HTTP状态契约](../../../../../specs/002-order-management/contracts/http-api.md)。测试：TC-206/211。
import { useEffect, useMemo, useRef, useState } from 'react';
import { useLocation, useNavigate, useNavigationType } from 'react-router-dom';
import type { OrderQuery } from '../types/order.types';
import { parseOrderQuery, serializeOrderQuery } from '../models/order-query';

/** 安全URL与内存关键字分开；POP在同次渲染清关键字，避免发出旧敏感条件请求。 */
export function useOrderQueryState() {
  const location = useLocation(); const navigate = useNavigate(); const navigationType = useNavigationType();
  const memoryKeyword = useRef<string | undefined>(undefined);
  const pendingQuery = useRef<OrderQuery | undefined>(undefined);
  const locationKey = useRef(location.key);
  const revision = useRef(0);
  const [keywordVersion, setKeywordVersion] = useState(0);
  const [notice, setNotice] = useState('');
  if (locationKey.current !== location.key) {
    locationKey.current = location.key;
    pendingQuery.current = undefined;
    if (navigationType === 'POP') { memoryKeyword.current = undefined; revision.current++; }
  }
  const safeQuery = useMemo(() => parseOrderQuery(new URLSearchParams(location.search)), [location.search]);
  const query = useMemo(() => pendingQuery.current ?? ({ ...safeQuery,
    filter: { ...safeQuery.filter, ...(memoryKeyword.current ? { keyword: memoryKeyword.current } : {}) } }),
    // 导航key也必须参与，POP到相同安全URL时仍丢弃关键字。
    [safeQuery, keywordVersion, location.key]);
  useEffect(() => {
    const params = new URLSearchParams(location.search);
    const normalized = serializeOrderQuery(safeQuery);
    const allowed = new Set(['orderNo','status','channel','createdFrom','createdTo','minAmount','maxAmount','page','pageSize','sortDirection']);
    const isInvalid = [...params.keys()].some(name => !allowed.has(name) || params.getAll(name).length !== 1) ||
      [...params.entries()].some(([name, value]) => name !== 'keyword' &&
        normalized.get(name) !== value && !((name === 'page' && value === '1') ||
        (name === 'pageSize' && value === '20') || (name === 'sortDirection' && value === 'DESC')));
    if (params.has('keyword') || revision.current > 0) setNotice('关键字需重新输入');
    if (isInvalid) {
      setNotice(params.has('keyword') ? '无效条件已恢复，关键字需重新输入' : '无效查询条件已恢复');
      void navigate({ search: normalized.toString() }, { replace: true });
    }
  }, [location.search, location.key, navigate, safeQuery]);
  function setQuery(next: OrderQuery, mode: 'push' | 'replace' = 'push') {
    pendingQuery.current = next;
    memoryKeyword.current = next.filter.keyword;
    setKeywordVersion(value => value + 1);
    void navigate({ search: serializeOrderQuery(next).toString() }, { replace: mode === 'replace' });
  }
  return { query, setQuery, notice, historyRevision: revision.current };
}
