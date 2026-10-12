// 规格互链：[计划](../../../../../../../../specs/002-order-management/plan.md)；[任务与完整映射](../../../../../../../../specs/002-order-management/tasks.md)；验证：[CreationInputBoundaryTest.java](../../../../../../test/java/com/exportflow/exporttasks/CreationInputBoundaryTest.java)、[IdempotencyMySqlTest.java](../../../../../../test/java/com/exportflow/exporttasks/IdempotencyMySqlTest.java)。
package com.exportflow.exporttasks.port;

import static com.exportflow.exporttasks.domain.ExportTaskModels.*;

import java.util.Optional;
import java.util.UUID;

/** 幂等持久边界；设计 function-design-reference.md，验证 TC-217/218。 */
public interface IdempotencyRepository {
  Optional<IdempotencyRecord> findByKeyHash(byte[] keyHash);

  void insert(CanonicalSubmission submission, UUID taskId);
}
