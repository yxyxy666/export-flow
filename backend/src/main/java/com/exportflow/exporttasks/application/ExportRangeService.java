// 增量实施：[计划](../../../../../../../../specs/003-select-all-filtered/plan.md)、[任务](../../../../../../../../specs/003-select-all-filtered/tasks.md)；[测试](../../../../../../test/java/com/exportflow/exporttasks/ExportExclusionMySqlTest.java)。
// 规格互链：[计划](../../../../../../../../specs/002-order-management/plan.md)；[任务与完整映射](../../../../../../../../specs/002-order-management/tasks.md)；验证：[CreationInputBoundaryTest.java](../../../../../../test/java/com/exportflow/exporttasks/CreationInputBoundaryTest.java)、[ExportCreationContractTest.java](../../../../../../test/java/com/exportflow/exporttasks/ExportCreationContractTest.java)。
package com.exportflow.exporttasks.application;

import static com.exportflow.exporttasks.domain.ExportTaskModels.*;

import com.exportflow.exporttasks.domain.ExportScope;
import com.exportflow.orders.port.OrderRepository;
import com.exportflow.shared.domain.BusinessException;
import java.util.Map;
import org.springframework.stereotype.Service;

/** 数据库裁决真实范围，不接受客户端计数；设计 function-design-reference.md，验证 TC-216。 */
@Service
public class ExportRangeService {
  private final OrderRepository orders;

  public ExportRangeService(OrderRepository orders) {
    this.orders = orders;
  }

  public ExportRangeCheck verify(ExportScope scope) {
    scope.validate();
    long total;
    if (scope.orderIds() != null) {
      var ids = scope.orderIds().stream().distinct().sorted().toList();
      total = ids.size();
      if (total <= 200000 && orders.countExisting(ids) != total)
        throw new BusinessException(
            "ORDER_SELECTION_INVALID",
            "已选订单无效，请重新选择",
            422,
            "REJECTED",
            Map.of("currentCount", total, "limit", 200000L));
    } else {
      var ids = scope.excludedOrderIds().stream().distinct().sorted().toList();
      if (orders.countExisting(ids) != ids.size())
        throw new BusinessException("ORDER_SELECTION_INVALID", "已选订单无效，请重新选择", 422);
      total = orders.count(scope.filter()) - orders.countMatching(scope.filter(), ids);
    }
    if (total == 0) throw new BusinessException("EMPTY_EXPORT_SCOPE", "导出范围为空", 400);
    if (total > 200000)
      throw new BusinessException(
          "EXPORT_LIMIT_EXCEEDED",
          "导出数量超过上限，请缩小范围",
          422,
          "REJECTED",
          Map.of("currentCount", total, "limit", 200000L));
    return new ExportRangeCheck(total, scope);
  }
}
