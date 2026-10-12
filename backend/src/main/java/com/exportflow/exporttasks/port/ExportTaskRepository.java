// 规格互链：[计划](../../../../../../../../specs/002-order-management/plan.md)；[任务与完整映射](../../../../../../../../specs/002-order-management/tasks.md)；验证：[CreateTransactionMySqlTest.java](../../../../../../test/java/com/exportflow/exporttasks/CreateTransactionMySqlTest.java)、[CreationAsyncBoundaryTest.java](../../../../../../test/java/com/exportflow/exporttasks/CreationAsyncBoundaryTest.java)、[CreationInputBoundaryTest.java](../../../../../../test/java/com/exportflow/exporttasks/CreationInputBoundaryTest.java)。
// 职责：任务持久化与权威状态读取端口。
package com.exportflow.exporttasks.port;

import static com.exportflow.exporttasks.domain.ExportTaskModels.*;

import com.exportflow.exporttasks.domain.*;
import java.time.Instant;
import java.util.*;

/** 任务权威边界；设计 function-design-reference.md，验证 TC-218/224。 */
public interface ExportTaskRepository {
  void insert(ExportTask task, ExportScope scope, List<ExportField> fields);

  Optional<TaskSummary> findSummary(UUID taskId);

  Optional<TaskSnapshot> findSnapshot(UUID taskId);

  ExportTask lockTask(UUID taskId);

  boolean failDispatchTimeout(UUID taskId, Instant endedAt);
}
