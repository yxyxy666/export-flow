// 增量实施：[计划](../../../../../../../../specs/003-select-all-filtered/plan.md)、[任务](../../../../../../../../specs/003-select-all-filtered/tasks.md)；[测试](../../../../../../test/java/com/exportflow/exporttasks/ExportExclusionMySqlTest.java)。
// 规格互链：[计划](../../../../../../../../specs/002-order-management/plan.md)；[任务与完整映射](../../../../../../../../specs/002-order-management/tasks.md)；验证：[CreationInputBoundaryTest.java](../../../../../../test/java/com/exportflow/exporttasks/CreationInputBoundaryTest.java)、[ExportCreationContractTest.java](../../../../../../test/java/com/exportflow/exporttasks/ExportCreationContractTest.java)。
package com.exportflow.exporttasks.domain;

import com.exportflow.orders.domain.OrderQuery.OrderFilter;
import com.exportflow.shared.domain.BusinessException;
import java.util.List;

/** 互斥导出范围；设计 data-model.md，验证 TC-216。 */
public record ExportScope(
    String type, List<Long> orderIds, OrderFilter filter, List<Long> excludedOrderIds) {
  public ExportScope {
    if (orderIds != null) orderIds = List.copyOf(orderIds);
    excludedOrderIds = excludedOrderIds == null ? List.of() : List.copyOf(excludedOrderIds);
  }

  /** 保留旧调用及空排除语义。 */
  public ExportScope(String type, List<Long> orderIds, OrderFilter filter) {
    this(type, orderIds, filter, List.of());
  }

  public void validate() {
    if ("SELECTED".equals(type)) {
      if (filter != null
          || !excludedOrderIds.isEmpty()
          || orderIds == null
          || orderIds.stream().anyMatch(id -> id == null || id <= 0))
        throw BusinessException.validation();
      if (orderIds.isEmpty()) throw new BusinessException("EMPTY_EXPORT_SCOPE", "导出范围为空", 400);
    } else if ("FILTERED".equals(type)) {
      if (orderIds != null
          || filter == null
          || excludedOrderIds.size() > 200000
          || excludedOrderIds.stream().anyMatch(id -> id <= 0))
        throw BusinessException.validation();
      filter.validate();
    } else throw BusinessException.validation();
  }
}
