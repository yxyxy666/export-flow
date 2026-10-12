// 增量实施：[计划](../../../../../../../../../specs/003-select-all-filtered/plan.md)、[任务](../../../../../../../../../specs/003-select-all-filtered/tasks.md)；[测试](../../../../../../../test/java/com/exportflow/exporttasks/ExportExclusionMySqlTest.java)。
// 规格互链：[计划](../../../../../../../../../specs/002-order-management/plan.md)；[任务与完整映射](../../../../../../../../../specs/002-order-management/tasks.md)；验证：[CreateTransactionMySqlTest.java](../../../../../../../test/java/com/exportflow/exporttasks/CreateTransactionMySqlTest.java)、[CreationAsyncBoundaryTest.java](../../../../../../../test/java/com/exportflow/exporttasks/CreationAsyncBoundaryTest.java)、[CreationInputBoundaryTest.java](../../../../../../../test/java/com/exportflow/exporttasks/CreationInputBoundaryTest.java)。
// 职责：任务持久化端口适配。
package com.exportflow.exporttasks.infrastructure.persistence;

import static com.exportflow.exporttasks.domain.ExportTaskModels.*;

import com.exportflow.exporttasks.domain.*;
import com.exportflow.exporttasks.port.ExportTaskRepository;
import java.time.Instant;
import java.util.*;
import org.springframework.stereotype.Repository;

/** 所有写入加入调用方事务；设计 function-design-reference.md，验证 TC-218。 */
@Repository
public class MyBatisExportTaskRepository implements ExportTaskRepository {
  private final ExportTaskMapper mapper;
  private final ExportTaskPersistenceMapper converter;

  public MyBatisExportTaskRepository(
      ExportTaskMapper mapper, ExportTaskPersistenceMapper converter) {
    this.mapper = mapper;
    this.converter = converter;
  }

  public void insert(ExportTask task, ExportScope scope, List<ExportField> fields) {
    mapper.insert(converter.toRecord(task, scope, fields));
    var ids = scope.orderIds() != null ? scope.orderIds() : scope.excludedOrderIds();
    for (int start = 0; start < ids.size(); start += 1000)
      mapper.insertScopeOrderIds(
          task.taskId().toString(), ids.subList(start, Math.min(start + 1000, ids.size())));
  }

  public Optional<TaskSummary> findSummary(UUID taskId) {
    return mapper.findSummary(taskId.toString()).map(converter::toSummary);
  }

  public Optional<TaskSnapshot> findSnapshot(UUID taskId) {
    return mapper
        .findSnapshot(taskId.toString())
        .map(row -> converter.toSnapshot(row, mapper.findScopeOrderIds(taskId.toString())));
  }

  public ExportTask lockTask(UUID taskId) {
    var row = mapper.lockTask(taskId.toString());
    return row == null ? null : converter.toTask(row);
  }

  public boolean failDispatchTimeout(UUID taskId, Instant endedAt) {
    return mapper.failDispatchTimeout(taskId.toString(), endedAt) == 1;
  }
}
