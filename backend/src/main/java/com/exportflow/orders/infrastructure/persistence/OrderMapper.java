// 增量实施：[计划](../../../../../../../../../specs/003-select-all-filtered/plan.md)、[任务](../../../../../../../../../specs/003-select-all-filtered/tasks.md)；[测试](../../../../../../../test/java/com/exportflow/exporttasks/ExportExclusionMySqlTest.java)。
// 规格互链：[计划](../../../../../../../../../specs/002-order-management/plan.md)；[任务与完整映射](../../../../../../../../../specs/002-order-management/tasks.md)；验证：[OrderQueryBoundaryTest.java](../../../../../../../test/java/com/exportflow/orders/OrderQueryBoundaryTest.java)、[OrderQueryMySqlTest.java](../../../../../../../test/java/com/exportflow/orders/OrderQueryMySqlTest.java)。
package com.exportflow.orders.infrastructure.persistence;

import com.exportflow.exporttasks.domain.ExportScope;
import com.exportflow.orders.domain.OrderQuery;
import com.exportflow.orders.domain.OrderQuery.OrderCursor;
import com.exportflow.orders.domain.OrderQuery.OrderFilter;
import java.util.List;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

/** 参数化SQL入口；设计 function-design-reference.md，验证 TC-215。 */
@Mapper
public interface OrderMapper {
  long count(@Param("filter") OrderFilter filter);

  List<OrderRecord> findPage(@Param("query") OrderQuery query);

  long countExisting(@Param("ids") List<Long> orderIds);

  long countMatching(@Param("filter") OrderFilter filter, @Param("ids") List<Long> orderIds);

  List<OrderRecord> readBatch(
      @Param("taskId") String taskId,
      @Param("scope") ExportScope scope,
      @Param("after") OrderCursor after,
      @Param("limit") int limit);
}
