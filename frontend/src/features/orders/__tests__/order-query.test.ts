// 规格与进度：[任务记录](../../../../../specs/002-order-management/tasks.md#测试进度与证据)
import { describe, expect, it } from 'vitest';
import { normalizeOrderFilter, parseOrderQuery, serializeOrderQuery } from '../models/order-query';
import { toOrderViewModel } from '../models/order-view-model';
import { normalizeMoney } from '../../../shared/formatters/money';
import { formatHongKongDateTime, toUtcInstant } from '../../../shared/formatters/date-time';
import { orders } from '../../../test/handlers';

// 生产模块尚不存在时本套件导入阻塞；不得将导入错误当作业务红灯。
// [AC-005](../../../../../docs/product/acceptance-criteria.md#ac-005)
// [AC-021](../../../../../docs/product/acceptance-criteria.md#ac-021)
// [AC-022](../../../../../docs/product/acceptance-criteria.md#ac-022)
// [AC-023](../../../../../docs/product/acceptance-criteria.md#ac-023)
describe('TC-204 查询规范化与展示', () => {
  it('裁剪订单号而保留大小写，移除空条件且不改变keyword原意', () => {
    expect(normalizeOrderFilter({ orderNo: ' Ord-001 ', keyword: ' Test ', status: undefined }))
      .toMatchObject({ orderNo: 'Ord-001', keyword: 'Test' });
    expect(normalizeOrderFilter({ orderNo: ' ', keyword: '' })).toEqual({});
  });
  it('香港秒级边界转UTC，结果与宿主时区无关', () => {
    expect(toUtcInstant('2026-10-07 08:00:00')).toBe('2026-10-07T00:00:00Z');
    expect(toUtcInstant('2026-10-07 00:00:00')).toBe('2026-10-06T16:00:00Z');
    expect(formatHongKongDateTime('2026-10-07T00:00:00Z')).toBe('2026-10-07 08:00:00');
  });
  it.each(['2026-02-30 08:00:00', '2026-13-01 00:00:00', '2026-10-07 25:00:00', 'not-a-date'])
    ('不存在的日历时间%s必须拒绝', value => expect(() => toUtcInstant(value)).toThrow());
  it('时间单边和相等边界合法，逆序必须拒绝', () => {
    expect(normalizeOrderFilter({ createdFromLocal: '2026-10-07 08:00:00' }))
      .toEqual({ createdFrom: '2026-10-07T00:00:00Z' });
    expect(() => normalizeOrderFilter({ createdFromLocal: '2026-10-07 08:00:00',
      createdToLocal: '2026-10-07 08:00:00' })).not.toThrow();
    expect(() => normalizeOrderFilter({ createdFromLocal: '2026-10-08 08:00:00',
      createdToLocal: '2026-10-07 08:00:00' })).toThrow();
  });
  it.each([['0001.2', '1.20'], ['0', '0.00'], ['9999999999999999.99', '9999999999999999.99']])
    ('金额%s定点归一为%s而不转Number', (raw, expected) => expect(normalizeMoney(raw)).toBe(expected));
  it.each(['-1', '1.001', '1e3', 'NaN', '10000000000000000.00'])
    ('非法金额%s必须拒绝', raw => expect(() => normalizeMoney(raw)).toThrow());
  it('按十进制判断范围，不按字符串排序；相等边界可用', () => {
    expect(() => normalizeOrderFilter({ minAmount: '2', maxAmount: '10' })).not.toThrow();
    expect(() => normalizeOrderFilter({ minAmount: '10', maxAmount: '2' })).toThrow();
    expect(normalizeOrderFilter({ minAmount: '2', maxAmount: '2.00' }))
      .toMatchObject({ minAmount: '2.00', maxAmount: '2.00' });
  });
  it('大ID保持字符串，未知状态展示未知，客户原值和前导零保持', () => {
    const model = toOrderViewModel({ ...orders[0], status: 'FUTURE_STATUS', channel: 'FUTURE_CHANNEL' });
    expect(model.key).toBe('9007199254740993');
    expect(Object.values(model).filter(value => value === '未知')).toHaveLength(2);
    expect(Object.values(model)).toContain('0013800000001');
    expect(Object.values(model)).toContain('=测试客户');
    expect(Object.values(model)).toContain('2026-10-07 08:00:00');
  });
});
// [AC-065](../../../../../docs/product/acceptance-criteria.md#ac-065)
describe('TC-206 安全URL与恢复', () => {
  it('只恢复白名单，关键字永不从URL读取或再次序列化', () => {
    const query = parseOrderQuery(new URLSearchParams('keyword=客户资料&orderNo=ORD-001&page=2&pageSize=50&sortDirection=ASC'));
    expect(query).toMatchObject({ page: 2, pageSize: 50, sortDirection: 'ASC', filter: { orderNo: 'ORD-001' } });
    expect(query.filter.keyword).toBeUndefined();
    const encoded = serializeOrderQuery({ ...query, filter: { ...query.filter, keyword: '内存客户' } });
    expect(encoded.has('keyword')).toBe(false);
    expect(encoded.get('orderNo')).toBe('ORD-001');
  });
  it.each(['page=-1', 'page=1.5', 'page=1000001', 'pageSize=30', 'sortDirection=RANDOM',
    'status=UNKNOWN', 'channel=UNKNOWN', 'minAmount=NaN', 'createdFrom=not-a-date'])
    ('非法项%s安全恢复默认', invalid => {
      const query = parseOrderQuery(new URLSearchParams(invalid));
      expect(query).toEqual({ filter: {}, page: 1, pageSize: 20, sortDirection: 'DESC' });
    });
  it('安全条件往返稳定，URL不含内存关键字', () => {
    const query = { filter: { orderNo: 'ORD-001', minAmount: '0.00', createdFrom: '2026-10-07T00:00:00Z' },
      page: 3, pageSize: 100 as const, sortDirection: 'ASC' as const };
    expect(parseOrderQuery(serializeOrderQuery(query))).toEqual(query);
  });
});
