// 规格互链：[计划](../../../../../../../../specs/002-order-management/plan.md)；[任务与完整映射](../../../../../../../../specs/002-order-management/tasks.md)；验证：[OrderApiContractTest.java](../../../../../../test/java/com/exportflow/orders/OrderApiContractTest.java)、[RuntimeConfigurationTest.java](../../../../../../test/java/com/exportflow/RuntimeConfigurationTest.java)。
package com.exportflow.shared.domain;

import java.util.List;

/** 不可变领域分页值；HTTP层投影自己的分页协议。 */
public record PageSlice<T>(List<T> items, int page, int pageSize, long total) {
  public PageSlice {
    items = List.copyOf(items);
  }
}
