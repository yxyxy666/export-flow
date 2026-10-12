// 规格互链：[计划](../../../../../../../../specs/002-order-management/plan.md)；[任务与完整映射](../../../../../../../../specs/002-order-management/tasks.md)；验证：[CreationInputBoundaryTest.java](../../../../../../test/java/com/exportflow/exporttasks/CreationInputBoundaryTest.java)、[ExportCreationContractTest.java](../../../../../../test/java/com/exportflow/exporttasks/ExportCreationContractTest.java)、[OrderApiContractTest.java](../../../../../../test/java/com/exportflow/orders/OrderApiContractTest.java)。
package com.exportflow.orders.api;

import java.util.List;
import java.util.Map;

/** 传输模型独立于领域和SQL记录；设计 data-model.md，验证 TC-214/215。 */
public final class OrderApiModels {
  private OrderApiModels() {}

  public record OrderQueryDto(Map<String, String> values) {
    public OrderQueryDto {
      values = Map.copyOf(values);
    }
  }

  public record OrderResponse(
      String id,
      String orderNo,
      String customerName,
      String customerPhone,
      String status,
      String channel,
      String amount,
      String currency,
      String region,
      String createdAt) {}

  public record PageResult<T>(List<T> items, int page, int pageSize, long total) {
    public PageResult {
      items = List.copyOf(items);
    }
  }
}
