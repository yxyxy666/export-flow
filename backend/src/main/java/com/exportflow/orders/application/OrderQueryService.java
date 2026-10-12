// 规格互链：[计划](../../../../../../../../specs/002-order-management/plan.md)；[任务与完整映射](../../../../../../../../specs/002-order-management/tasks.md)；验证：[OrderQueryBoundaryTest.java](../../../../../../test/java/com/exportflow/orders/OrderQueryBoundaryTest.java)、[OrderQueryMySqlTest.java](../../../../../../test/java/com/exportflow/orders/OrderQueryMySqlTest.java)。
package com.exportflow.orders.application;

import com.exportflow.orders.domain.Order;
import com.exportflow.orders.domain.OrderQuery;
import com.exportflow.orders.port.OrderRepository;
import com.exportflow.shared.domain.PageSlice;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** 短只读事务复用统一筛选；设计 function-design-reference.md，验证 TC-215。 */
@Service
public class OrderQueryService {
  private final OrderRepository orders;

  public OrderQueryService(OrderRepository orders) {
    this.orders = orders;
  }

  @Transactional(readOnly = true)
  public PageSlice<Order> query(OrderQuery query) {
    query.validate();
    long count = orders.count(query.filter());
    if (count > 9007199254740991L) throw new IllegalStateException("分页计数超出协议范围");
    return new PageSlice<>(orders.findPage(query), query.page(), query.pageSize(), count);
  }
}
