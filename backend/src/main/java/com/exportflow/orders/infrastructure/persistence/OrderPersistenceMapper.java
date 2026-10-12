// 规格互链：[计划](../../../../../../../../../specs/002-order-management/plan.md)；[任务与完整映射](../../../../../../../../../specs/002-order-management/tasks.md)；验证：[OrderQueryBoundaryTest.java](../../../../../../../test/java/com/exportflow/orders/OrderQueryBoundaryTest.java)、[OrderQueryMySqlTest.java](../../../../../../../test/java/com/exportflow/orders/OrderQueryMySqlTest.java)。
package com.exportflow.orders.infrastructure.persistence;

import com.exportflow.orders.domain.Order;
import java.time.ZoneOffset;
import org.springframework.stereotype.Component;

/** 数据库记录和领域隔离；设计 data-model.md，验证 TC-215。 */
@Component
public class OrderPersistenceMapper {
  public Order toDomain(OrderRecord record) {
    return new Order(
        record.id(),
        record.orderNo(),
        record.customerName(),
        record.customerPhone(),
        record.status(),
        record.channel(),
        record.amount(),
        record.currency(),
        record.region(),
        record.createdAt().toInstant(ZoneOffset.UTC));
  }
}
