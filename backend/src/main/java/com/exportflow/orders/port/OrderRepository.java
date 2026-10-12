// 增量实施：[计划](../../../../../../../../specs/003-select-all-filtered/plan.md)、[任务](../../../../../../../../specs/003-select-all-filtered/tasks.md)；[测试](../../../../../../test/java/com/exportflow/exporttasks/ExportExclusionMySqlTest.java)。
// 规格互链：[计划](../../../../../../../../specs/002-order-management/plan.md)；[任务与完整映射](../../../../../../../../specs/002-order-management/tasks.md)；验证：[OrderQueryBoundaryTest.java](../../../../../../test/java/com/exportflow/orders/OrderQueryBoundaryTest.java)、[OrderQueryMySqlTest.java](../../../../../../test/java/com/exportflow/orders/OrderQueryMySqlTest.java)。
package com.exportflow.orders.port;

import com.exportflow.exporttasks.domain.ExportScope;
import com.exportflow.orders.domain.Order;
import com.exportflow.orders.domain.OrderQuery;
import com.exportflow.orders.domain.OrderQuery.*;
import com.exportflow.orders.domain.OrderQuery.OrderFilter;
import java.util.List;
import java.util.UUID;

/** 订单读取端口；设计 function-design-reference.md，验证 TC-215。 */
public interface OrderRepository {
  long count(OrderFilter filter);

  List<Order> findPage(OrderQuery query);

  long countExisting(List<Long> orderIds);

  /** 计算排除项与当前筛选的交集，不写入来源订单。 */
  long countMatching(OrderFilter filter, List<Long> orderIds);

  OrderBatch readBatch(UUID taskId, ExportScope scope, OrderCursor after, int limit);
}
