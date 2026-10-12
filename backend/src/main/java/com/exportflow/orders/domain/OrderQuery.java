// 规格互链：[计划](../../../../../../../../specs/002-order-management/plan.md)；[任务与完整映射](../../../../../../../../specs/002-order-management/tasks.md)；验证：[CreationInputBoundaryTest.java](../../../../../../test/java/com/exportflow/exporttasks/CreationInputBoundaryTest.java)、[ExportCreationContractTest.java](../../../../../../test/java/com/exportflow/exporttasks/ExportCreationContractTest.java)、[OrderApiContractTest.java](../../../../../../test/java/com/exportflow/orders/OrderApiContractTest.java)。
package com.exportflow.orders.domain;

import com.exportflow.shared.domain.BusinessException;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.Set;

/** 稳定分页及筛选不变量；设计 function-design-reference.md，验证 TC-214/215。 */
public record OrderQuery(OrderFilter filter, int page, int pageSize, String sortDirection) {
  public void validate() {
    if (filter == null
        || page < 1
        || page > 1000000
        || !Set.of(20, 50, 100).contains(pageSize)
        || !Set.of("ASC", "DESC").contains(sortDirection)) throw BusinessException.validation();
    filter.validate();
  }

  public long offset() {
    return (long) (page - 1) * pageSize;
  }

  public record OrderFilter(
      String keyword,
      String orderNo,
      String status,
      String channel,
      Instant createdFrom,
      Instant createdTo,
      BigDecimal minAmount,
      BigDecimal maxAmount) {
    public void validate() {
      if (keyword != null && keyword.length() > 100
          || orderNo != null && orderNo.length() > 64
          || status != null
              && !Set.of("PENDING", "PAID", "SHIPPED", "COMPLETED", "CANCELED").contains(status)
          || channel != null && !Set.of("WEB", "APP", "STORE", "PARTNER").contains(channel)
          || createdFrom != null && createdTo != null && createdFrom.isAfter(createdTo)
          || minAmount != null && maxAmount != null && minAmount.compareTo(maxAmount) > 0)
        throw BusinessException.validation();
      for (BigDecimal value : new BigDecimal[] {minAmount, maxAmount}) {
        if (value != null
            && (value.signum() < 0 || value.scale() > 2 || value.precision() - value.scale() > 16))
          throw BusinessException.validation();
      }
    }

    public String literalKeyword() {
      if (keyword == null) return null;
      return "%" + keyword.replace("!", "!!").replace("%", "!%").replace("_", "!_") + "%";
    }
  }

  public record OrderCursor(Instant createdAt, long id) {}

  public record OrderBatch(java.util.List<Order> rows, OrderCursor lastCursor, boolean isEnd) {
    public OrderBatch {
      rows = java.util.List.copyOf(rows);
    }
  }
}
