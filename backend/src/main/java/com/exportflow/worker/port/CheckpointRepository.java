// 规格互链：[计划](../../../../../../../../specs/002-order-management/plan.md)；[任务与完整映射](../../../../../../../../specs/002-order-management/tasks.md)；验证：[OrderApiContractTest.java](../../../../../../test/java/com/exportflow/orders/OrderApiContractTest.java)、[RuntimeConfigurationTest.java](../../../../../../test/java/com/exportflow/RuntimeConfigurationTest.java)。
package com.exportflow.worker.port;

import com.exportflow.exporttasks.domain.ExportFileModels.*;
import com.exportflow.worker.domain.ExecutionModels.ExecutionLease;
import java.util.*;

/** 断点与真实进度同事务，提交不明按blockId核对；设计 function-design-reference.md。 */
public interface CheckpointRepository {
  CheckpointSnapshot find(UUID taskId);

  CheckpointWriteResult commitBlock(
      ExecutionLease lease, CheckpointSnapshot expected, BlockRef ref);

  Optional<BlockRef> reconcileBlock(UUID taskId, UUID blockId);

  CheckpointWriteResult startGeneration(
      ExecutionLease lease, long expectedVersion, TrustedPrefix prefix);
}
