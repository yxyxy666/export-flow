// 设计与验证：[列表与事件衔接](../../../../../specs/004-task-management/function-design.md)。
import { useCallback, useEffect, useMemo, useRef } from 'react';
import { keepPreviousData, useQuery, useQueryClient } from '@tanstack/react-query';
import { useSearchParams } from 'react-router-dom';
import { queryExportTasks } from '../../../shared/api/api';
import { parseTaskQuery, writeTaskQuery } from '../models/task-query';
import { affectsTaskMembership, mergeTaskEvent } from '../models/task-list-model';
import type { TaskListItem, TaskPage, TaskQuery } from '../types/export-task.types';

/** 查询缓存只存展示快照；在途响应必须先按本代已见版本对账。 */
export function useTaskList() {
  const [search, setSearch] = useSearchParams();
  const identity = writeTaskQuery(parseTaskQuery(search.toString()));
  const query = useMemo(() => parseTaskQuery(identity), [identity]);
  const key = useMemo(() => ['export-tasks', identity] as const, [identity]);
  const client = useQueryClient();
  const current = useRef(identity); current.current = identity;
  const events = useRef({ identity, seen: new Map<string, TaskListItem>(), dirty: false });
  if (events.current.identity !== identity) events.current = { identity, seen: new Map(), dirty: false };
  const result = useQuery({ queryKey: key, placeholderData: keepPreviousData,
    queryFn: async ({ signal }) => {
      const snapshot = await queryExportTasks(query, signal);
      if (current.current !== identity) return snapshot;
      return { ...snapshot, items: snapshot.items.map(item => mergeTaskEvent(item, events.current.seen.get(item.taskId) ?? item))
        .filter(item => !query.status || item.status === query.status) };
    } });
  const refresh = useCallback(async (): Promise<TaskPage | undefined> => {
    const response = await result.refetch();
    return response.isSuccess ? response.data : undefined;
  }, [result.refetch]);
  const accept = useCallback((next: TaskListItem) => {
    const cached = client.getQueryData<TaskPage>(key);
    const previous = events.current.seen.get(next.taskId) ?? cached?.items.find(item => item.taskId === next.taskId);
    if (previous && BigInt(previous.version) >= BigInt(next.version)) return;
    events.current.seen.set(next.taskId, next);
    client.setQueryData<TaskPage>(key, old => old ? { ...old, items: old.items
      .map(item => item.taskId === next.taskId ? next : item)
      .filter(item => !query.status || item.status === query.status) } : old);
    if (affectsTaskMembership(previous, next, query)) {
      if (client.isFetching({ queryKey: key })) events.current.dirty = true;
      else void refresh();
    }
  }, [client, key, query, refresh]);
  useEffect(() => {
    if (!result.isFetching && events.current.dirty) { events.current.dirty = false; void refresh(); }
  }, [result.isFetching, refresh]);
  function setQuery(next: TaskQuery) {
    if (writeTaskQuery(next) === identity) void refresh();
    else setSearch(writeTaskQuery(next));
  }
  useEffect(() => {
    if (!result.data || result.isPlaceholderData || result.isFetching || result.data.items.length) return;
    const last = (BigInt(result.data.total) + BigInt(query.pageSize) - 1n) / BigInt(query.pageSize);
    if (last > 0n && BigInt(query.page) > last) setSearch(writeTaskQuery({ ...query, page: Number(last) }), { replace: true });
  }, [result.data, result.isPlaceholderData, result.isFetching, query, setSearch]);
  return { query, identity, snapshot: result.data, isLoading: result.isFetching, error: result.error,
    refresh, accept, setQuery };
}
