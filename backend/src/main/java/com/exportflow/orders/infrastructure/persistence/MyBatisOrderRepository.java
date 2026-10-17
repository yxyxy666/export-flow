// 本期实施与验证：[任务记录](../../../../../../../../../specs/004-task-management/tasks.md)。
// 增量实施：[计划](../../../../../../../../../specs/003-select-all-filtered/plan.md)、[任务](../../../../../../../../../specs/003-select-all-filtered/tasks.md)；[测试](../../../../../../../test/java/com/exportflow/exporttasks/ExportExclusionMySqlTest.java)。
// 规格互链：[计划](../../../../../../../../../specs/002-order-management/plan.md)；[任务与完整映射](../../../../../../../../../specs/002-order-management/tasks.md)；验证：[OrderQueryBoundaryTest.java](../../../../../../../test/java/com/exportflow/orders/OrderQueryBoundaryTest.java)、[OrderQueryMySqlTest.java](../../../../../../../test/java/com/exportflow/orders/OrderQueryMySqlTest.java)。
package com.exportflow.orders.infrastructure.persistence;

import com.exportflow.exporttasks.domain.ExportScope;
import com.exportflow.orders.domain.Order;
import com.exportflow.orders.domain.OrderQuery;
import com.exportflow.orders.domain.OrderQuery.*;
import com.exportflow.orders.domain.OrderQuery.OrderFilter;
import com.exportflow.orders.port.OrderRepository;
import java.util.List;
import java.util.UUID;
import org.springframework.stereotype.Repository;

/** 有界读取和独立领域映射；设计 function-design-reference.md，验证 TC-215。 */
@Repository
public class MyBatisOrderRepository implements OrderRepository {
  private final OrderMapper mapper;
  private final OrderPersistenceMapper converter;

  public MyBatisOrderRepository(OrderMapper mapper, OrderPersistenceMapper converter) {
    this.mapper = mapper;
    this.converter = converter;
  }

  public long count(OrderFilter filter) {
    filter.validate();
    return mapper.count(filter);
  }

  public List<Order> findPage(OrderQuery query) {
    query.validate();
    return mapper.findPage(query).stream().map(converter::toDomain).toList();
  }

  public long countExisting(List<Long> ids) {
    long count = 0;
    for (int start = 0; start < ids.size(); start += 1000)
      count += mapper.countExisting(ids.subList(start, Math.min(start + 1000, ids.size())));
    return count;
  }

  public OrderBatch readBatch(UUID taskId, ExportScope scope, OrderCursor after, int limit) {
    scope.validate();
    if (limit < 1 || limit > 1000) throw new IllegalArgumentException("读取批量无效");
    var rows =
        mapper.readBatch(taskId.toString(), scope, after, limit).stream()
            .map(converter::toDomain)
            .toList();
    var last =
        rows.isEmpty() ? after : new OrderCursor(rows.getLast().createdAt(), rows.getLast().id());
    return new OrderBatch(rows, last, rows.size() < limit);
  }

  /** 严格限定原scope与分段上下界，多取一行用于发现源成员增长。 */
  public List<Order> readSegment(
      UUID taskId, ExportScope scope, OrderCursor previous, OrderCursor last, int limit) {
    scope.validate();
    if (last == null
        || limit < 1
        || limit > 1001
        || previous != null
            && (last.createdAt().isAfter(previous.createdAt())
                || last.createdAt().equals(previous.createdAt()) && last.id() >= previous.id()))
      throw new IllegalArgumentException("分段范围无效");
    return mapper.readSegment(taskId.toString(), scope, previous, last, limit).stream()
        .map(converter::toDomain)
        .toList();
  }

  /** 每批最多1000个占位符，空排除不发SQL。 */
  public long countMatching(OrderFilter filter, List<Long> ids) {
    filter.validate();
    long count = 0;
    for (int start = 0; start < ids.size(); start += 1000)
      count += mapper.countMatching(filter, ids.subList(start, Math.min(start + 1000, ids.size())));
    return count;
  }
}
